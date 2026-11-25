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

package org.snomed.snap2snomed.controller.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * How the source codes of a map version differ from the previous version's: codes in both (their rows
 * and targets were copied), codes only in the new version (unmapped) and codes that are gone.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceChangesDto {

  private int keptCount;
  private int addedCount;
  private List<Code> removed;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Code {
    private String code;
    private String display;
  }
}
