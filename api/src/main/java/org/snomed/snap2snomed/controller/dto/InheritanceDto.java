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
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Inheritance in a map from SNOMED CT: a count per state and the state of every row. */
@Data
@NoArgsConstructor
public class InheritanceDto {

  /** Rows per state (ASSERTED, NO_MAP, STOPPED, INHERITED, BLOCKED, CONFLICT, UNCOVERED). */
  private Map<String, Integer> counts;
  private List<RowDto> rows;

  /** Concepts generated at export time (descendants that are not rows) per state, and their total. */
  private Map<String, Integer> generatedCounts = Map.of();
  private int generatedTotal;
  /** Mapped or stopping concepts with more descendants in the scope than {@link #maxGenerated}. */
  private List<TooLarge> tooLarge = List.of();
  /** All generated concepts together are more than {@link #maxGenerated}. */
  private boolean generatedTooLarge;
  private int maxGenerated;
  /** Rows without targets whose inheritance was accepted, and those whose inheritance changed since. */
  private int acceptedCount;
  private int changedCount;

  public InheritanceDto(Map<String, Integer> counts, List<RowDto> rows) {
    this.counts = counts;
    this.rows = rows;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RowDto {
    private Long rowId;
    private String code;
    private String display;
    private String state;
    private boolean stopInheritance;
    /** INHERITED: the ancestor the targets come from; BLOCKED: the row that stops it. */
    private String fromCode;
    private String fromDisplay;
    private List<TargetDto> targets;
    /** CONFLICT: the disagreeing nearest ancestors. */
    private List<String> candidates;
    /** ASSERTED: how many rows inherit its targets. */
    private int inheritedBy;
    /** ASSERTED: how many generated concepts inherit its targets. */
    private int generatedBy;
    /** The row's workflow status. */
    private String status;
    /** Its inheritance was accepted (no targets of its own). */
    private boolean accepted;
    /** Accepted, but it inherits something else now. */
    private boolean inheritanceChanged;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AcceptRequest {
    /** Rows to accept; empty or null means every row that can be accepted (within the task, if one is given). */
    private List<Long> rowIds;
    /** The author task the user works in; without one, only a project owner can accept. */
    private Long taskId;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AcceptResult {
    private int accepted;
    /** Rows left as they were, by reason: an inheritance state, NOT_IN_TASK or NOT_EDITABLE. */
    private Map<String, Integer> skipped;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TooLarge {
    private String code;
    private String display;
    private int descendants;
  }

  /**
   * The descendants of a concept in a map with inheritance: how many there are (rows of the map, and beyond the
   * source when inheritance reaches there), and how many would take its mapping if it were mapped (or do, when
   * it is): those whose nearest mapped ancestor it would be. When it has more descendants than the limit, only
   * {@link #tooLarge} and {@link #descendants} are set.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DescendantsInfo {
    private String code;
    private boolean tooLarge;
    private int descendants;
    private int inSource;
    private int beyondSource;
    private int wouldInherit;
    private int wouldGenerate;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class GeneratedPage {
    private int total;
    private List<RowDto> rows;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TargetDto {
    private String code;
    private String display;
    private String relationship;
  }
}
