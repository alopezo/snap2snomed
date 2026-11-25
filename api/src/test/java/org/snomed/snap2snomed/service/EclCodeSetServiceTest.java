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

import org.junit.jupiter.api.Test;

class EclCodeSetServiceTest {

  @Test
  void codeSetVersionIsTheEffectiveTime() {
    assertThat(EclCodeSetService.effectiveTime("http://snomed.info/sct/11000221109/version/20260520")).isEqualTo("20260520");
    assertThat(EclCodeSetService.effectiveTime("http://snomed.info/xsct/900000000000207008/version/20250101")).isEqualTo("20250101");
  }

  @Test
  void capIsTenThousand() {
    assertThat(EclCodeSetService.MAX_CONCEPTS).isEqualTo(10000);
  }
}
