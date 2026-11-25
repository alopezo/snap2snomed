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
import static org.snomed.snap2snomed.model.enumeration.MappingRelationship.TARGET_BROADER;
import static org.snomed.snap2snomed.model.enumeration.MappingRelationship.TARGET_EQUIVALENT;
import static org.snomed.snap2snomed.model.enumeration.MappingRelationship.TARGET_INEXACT;
import static org.snomed.snap2snomed.model.enumeration.MappingRelationship.TARGET_NARROWER;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.snomed.snap2snomed.model.MapView;
import org.snomed.snap2snomed.model.enumeration.MappingRelationship;

class TargetExportFormTest {

  private static final String LEFT = "71620000:272741003=7771000";
  private static final String RIGHT = "71620000:272741003=24028007";

  private static MapView row(long sourceIndex, String code, String display, MappingRelationship relationship) {
    return MapView.builder().sourceIndex(sourceIndex).sourceCode("S" + sourceIndex)
        .targetCode(code).targetDisplay(display).relationship(relationship).noMap(false).build();
  }

  @ParameterizedTest
  @CsvSource({"focus,FOCUS", "BOTH,BOTH", "expression,EXPRESSION", "unknown,EXPRESSION", ",EXPRESSION"})
  void readsTheRequestParameter(String value, TargetExportForm expected) {
    assertThat(TargetExportForm.of(value)).isEqualTo(expected);
  }

  @Test
  void expressionAndBothKeepTheRows() {
    final List<MapView> rows = List.of(row(1, LEFT, "Fracture of femur - Left", TARGET_EQUIVALENT));
    assertThat(TargetExportForm.EXPRESSION.apply(rows)).isSameAs(rows);
    assertThat(TargetExportForm.BOTH.apply(rows)).isSameAs(rows);
  }

  @ParameterizedTest
  @CsvSource({"TARGET_EQUIVALENT,TARGET_BROADER", "TARGET_NARROWER,TARGET_INEXACT",
      "TARGET_BROADER,TARGET_BROADER", "TARGET_INEXACT,TARGET_INEXACT"})
  void focusAdjustsTheRelationship(MappingRelationship relationship, MappingRelationship expected) {
    final List<MapView> result = TargetExportForm.FOCUS.apply(new ArrayList<>(List.of(
        row(1, LEFT, "Fracture of femur - Left", relationship))));
    assertThat(result).singleElement().satisfies(r -> {
      assertThat(r.getTargetCode()).isEqualTo("71620000");
      assertThat(r.getTargetDisplay()).isEqualTo("Fracture of femur");
      assertThat(r.getRelationship()).isEqualTo(expected);
    });
  }

  @Test
  void focusWritesRefinedTargetsOfTheSameRowOnce() {
    final List<MapView> result = TargetExportForm.FOCUS.apply(new ArrayList<>(List.of(
        row(1, LEFT, "Fracture of femur - Left", TARGET_EQUIVALENT),
        row(1, RIGHT, "Fracture of femur - Right", TARGET_EQUIVALENT),
        row(2, LEFT, "Fracture of femur - Left", TARGET_EQUIVALENT))));
    assertThat(result).extracting(MapView::getSourceIndex, MapView::getTargetCode, MapView::getRelationship)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(1L, "71620000", TARGET_BROADER),
            org.assertj.core.groups.Tuple.tuple(2L, "71620000", TARGET_BROADER));
  }

  @Test
  void focusMergesDifferentRelationshipsAsInexact() {
    final List<MapView> result = TargetExportForm.FOCUS.apply(new ArrayList<>(List.of(
        row(1, LEFT, "Fracture of femur - Left", TARGET_EQUIVALENT),
        row(1, RIGHT, "Fracture of femur - Right", TARGET_INEXACT))));
    assertThat(result).singleElement().extracting(MapView::getRelationship).isEqualTo(TARGET_INEXACT);
  }

  @Test
  void focusKeepsATargetThatAlreadyIsTheFocusConcept() {
    final List<MapView> result = TargetExportForm.FOCUS.apply(new ArrayList<>(List.of(
        row(1, LEFT, "Fracture of femur - Left", TARGET_EQUIVALENT),
        row(1, "71620000", "Fracture of femur", TARGET_NARROWER),
        row(1, "125605004", "Fracture of bone", TARGET_BROADER))));
    assertThat(result).extracting(MapView::getTargetCode, MapView::getRelationship)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("71620000", TARGET_NARROWER),
            org.assertj.core.groups.Tuple.tuple("125605004", TARGET_BROADER));
  }

  @Test
  void focusKeepsNoMapRows() {
    final MapView noMap = MapView.builder().sourceIndex(1L).noMap(true).build();
    final MapView noMap2 = MapView.builder().sourceIndex(2L).noMap(true).build();
    assertThat(TargetExportForm.FOCUS.apply(new ArrayList<>(List.of(noMap, noMap2)))).containsExactly(noMap, noMap2);
  }

  @Test
  void focusDisplayRemovesOneTermPerValue() {
    assertThat(TargetExportForm.focusDisplay("71620000:272741003=7771000,246112005=24484000",
        "Fracture of femur - with dislocation - Left - Severe")).isEqualTo("Fracture of femur - with dislocation");
    assertThat(TargetExportForm.focusDisplay("71620000", "Fracture - of femur")).isEqualTo("Fracture - of femur");
  }
}
