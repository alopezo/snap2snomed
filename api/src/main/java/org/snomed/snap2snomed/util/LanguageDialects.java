/*
 * Copyright © 2026 SNOMED International
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.snomed.snap2snomed.util;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.snomed.snap2snomed.service.TerminologyServerKind;

/**
 * Helpers for the map display language: an ordered, comma separated sequence of language
 * dialects stored in Snowstorm format, e.g. {@code es-X-450828004,en-X-900000000000509007}.
 *
 * <p>Snowstorm accepts that format in {@code Accept-Language}. Ontoserver ignores it and expects
 * {@code es-x-sctlang-45082800-4} instead (the refset id split in chunks of 8 digits, the
 * maximum length of a BCP-47 subtag). Keep in sync with {@code language-dialects.ts} in the UI.
 */
public final class LanguageDialects {

  /**
   * Accepted display language values: comma separated language tags such as "es", "es-AR" or
   * "es-X-450828004". Only letters, digits, hyphens, commas and spaces, so the value is always a
   * CORS-safelisted Accept-Language header (browsers call the terminology server directly).
   */
  public static final String VALID_PATTERN =
      "^\\s*[A-Za-z]{2,3}(-[A-Za-z0-9]{1,18})*(\\s*,\\s*[A-Za-z]{2,3}(-[A-Za-z0-9]{1,18})*)*\\s*$";

  private static final Pattern VALID = Pattern.compile(VALID_PATTERN);

  private static final Pattern SNOWSTORM_DIALECT = Pattern.compile("^([a-zA-Z]{2,3})-[xX]-(\\d+)$");

  private LanguageDialects() {
  }

  /** True for a null/blank value (no display language) or a well formed dialect sequence. */
  public static boolean isValid(String dialects) {
    return dialects == null || dialects.isBlank() || VALID.matcher(dialects).matches();
  }

  /**
   * Trims every entry and drops empty ones. Returns null for a null or blank value, which means
   * "no explicit display language" (English FSN).
   */
  public static String normalize(String dialects) {
    if (dialects == null || dialects.isBlank()) {
      return null;
    }
    final String normalized = Arrays.stream(dialects.split(","))
        .map(String::trim)
        .filter(entry -> !entry.isEmpty())
        .collect(Collectors.joining(","));
    return normalized.isEmpty() ? null : normalized;
  }

  /**
   * Converts a normalized dialect sequence to the format the given server understands. Entries
   * that are not in Snowstorm format (e.g. a plain "es-AR") are passed through unchanged.
   */
  public static String toServerFormat(String dialects, TerminologyServerKind serverKind) {
    final String normalized = normalize(dialects);
    if (normalized == null || serverKind != TerminologyServerKind.ONTOSERVER) {
      return normalized;
    }
    return Arrays.stream(normalized.split(","))
        .map(LanguageDialects::toOntoserverDialect)
        .collect(Collectors.joining(","));
  }

  /** Language code of the first entry, e.g. "es" for "es-X-450828004,en-X-...", or null. */
  public static String primaryLanguage(String dialects) {
    final String normalized = normalize(dialects);
    if (normalized == null) {
      return null;
    }
    return normalized.split(",")[0].split("-")[0].toLowerCase();
  }

  static String toOntoserverDialect(String entry) {
    final Matcher matcher = SNOWSTORM_DIALECT.matcher(entry);
    if (!matcher.matches()) {
      return entry;
    }
    final String refsetId = matcher.group(2);
    final StringBuilder chunks = new StringBuilder();
    for (int i = 0; i < refsetId.length(); i += 8) {
      if (chunks.length() > 0) {
        chunks.append('-');
      }
      chunks.append(refsetId, i, Math.min(i + 8, refsetId.length()));
    }
    return matcher.group(1).toLowerCase() + "-x-sctlang-" + chunks;
  }
}
