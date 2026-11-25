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

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.snomed.snap2snomed.model.enumeration.MappingRelationship;
import org.snomed.snap2snomed.util.InheritanceCalculator.Result;
import org.snomed.snap2snomed.util.InheritanceCalculator.Row;
import org.snomed.snap2snomed.util.InheritanceCalculator.State;
import org.snomed.snap2snomed.util.InheritanceCalculator.Target;

/**
 * Hierarchy used (proper descendants):
 * <pre>
 *   A ── B ── C ── D
 *   │         │
 *   │         E (also a child of F)
 *   F ────────┘
 *   G (unrelated)
 * </pre>
 */
class InheritanceCalculatorTest {

  private static final Map<String, Set<String>> DESCENDANTS = Map.of(
      "A", Set.of("B", "C", "D", "E"),
      "B", Set.of("C", "D", "E"),
      "C", Set.of("D", "E"),
      "D", Set.of(),
      "E", Set.of(),
      "F", Set.of("E"),
      "G", Set.of());

  private static Row mapped(String code, String target, MappingRelationship relationship) {
    return new Row(code, false, false, List.of(new Target(target, target + " display", relationship)));
  }

  private static Row empty(String code) {
    return new Row(code, false, false, List.of());
  }

  private static Map<String, Result> compute(Row... rows) {
    final Map<String, Result> results = new java.util.LinkedHashMap<>();
    InheritanceCalculator.compute(List.of(rows), DESCENDANTS::get).forEach(r -> results.put(r.code(), r));
    return results;
  }

  @Test
  void descendantsTakeTheNearestMappedAncestorWithAdjustedRelationship() {
    final Map<String, Result> results = compute(mapped("A", "X1", TARGET_EQUIVALENT), empty("B"),
        mapped("C", "X2", TARGET_NARROWER), empty("D"), empty("G"));
    assertThat(results.get("A").state()).isEqualTo(State.ASSERTED);
    assertThat(results.get("B").state()).isEqualTo(State.INHERITED);
    assertThat(results.get("B").from()).isEqualTo("A");
    assertThat(results.get("B").targets()).extracting(Target::code, Target::relationship)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("X1", TARGET_BROADER));
    assertThat(results.get("D").from()).isEqualTo("C");
    assertThat(results.get("D").targets()).extracting(Target::relationship).containsExactly(TARGET_INEXACT);
    assertThat(results.get("G").state()).isEqualTo(State.UNCOVERED);
  }

  @Test
  void stopAndNoMapBlockTheirSubtree() {
    final Map<String, Result> stopped = compute(mapped("A", "X1", TARGET_EQUIVALENT),
        new Row("B", false, true, List.of()), empty("C"), empty("D"));
    assertThat(stopped.get("B").state()).isEqualTo(State.STOPPED);
    assertThat(stopped.get("C").state()).isEqualTo(State.BLOCKED);
    assertThat(stopped.get("C").from()).isEqualTo("B");
    assertThat(stopped.get("D").state()).isEqualTo(State.BLOCKED);

    final Map<String, Result> noMap = compute(mapped("A", "X1", TARGET_EQUIVALENT),
        new Row("C", true, false, List.of()), empty("D"));
    assertThat(noMap.get("C").state()).isEqualTo(State.NO_MAP);
    assertThat(noMap.get("D").state()).isEqualTo(State.BLOCKED);
  }

  @Test
  void aMappedRowBelowAStopStillPassesItsTargetsDown() {
    final Map<String, Result> results = compute(mapped("A", "X1", TARGET_EQUIVALENT),
        new Row("B", false, true, List.of()), mapped("C", "X2", TARGET_BROADER), empty("D"));
    assertThat(results.get("D").state()).isEqualTo(State.INHERITED);
    assertThat(results.get("D").from()).isEqualTo("C");
  }

  @Test
  void disagreeingNearestAncestorsAreAConflict() {
    final Map<String, Result> results = compute(mapped("C", "X1", TARGET_EQUIVALENT),
        mapped("F", "X2", TARGET_EQUIVALENT), empty("E"));
    assertThat(results.get("E").state()).isEqualTo(State.CONFLICT);
    assertThat(results.get("E").candidates()).containsExactlyInAnyOrder("C", "F");
  }

  @Test
  void agreeingNearestAncestorsAreInherited() {
    final Map<String, Result> results = compute(mapped("C", "X1", TARGET_EQUIVALENT),
        mapped("F", "X1", TARGET_BROADER), empty("E"));
    assertThat(results.get("E").state()).isEqualTo(State.INHERITED);
  }

  @Test
  void aStopOnOnePathAndAMappingOnAnotherIsAConflict() {
    final Map<String, Result> results = compute(new Row("C", false, true, List.of()),
        mapped("F", "X2", TARGET_EQUIVALENT), empty("E"));
    assertThat(results.get("E").state()).isEqualTo(State.CONFLICT);
  }

  @Test
  void onlyAnchorsNeedDescendants() {
    assertThat(InheritanceCalculator.anchorCodes(List.of(mapped("A", "X", TARGET_EQUIVALENT), empty("B"),
        new Row("C", true, false, List.of()), new Row("D", false, true, List.of())))).containsExactlyInAnyOrder("A", "C", "D");
  }
}
