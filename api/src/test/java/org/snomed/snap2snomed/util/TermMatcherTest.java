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

package org.snomed.snap2snomed.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.snomed.snap2snomed.util.TermMatcher.Candidate;

class TermMatcherTest {

  private final TermMatcher matcher = new TermMatcher(List.of(
      new Candidate("J45", "Asthma"),
      new Candidate("J44", "Chronic obstructive pulmonary disease"),
      new Candidate("E11", "Diabetes mellitus type 2"),
      new Candidate("E10", "Diabetes mellitus type 1"),
      new Candidate("I10", "Essential (primary) hypertension"),
      new Candidate("R51", "Headache"),
      new Candidate("J18", "Pneumonia, unspecified organism"),
      new Candidate("K35", "Apendicitis aguda"),
      new Candidate("J45.0", "Predominantly allergic asthma"),
      new Candidate("J45.1", "Nonallergic asthma"),
      new Candidate("J46", "Status asthmaticus")));

  private String best(String term) {
    return matcher.best(term, TermMatcher.DEFAULT_THRESHOLD).map(TermMatcher.Match::code).orElse(null);
  }

  @Test
  void matchesTheSameTermIgnoringCaseAccentsAndTheSemanticTag() {
    assertThat(best("Asthma (disorder)")).isEqualTo("J45");
    assertThat(best("Status asthmaticus")).isEqualTo("J46");
    assertThat(best("HEADACHE")).isEqualTo("R51");
    assertThat(best("Apendicitis Agúda")).isEqualTo("K35");
  }

  @Test
  void matchesTheSameWordsInAnotherOrderAndTellsNumbersApart() {
    assertThat(best("Type 2 diabetes mellitus")).isEqualTo("E11");
    assertThat(best("Type 1 diabetes mellitus")).isEqualTo("E10");
  }

  @Test
  void toleratesTyposAndPlurals() {
    assertThat(best("Chronic obstructive pulmonary diseases")).isEqualTo("J44");
    assertThat(best("Hypertension essential")).isEqualTo("I10");
  }

  @Test
  void neverMatchesTheNegatedForm() {
    assertThat(best("Allergic asthma (disorder)")).isEqualTo("J45.0");
    assertThat(best("Non-allergic asthma")).isEqualTo("J45.1");
    assertThat(best("Asthma without status asthmaticus (disorder)")).isNull();
  }

  @Test
  void leavesANarrowerTermUnmatched() {
    // inheritance gives it the parent's code; a partial match would block that
    assertThat(best("Severe persistent asthma")).isNull();
    assertThat(best("Myocardial infarction")).isNull();
  }

  @Test
  void scoresAnExactMatchAsOne() {
    assertThat(matcher.best("Asthma", 0).orElseThrow().score()).isEqualTo(1.0);
  }
}
