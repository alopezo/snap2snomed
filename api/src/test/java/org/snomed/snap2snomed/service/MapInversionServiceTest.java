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
import static org.snomed.snap2snomed.model.enumeration.MappingRelationship.TARGET_BROADER;
import static org.snomed.snap2snomed.model.enumeration.MappingRelationship.TARGET_EQUIVALENT;
import static org.snomed.snap2snomed.model.enumeration.MappingRelationship.TARGET_INEXACT;
import static org.snomed.snap2snomed.model.enumeration.MappingRelationship.TARGET_NARROWER;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.snomed.snap2snomed.model.ImportedCode;
import org.snomed.snap2snomed.model.MapRow;
import org.snomed.snap2snomed.model.MapRowTarget;
import org.snomed.snap2snomed.model.enumeration.MappingRelationship;
import org.snomed.snap2snomed.service.MapInversionService.Inverted;

class MapInversionServiceTest {

  private static MapRowTarget target(String source, String targetCode, MappingRelationship relationship, boolean noMap) {
    final MapRow row = MapRow.builder().sourceCode(ImportedCode.builder().code(source).display(source + " term").build())
        .noMap(noMap).build();
    return MapRowTarget.builder().row(row).targetCode(targetCode).relationship(relationship).build();
  }

  @Test
  void invertsTheRelationship() {
    assertThat(MapInversionService.invert(TARGET_BROADER)).isEqualTo(TARGET_NARROWER);
    assertThat(MapInversionService.invert(TARGET_NARROWER)).isEqualTo(TARGET_BROADER);
    assertThat(MapInversionService.invert(TARGET_EQUIVALENT)).isEqualTo(TARGET_EQUIVALENT);
    assertThat(MapInversionService.invert(TARGET_INEXACT)).isEqualTo(TARGET_INEXACT);
  }

  @Test
  void mapsEachConceptBackToTheCodesThatTargetIt() {
    final List<Inverted> inverted = MapInversionService.invertTargets(List.of(
        target("A1", "195967001", TARGET_EQUIVALENT, false),
        target("A2", "195967001", TARGET_BROADER, false),
        target("B1", "13645005", TARGET_NARROWER, false)));

    assertThat(inverted).containsExactly(
        new Inverted("195967001", "A1", "A1 term", TARGET_EQUIVALENT, false),
        new Inverted("195967001", "A2", "A2 term", TARGET_NARROWER, false),
        new Inverted("13645005", "B1", "B1 term", TARGET_BROADER, false));
  }

  @Test
  void mapsARefinedTargetFromItsFocusConcept() {
    // the focus is broader than the refined target: equivalent becomes broader, then inverted to narrower
    final List<Inverted> inverted = MapInversionService.invertTargets(List.of(
        target("C1", "195967001:272741003=7771000", TARGET_EQUIVALENT, false),
        target("C2", "195967001:272741003=7771000", TARGET_NARROWER, false)));

    assertThat(inverted).containsExactly(
        new Inverted("195967001", "C1", "C1 term", TARGET_NARROWER, true),
        new Inverted("195967001", "C2", "C2 term", TARGET_INEXACT, true));
  }

  @Test
  void keepsAPlainTargetOverARefinedOneAndLeavesNoMapRowsOut() {
    final List<Inverted> inverted = MapInversionService.invertTargets(List.of(
        target("D1", "195967001:272741003=7771000", TARGET_EQUIVALENT, false),
        target("D1", "195967001", TARGET_EQUIVALENT, false),
        target("E1", "13645005", TARGET_EQUIVALENT, true),
        target("F1", "", TARGET_EQUIVALENT, false)));

    assertThat(inverted).containsExactly(new Inverted("195967001", "D1", "D1 term", TARGET_EQUIVALENT, false));
  }
}
