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
import static org.assertj.core.groups.Tuple.tuple;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.snomed.snap2snomed.controller.dto.InheritanceDto;
import org.snomed.snap2snomed.model.MapView;
import org.snomed.snap2snomed.model.enumeration.MappingRelationship;

class InheritanceExportTest {

  private static MapView view(String sourceCode, String targetCode) {
    return MapView.builder().sourceIndex(1L).sourceCode(sourceCode).targetCode(targetCode)
        .relationship(targetCode == null ? null : MappingRelationship.TARGET_EQUIVALENT).noMap(false).build();
  }

  private static InheritanceDto.RowDto row(String code, String state, String from, List<InheritanceDto.TargetDto> targets,
      List<String> candidates) {
    return new InheritanceDto.RowDto(1L, code, code + " display", state, false, from, null, targets, candidates, 0, 0);
  }

  @Test
  void unmappedRowsGetOneLinePerInheritedTargetAndEveryLineAnOrigin() {
    final InheritanceDto inheritance = new InheritanceDto(Map.of(), List.of(
        row("A", "ASSERTED", null, List.of(), List.of()),
        row("B", "INHERITED", "A", List.of(new InheritanceDto.TargetDto("X1", "One", "TARGET_BROADER"),
            new InheritanceDto.TargetDto("X2", "Two", "TARGET_INEXACT")), List.of()),
        row("C", "CONFLICT", null, List.of(), List.of("A", "F")),
        row("D", "UNCOVERED", null, List.of(), List.of())));
    final List<InheritanceExport.Line> lines = InheritanceExport.apply(
        List.of(view("A", "X1"), view("B", null), view("C", null), view("D", null)), inheritance);
    assertThat(lines).extracting(l -> l.view().getSourceCode(), l -> l.view().getTargetCode(),
            l -> l.view().getRelationship(), InheritanceExport.Line::origin, InheritanceExport.Line::inheritedFrom)
        .containsExactly(
            tuple("A", "X1", MappingRelationship.TARGET_EQUIVALENT, "ASSERTED", null),
            tuple("B", "X1", MappingRelationship.TARGET_BROADER, "INHERITED", "A"),
            tuple("B", "X2", MappingRelationship.TARGET_INEXACT, "INHERITED", "A"),
            tuple("C", null, null, "CONFLICT", "A F"),
            tuple("D", null, null, "UNCOVERED", null));
  }

  @Test
  void plainLinesHaveNoOrigin() {
    assertThat(InheritanceExport.plain(List.of(view("A", "X1")))).singleElement()
        .extracting(InheritanceExport.Line::origin).isNull();
  }

  @Test
  void generatedConceptsAreAppendedAfterTheRows() {
    final InheritanceDto inheritance = new InheritanceDto(Map.of(), List.of(row("A", "ASSERTED", null, List.of(), List.of())));
    final List<InheritanceExport.Line> lines = InheritanceExport.apply(List.of(view("A", "X1")), inheritance, List.of(
        row("G1", "INHERITED", "A", List.of(new InheritanceDto.TargetDto("X1", "One", "TARGET_BROADER")), List.of()),
        row("G2", "BLOCKED", "S", List.of(), List.of())));
    assertThat(lines).extracting(l -> l.view().getSourceCode(), l -> l.view().getSourceIndex(), l -> l.view().getTargetCode(),
            InheritanceExport.Line::origin, InheritanceExport.Line::inheritedFrom)
        .containsExactly(
            tuple("A", 1L, "X1", "ASSERTED", null),
            tuple("G1", null, "X1", "GENERATED", "A"),
            tuple("G2", null, null, "BLOCKED", "S"));
  }
}
