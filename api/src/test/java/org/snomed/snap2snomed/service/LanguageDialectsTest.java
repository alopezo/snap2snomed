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

package org.snomed.snap2snomed.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// Same cases as language-dialects.spec.ts in the UI: keep both in sync
class LanguageDialectsTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", " , ,"})
  void normalizeReturnsNullForNoLanguage(String input) {
    assertThat(LanguageDialects.normalize(input)).isNull();
  }

  @Test
  void normalizeTrimsEntriesAndDropsEmptyOnes() {
    assertThat(LanguageDialects.normalize("es-X-5641000179103, es-X-450828004,,en-X-900000000000509007 "))
        .isEqualTo("es-X-5641000179103,es-X-450828004,en-X-900000000000509007");
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "en-X-900000000000509007|en-x-sctlang-90000000-00005090-07",
      "es-X-450828004|es-x-sctlang-45082800-4",
      "sv-X-46011000052107|sv-x-sctlang-46011000-052107",
      "en-x-32570271000036106|en-x-sctlang-32570271-00003610-6",
      "es-X-450828004, en-X-900000000000509007|es-x-sctlang-45082800-4,en-x-sctlang-90000000-00005090-07",
      "es-AR,en-X-900000000000509007|es-AR,en-x-sctlang-90000000-00005090-07"
  })
  void convertsEveryEntryToOntoserverFormat(String input, String expected) {
    assertThat(LanguageDialects.toServerFormat(input, TerminologyServerKind.ONTOSERVER)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SNOWSTORM", "SNOWSTORM_LITE", "OTHER"})
  void keepsSnowstormFormatForOtherServers(TerminologyServerKind kind) {
    assertThat(LanguageDialects.toServerFormat("es-X-450828004, en-X-900000000000509007", kind))
        .isEqualTo("es-X-450828004,en-X-900000000000509007");
  }

  @Test
  void toServerFormatKeepsNullAsNull() {
    assertThat(LanguageDialects.toServerFormat(null, TerminologyServerKind.ONTOSERVER)).isNull();
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', nullValues = "NULL", value = {
      "es-X-450828004,en-X-900000000000509007|es",
      "NL-X-31000146106|nl",
      "sv|sv",
      "NULL|NULL"
  })
  void primaryLanguageIsTheFirstEntryLanguage(String input, String expected) {
    assertThat(LanguageDialects.primaryLanguage(input)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', nullValues = "NULL", value = {
      "Ontoserver®|ONTOSERVER",
      "Snowstorm Lite FHIR Terminology Server|SNOWSTORM_LITE",
      "SnowstormX FHIR Server|SNOWSTORM",
      "HAPI FHIR Server|OTHER",
      "NULL|OTHER"
  })
  void detectsServerKindFromSoftwareName(String softwareName, TerminologyServerKind expected) {
    assertThat(TerminologyServerKind.fromSoftwareName(softwareName)).isEqualTo(expected);
  }
}
