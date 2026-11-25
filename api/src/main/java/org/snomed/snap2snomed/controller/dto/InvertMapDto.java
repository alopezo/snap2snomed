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

import org.snomed.snap2snomed.model.enumeration.InheritanceMode;

/** Creating a map from SNOMED CT from a map to SNOMED CT, its rows inverted (see MapInversionService). */
public final class InvertMapDto {

  private InvertMapDto() {
  }

  /** The new project's title and description, and whether the new map has inheritance. */
  public record Request(String title, String description, boolean inheritance, InheritanceMode inheritanceMode) {
  }

  /**
   * The new project and map, and what was carried over: SNOMED CT concepts (the new source), targets (the
   * original source codes), refined targets mapped from their focus concept, and what was left out.
   */
  public record Result(long projectId, long mapId, int concepts, int targets, int refinedTargets,
      int inactiveConcepts, int noMapRows) {
  }
}
