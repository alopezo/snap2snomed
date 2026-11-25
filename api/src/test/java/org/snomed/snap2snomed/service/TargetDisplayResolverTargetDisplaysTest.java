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

package org.snomed.snap2snomed.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TargetDisplayResolverTargetDisplaysTest {

  @Test
  void composesRefinedTargetDisplaysWithoutValueSemanticTags() {
    final Map<String, String> concepts = Map.of(
        "71620000", "Fracture of femur (disorder)",
        "7771000", "Left (qualifier value)",
        "24484000", "Severe (severity modifier) (qualifier value)",
        "125605004", "Fracture of bone (disorder)");
    final Map<String, String> displays = TargetDisplayResolver.targetDisplays(
        List.of("71620000:272741003=7771000,246112005=24484000", "125605004"), concepts);
    assertThat(displays).containsExactlyInAnyOrderEntriesOf(Map.of(
        "71620000:272741003=7771000,246112005=24484000", "Fracture of femur (disorder) - Left - Severe (severity modifier)",
        "125605004", "Fracture of bone (disorder)"));
  }

  @Test
  void leavesOutTargetsWithAMissingTerm() {
    final Map<String, String> displays = TargetDisplayResolver.targetDisplays(
        List.of("71620000:272741003=24028007", "22298006"), Map.of("71620000", "Fracture of femur"));
    assertThat(displays).isEmpty();
  }
}
