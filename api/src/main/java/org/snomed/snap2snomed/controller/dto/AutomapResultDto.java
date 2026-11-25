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

/**
 * What automapping a task of a map from SNOMED CT did: of the unmapped rows, how many matched a target code,
 * how many got it as a new target, and how many matches were left out because the row already inherits
 * exactly that code (a target of its own would only stop inheritance for its subtree).
 */
public record AutomapResultDto(int rows, int matched, int created, int alreadyInherited) {
}
