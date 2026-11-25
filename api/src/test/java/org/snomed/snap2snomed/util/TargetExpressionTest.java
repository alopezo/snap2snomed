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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// Same cases as target-expression.spec.ts in the UI: keep both in sync
class TargetExpressionTest {

  @Test
  void buildsCanonicalCodeWithLateralityBeforeSeverity() {
    assertThat(TargetExpression.of("71620000", "7771000", "24484000").toCode())
        .isEqualTo("71620000:272741003=7771000,246112005=24484000");
    assertThat(TargetExpression.of("71620000", null, "24484000").toCode()).isEqualTo("71620000:246112005=24484000");
    assertThat(TargetExpression.of("71620000", "24028007", null).toCode()).isEqualTo("71620000:272741003=24028007");
  }

  @Test
  void refusesNoRefinementOrUnsupportedValues() {
    assertThatThrownBy(() -> TargetExpression.of("71620000", null, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TargetExpression.of("71620000", "24484000", null)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
      "71620000:272741003=7771000;71620000:272741003=7771000",
      "71620000:246112005=24484000,272741003=7771000;71620000:272741003=7771000,246112005=24484000",
      "71620000 |Fracture of femur| : 272741003 |Laterality| = 7771000 |Left|;71620000:272741003=7771000",
      "71620000:{272741003=51440002};71620000:{272741003=51440002}"
  })
  void canonicalFormIgnoresTermsSpacesAndOrder(String code, String expected) {
    assertThat(TargetExpression.canonical(code)).isEqualTo(expected);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"71620000", "71620000:363698007=71341001", "71620000:272741003=24484000",
      "71620000:272741003=7771000,272741003=24028007", "71620000:", "abc:272741003=7771000"})
  void doesNotParseConceptsOrUnsupportedRefinements(String code) {
    assertThat(TargetExpression.parse(code)).isEmpty();
  }

  @Test
  void focusOfReturnsFocusOrTheCodeItself() {
    assertThat(TargetExpression.focusOf("71620000:272741003=7771000")).isEqualTo("71620000");
    assertThat(TargetExpression.focusOf("71620000")).isEqualTo("71620000");
  }

  @Test
  void composesDisplayWithSeparator() {
    assertThat(TargetExpression.composeDisplay("Fracture of femur", List.of("Left", "Severe")))
        .isEqualTo("Fracture of femur - Left - Severe");
  }
}
