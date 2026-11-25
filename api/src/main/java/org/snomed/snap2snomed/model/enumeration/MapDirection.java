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
 * Which side of a map is SNOMED CT. The map's toVersion, toScope and displayLanguage always describe
 * the SNOMED CT side: the target for {@link #TO_SNOMED}, the source for {@link #FROM_SNOMED}.
 */
public enum MapDirection {
  /** Imported codes mapped to SNOMED CT concepts (the original behaviour). */
  TO_SNOMED,
  /**
   * SNOMED CT concepts (an ECL expansion frozen into an imported code set when the map is created)
   * mapped to the codes of another imported code set.
   */
  FROM_SNOMED
}
