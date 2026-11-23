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

/**
 * Helpers for the map display language: an ordered, comma separated sequence of language
 * dialects stored in Snowstorm format, e.g. "es-X-450828004,en-X-900000000000509007".
 *
 * Snowstorm accepts that format in Accept-Language. Ontoserver ignores it and expects
 * "es-x-sctlang-45082800-4" instead (the refset id split in chunks of 8 digits, the maximum length
 * of a BCP-47 subtag). Keep in sync with LanguageDialects.java in the API.
 */

/** Terminology server implementation, detected from CapabilityStatement.software.name. */
export type TerminologyServerKind = 'snowstorm' | 'snowstorm-lite' | 'ontoserver' | 'other';

const SNOWSTORM_DIALECT = /^([a-z]{2,3})-x-(\d+)$/i;

export class LanguageDialects {

  /** Trims every entry and drops empty ones; null when there is no explicit display language. */
  static normalize(dialects: string | null | undefined): string | null {
    if (!dialects) {
      return null;
    }
    const normalized = dialects.split(',').map(entry => entry.trim()).filter(entry => entry.length > 0).join(',');
    return normalized.length > 0 ? normalized : null;
  }

  /** Converts a dialect sequence to the format the given server understands. */
  static toServerFormat(dialects: string | null | undefined, serverKind: TerminologyServerKind): string | null {
    const normalized = LanguageDialects.normalize(dialects);
    if (!normalized || serverKind !== 'ontoserver') {
      return normalized;
    }
    return normalized.split(',').map(LanguageDialects.toOntoserverDialect).join(',');
  }

  /** Language code of the first entry, e.g. "es" for "es-X-450828004,en-X-...", or null. */
  static primaryLanguage(dialects: string | null | undefined): string | null {
    const normalized = LanguageDialects.normalize(dialects);
    return normalized ? normalized.split(',')[0].split('-')[0].toLowerCase() : null;
  }

  static serverKindFromSoftwareName(softwareName: string | null | undefined): TerminologyServerKind {
    const name = (softwareName ?? '').toLowerCase();
    if (name.includes('ontoserver')) {
      return 'ontoserver';
    }
    if (name.includes('snowstorm lite')) {
      return 'snowstorm-lite';
    }
    if (name.includes('snowstorm')) {
      return 'snowstorm';
    }
    return 'other';
  }

  /** Edition module URI of a SNOMED CT version URI, e.g. "http://snomed.info/sct/11000221109". */
  static editionUri(versionUri: string | null | undefined): string | null {
    const match = versionUri?.match(/^(http:\/\/snomed\.info\/x?sct\/\d+)/);
    return match ? match[1] : null;
  }

  private static toOntoserverDialect(entry: string): string {
    const match = entry.match(SNOWSTORM_DIALECT);
    if (!match) {
      return entry;
    }
    const chunks = match[2].match(/.{1,8}/g) ?? [];
    return `${match[1].toLowerCase()}-x-sctlang-${chunks.join('-')}`;
  }
}
