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

package org.snomed.snap2snomed.model.enumeration;

/**
 * Where the inheritance of a map from SNOMED CT reaches: unmapped concepts always take the targets of
 * their nearest mapped ancestor; the mode only says which descendants count.
 */
public enum InheritanceMode {
  /** Only the concepts of the map's source: rows inherit from other rows, nothing is generated. */
  WITHIN_SOURCE,
  /**
   * Every descendant in the map's SNOMED CT version: rows inherit as within the source, and the
   * descendants that are not rows are generated at export time.
   */
  BEYOND_SOURCE
}
