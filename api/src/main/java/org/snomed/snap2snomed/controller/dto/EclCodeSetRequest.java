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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Creates the source code set of a map from SNOMED CT by expanding an ECL expression. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EclCodeSetRequest {

  @NotBlank
  @Size(max = 100)
  private String name;

  /** SNOMED CT version URI, e.g. http://snomed.info/sct/900000000000207008/version/20250101 */
  @NotBlank
  @Pattern(regexp = "^http://snomed.info/x?sct/[0-9]{6,18}/version/[0-9]{8}$",
      message = "version must be a SNOMED CT version URI")
  private String version;

  @NotBlank
  @Size(max = 1024)
  private String ecl;

  /** Dialect sequence for the concept terms (see LanguageDialects); null means the English FSN. */
  @Size(max = 512)
  private String displayLanguage;
}
