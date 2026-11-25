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

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.snomed.snap2snomed.model.enumeration.MapStatus;
import org.snomed.snap2snomed.model.enumeration.MappingRelationship;
import org.snomed.snap2snomed.util.InheritanceCalculator;

class InheritanceServiceTest {

  @Test
  void cachedDescendantsKeepTheirTerms() {
    final Map<String, String> concepts = new LinkedHashMap<>();
    concepts.put("389145006", "Allergic asthma (disorder)");
    concepts.put("426656000", "Severe persistent\tasthma");
    final String cached = InheritanceService.format(concepts);
    assertThat(InheritanceService.parse(cached)).containsExactly(
        Map.entry("389145006", "Allergic asthma (disorder)"), Map.entry("426656000", "Severe persistent asthma"));
    assertThat(InheritanceService.parse("")).isEmpty();
  }

  private static InheritanceCalculator.Result inherited(String from, String... targets) {
    return new InheritanceCalculator.Result("C", InheritanceCalculator.State.INHERITED, from,
        java.util.Arrays.stream(targets).map(t -> new InheritanceCalculator.Target(t, t, MappingRelationship.TARGET_BROADER)).toList(),
        java.util.List.of());
  }

  @Test
  void fingerprintRecordsWhatARowInherits() {
    assertThat(InheritanceService.fingerprint(inherited("195967001", "J45.9", "J45.0"))).isEqualTo("INHERITED 195967001 J45.0,J45.9");
    assertThat(InheritanceService.fingerprint(new InheritanceCalculator.Result("C", InheritanceCalculator.State.BLOCKED, "B",
        java.util.List.of(), java.util.List.of()))).isEqualTo("BLOCKED B");
    assertThat(InheritanceService.fingerprint(new InheritanceCalculator.Result("C", InheritanceCalculator.State.CONFLICT, null,
        java.util.List.of(), java.util.List.of("A", "B")))).isNull();
  }

  @Test
  void anAcceptedRowChangesWhenItInheritsSomethingElse() {
    final InheritanceCalculator.Row row = new InheritanceCalculator.Row("C", false, false, java.util.List.of());
    final InheritanceService.LoadedRow accepted = new InheritanceService.LoadedRow(1L, "C", row, MapStatus.MAPPED,
        "INHERITED 195967001 J45.9");
    assertThat(InheritanceService.inheritanceChanged(accepted, inherited("195967001", "J45.9"))).isFalse();
    assertThat(InheritanceService.inheritanceChanged(accepted, inherited("195967001", "J45.0"))).isTrue();
    assertThat(InheritanceService.inheritanceChanged(new InheritanceService.LoadedRow(1L, "C", row, MapStatus.UNMAPPED, null),
        inherited("195967001", "J45.0"))).isFalse();
  }
}
