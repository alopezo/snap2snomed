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

package org.snomed.snap2snomed.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.snomed.snap2snomed.controller.dto.SourceChangesDto;
import org.snomed.snap2snomed.controller.dto.TargetCodeDto;
import org.snomed.snap2snomed.model.AdditionalCodeColumn;
import org.snomed.snap2snomed.model.AdditionalCodeValue;
import org.snomed.snap2snomed.model.ImportedCode;

class TargetCodeRestControllerTest {

  @Test
  void namesTheAdditionalColumnsOfACode() {
    final ImportedCode code = new ImportedCode(1L, "J45", null, 1L, "Asthma",
        List.of(new AdditionalCodeValue("X"), new AdditionalCodeValue("Respiratory")));
    final AdditionalCodeColumn chapter = new AdditionalCodeColumn();
    chapter.setName("Chapter");
    final TargetCodeDto dto = TargetCodeRestController.toDto(code, List.of(chapter));
    assertThat(dto.getCode()).isEqualTo("J45");
    assertThat(dto.getDisplay()).isEqualTo("Asthma");
    assertThat(dto.getAdditionalColumns()).containsExactly(new TargetCodeDto.Column("Chapter", "X"));
  }

  @Test
  void codeWithoutAdditionalColumns() {
    final ImportedCode code = new ImportedCode(1L, "J45", null, 1L, "Asthma", null);
    assertThat(TargetCodeRestController.toDto(code, null).getAdditionalColumns()).isEmpty();
  }

  private static ImportedCode code(String code, String display) {
    return new ImportedCode(null, code, null, 1L, display, null);
  }

  @Test
  void comparesTheSourceCodesOfTwoVersions() {
    final SourceChangesDto changes = TargetCodeRestController.compare(
        List.of(code("1", "Kept"), code("2", "Gone"), code("3", "Kept too")),
        List.of(code("3", "Kept too"), code("1", "Kept"), code("4", "New"), code("5", "New too")));
    assertThat(changes.getKeptCount()).isEqualTo(2);
    assertThat(changes.getAddedCount()).isEqualTo(2);
    assertThat(changes.getRemoved()).containsExactly(new SourceChangesDto.Code("2", "Gone"));
  }
}
