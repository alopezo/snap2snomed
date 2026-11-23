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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Outcome of re-resolving a map's target display terms. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TargetDisplayRefreshResult {

  /** Distinct target codes in the map. */
  private int codeCount;

  /** Targets whose display changed. */
  private int updatedCount;

  /** Codes the terminology server did not return (unknown in the map's version); they keep their display. */
  private int unresolvedCount;
}
