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

/**
 * Terminology server implementation, detected from {@code CapabilityStatement.software.name}.
 * Drives server specific request formats such as the language dialect syntax.
 */
public enum TerminologyServerKind {
  SNOWSTORM,
  SNOWSTORM_LITE,
  ONTOSERVER,
  OTHER;

  /** Classifies a {@code CapabilityStatement.software.name} value. */
  public static TerminologyServerKind fromSoftwareName(String softwareName) {
    if (softwareName == null) {
      return OTHER;
    }
    final String name = softwareName.toLowerCase();
    if (name.contains("ontoserver")) {
      return ONTOSERVER;
    }
    if (name.contains("snowstorm lite")) {
      return SNOWSTORM_LITE;
    }
    if (name.contains("snowstorm")) {
      return SNOWSTORM;
    }
    return OTHER;
  }
}
