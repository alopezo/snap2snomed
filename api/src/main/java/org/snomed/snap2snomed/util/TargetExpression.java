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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A map target refined with laterality and/or severity, kept as a close-to-user SNOMED CT expression
 * without terms, for example {@code 71620000:272741003=7771000,246112005=24484000}.
 *
 * <p>Only the refinements offered in the user interface are accepted, and the canonical form always
 * lists laterality before severity, so the same refinement always gives the same code.
 * Same rules as target-expression.ts in the UI: keep both in sync.
 */
public final class TargetExpression {

  public static final String LATERALITY = "272741003";
  public static final String SEVERITY = "246112005";

  public static final String LEFT = "7771000";
  public static final String RIGHT = "24028007";
  public static final String RIGHT_AND_LEFT = "51440002";
  public static final String MILD = "255604002";
  public static final String MODERATE = "6736007";
  public static final String SEVERE = "24484000";

  /** Allowed values of each refinement attribute, in canonical attribute order. */
  public static final Map<String, Set<String>> ATTRIBUTE_VALUES;

  static {
    final Map<String, Set<String>> values = new LinkedHashMap<>();
    values.put(LATERALITY, Set.of(LEFT, RIGHT, RIGHT_AND_LEFT));
    values.put(SEVERITY, Set.of(MILD, MODERATE, SEVERE));
    ATTRIBUTE_VALUES = Collections.unmodifiableMap(values);
  }

  /** Between the focus term and each value term in the display of a refined target. */
  public static final String DISPLAY_SEPARATOR = " - ";

  private static final Pattern TERM = Pattern.compile("\\|[^|]*\\|");
  private static final Pattern EXPRESSION = Pattern.compile("^(\\d{6,18}):(\\d{6,18}=\\d{6,18}(?:,\\d{6,18}=\\d{6,18})*)$");

  private final String focus;
  /** Attribute to value, in canonical order. */
  private final Map<String, String> refinements;

  private TargetExpression(String focus, Map<String, String> refinements) {
    this.focus = focus;
    this.refinements = refinements;
  }

  /** Builds an expression; null values are left out. Fails on a value that is not offered. */
  public static TargetExpression of(String focus, String laterality, String severity) {
    final Map<String, String> refinements = new LinkedHashMap<>();
    if (laterality != null) {
      refinements.put(LATERALITY, laterality);
    }
    if (severity != null) {
      refinements.put(SEVERITY, severity);
    }
    return create(focus, refinements)
        .orElseThrow(() -> new IllegalArgumentException("Unsupported target refinement"));
  }

  /**
   * Reads a target code written as an expression. Terms between pipes and white space are ignored.
   * Returns empty for a plain concept id and for anything that is not a supported refinement.
   */
  public static Optional<TargetExpression> parse(String code) {
    if (code == null) {
      return Optional.empty();
    }
    final String bare = TERM.matcher(code).replaceAll("").replaceAll("\\s+", "");
    final Matcher matcher = EXPRESSION.matcher(bare);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    final Map<String, String> refinements = new LinkedHashMap<>();
    for (final String pair : matcher.group(2).split(",")) {
      final String[] parts = pair.split("=");
      if (refinements.put(parts[0], parts[1]) != null) {
        return Optional.empty(); // the same attribute twice
      }
    }
    return create(matcher.group(1), refinements);
  }

  /** True when the code has the shape of an expression (a focus concept followed by a refinement). */
  public static boolean looksLikeExpression(String code) {
    return code != null && code.contains(":");
  }

  /** The focus concept of an expression, or the code itself when it is not an expression. */
  public static String focusOf(String code) {
    return parse(code).map(TargetExpression::getFocus).orElse(code);
  }

  /** Writes an expression in canonical form, or returns the code unchanged when it is not one. */
  public static String canonical(String code) {
    return parse(code).map(TargetExpression::toCode).orElse(code);
  }

  private static Optional<TargetExpression> create(String focus, Map<String, String> refinements) {
    if (focus == null || refinements.isEmpty()) {
      return Optional.empty();
    }
    final Map<String, String> ordered = new LinkedHashMap<>();
    for (final Map.Entry<String, Set<String>> allowed : ATTRIBUTE_VALUES.entrySet()) {
      final String value = refinements.get(allowed.getKey());
      if (value != null) {
        if (!allowed.getValue().contains(value)) {
          return Optional.empty();
        }
        ordered.put(allowed.getKey(), value);
      }
    }
    if (ordered.size() != refinements.size()) {
      return Optional.empty(); // an attribute that is not offered
    }
    return Optional.of(new TargetExpression(focus, Collections.unmodifiableMap(ordered)));
  }

  public String getFocus() {
    return focus;
  }

  public Optional<String> getLaterality() {
    return Optional.ofNullable(refinements.get(LATERALITY));
  }

  public Optional<String> getSeverity() {
    return Optional.ofNullable(refinements.get(SEVERITY));
  }

  /** The refinement values, in canonical order (laterality, then severity). */
  public List<String> getValues() {
    return new ArrayList<>(refinements.values());
  }

  /** The canonical code: no terms, no spaces, laterality before severity. */
  public String toCode() {
    final StringBuilder code = new StringBuilder(focus).append(':');
    refinements.forEach((attribute, value) -> {
      if (code.charAt(code.length() - 1) != ':') {
        code.append(',');
      }
      code.append(attribute).append('=').append(value);
    });
    return code.toString();
  }

  /** The display of a refined target: the focus term, then each value term, joined by " - ". */
  public static String composeDisplay(String focusDisplay, List<String> valueDisplays) {
    final StringBuilder display = new StringBuilder(focusDisplay);
    valueDisplays.forEach(valueDisplay -> display.append(DISPLAY_SEPARATOR).append(valueDisplay));
    return display.toString();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof TargetExpression && toCode().equals(((TargetExpression) o).toCode());
  }

  @Override
  public int hashCode() {
    return toCode().hashCode();
  }

  @Override
  public String toString() {
    return toCode();
  }
}
