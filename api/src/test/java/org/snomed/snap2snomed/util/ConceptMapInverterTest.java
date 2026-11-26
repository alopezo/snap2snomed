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

import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.ConceptMap.ConceptMapGroupComponent;
import org.hl7.fhir.r4.model.ConceptMap.SourceElementComponent;
import org.hl7.fhir.r4.model.Enumerations.ConceptMapEquivalence;
import org.hl7.fhir.r4.model.UriType;
import org.junit.jupiter.api.Test;

class ConceptMapInverterTest {

  private static ConceptMap forward() {
    final ConceptMap map = new ConceptMap();
    map.setTitle("Emergency");
    map.setName("Emergency");
    map.setSource(new UriType("http://local/vs"));
    map.setTarget(new UriType("http://snomed.info/sct?fhir_vs=ecl/*"));
    final ConceptMapGroupComponent group = map.addGroup();
    group.setSource("http://local").setSourceVersion("1")
        .setTarget("http://snomed.info/sct").setTargetVersion("http://snomed.info/sct/900000000000207008/version/20260701");
    final SourceElementComponent t1 = group.addElement().setCode("T1").setDisplay("Shortness of breath");
    t1.addTarget().setCode("267036007").setDisplay("Dyspnea").setEquivalence(ConceptMapEquivalence.EQUIVALENT);
    t1.addTarget().setCode("195967001").setDisplay("Asthma").setEquivalence(ConceptMapEquivalence.WIDER)
        .setComment("INHERITED 1234");
    group.addElement().setCode("T2").setDisplay("Breathless").addTarget().setCode("267036007").setDisplay("Dyspnea")
        .setEquivalence(ConceptMapEquivalence.NARROWER);
    group.addElement().setCode("T3").setDisplay("Nothing").addTarget().setEquivalence(ConceptMapEquivalence.UNMATCHED);
    return map;
  }

  @Test
  void swapsTheSystemsVersionsAndValueSets() {
    final ConceptMap inverse = ConceptMapInverter.invert(forward());
    assertThat(inverse.getTitle()).isEqualTo("Emergency (inverse)");
    assertThat(inverse.getName()).isEqualTo("Emergency_inverse");
    assertThat(inverse.getSourceUriType().getValue()).isEqualTo("http://snomed.info/sct?fhir_vs=ecl/*");
    assertThat(inverse.getTargetUriType().getValue()).isEqualTo("http://local/vs");
    final ConceptMapGroupComponent group = inverse.getGroupFirstRep();
    assertThat(group.getSource()).isEqualTo("http://snomed.info/sct");
    assertThat(group.getSourceVersion()).isEqualTo("http://snomed.info/sct/900000000000207008/version/20260701");
    assertThat(group.getTarget()).isEqualTo("http://local");
    assertThat(group.getTargetVersion()).isEqualTo("1");
  }

  @Test
  void groupsByTargetInvertsTheEquivalenceAndLeavesUnmatchedOut() {
    final ConceptMapGroupComponent group = ConceptMapInverter.invert(forward()).getGroupFirstRep();
    assertThat(group.getElement()).extracting(SourceElementComponent::getCode).containsExactly("267036007", "195967001");

    final SourceElementComponent dyspnea = group.getElement().get(0);
    assertThat(dyspnea.getDisplay()).isEqualTo("Dyspnea");
    assertThat(dyspnea.getTarget()).extracting(t -> t.getCode() + " " + t.getEquivalence().toCode())
        .containsExactly("T1 equivalent", "T2 wider");

    final SourceElementComponent asthma = group.getElement().get(1);
    assertThat(asthma.getTargetFirstRep().getCode()).isEqualTo("T1");
    assertThat(asthma.getTargetFirstRep().getDisplay()).isEqualTo("Shortness of breath");
    assertThat(asthma.getTargetFirstRep().getEquivalence()).isEqualTo(ConceptMapEquivalence.NARROWER);
    assertThat(asthma.getTargetFirstRep().getComment()).isEqualTo("INHERITED 1234");
  }

  @Test
  void invertingTwiceGivesTheSameMappings() {
    final ConceptMapGroupComponent twice = ConceptMapInverter.invert(ConceptMapInverter.invert(forward())).getGroupFirstRep();
    assertThat(twice.getElement()).extracting(SourceElementComponent::getCode).containsExactly("T1", "T2");
    assertThat(twice.getElement().get(0).getTarget()).extracting(t -> t.getCode() + " " + t.getEquivalence().toCode())
        .containsExactly("267036007 equivalent", "195967001 wider");
  }
}
