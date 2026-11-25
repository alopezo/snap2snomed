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

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InheritanceServiceTest {

  @Test
  void cachedDescendantsKeepTheirTerms() {
    final Map<String, String> concepts = new LinkedHashMap<>();
    concepts.put("389145006", "Allergic asthma (disorder)");
    concepts.put("426656000", "Severe persistent\tasthma");
    final String cached = InheritanceService.format(concepts);
    assertThat(InheritanceService.parse(cached)).containsExactly(
        Map.entry("389145006", "Allergic asthma (disorder)"), Map.entry("426656000", "Severe persistent asthma"));
    assertThat(InheritanceService.parse("")).isEmpty();
  }
}
