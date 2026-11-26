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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.ConceptMap.ConceptMapGroupComponent;
import org.hl7.fhir.r4.model.ConceptMap.SourceElementComponent;
import org.hl7.fhir.r4.model.ConceptMap.TargetElementComponent;
import org.hl7.fhir.r4.model.Enumerations.ConceptMapEquivalence;

/**
 * The inverse of a ConceptMap: source and target value sets, systems and versions swapped, one element per
 * target code with the source codes that mapped to it as its targets, and the equivalence inverted (wider and
 * narrower swap; equivalent and the others stay). Targets without a code (unmatched, no map) cannot be
 * inverted and are left out. Comments (e.g. where an inherited mapping comes from) are kept.
 */
public final class ConceptMapInverter {

  private ConceptMapInverter() {
  }

  public static ConceptMap invert(ConceptMap map) {
    final ConceptMap inverse = map.copy();
    inverse.setTitle(map.getTitle() == null ? null : map.getTitle() + " (inverse)");
    inverse.setName(map.getName() == null ? null : map.getName() + "_inverse");
    inverse.setSource(map.getTarget());
    inverse.setTarget(map.getSource());
    inverse.getGroup().clear();
    for (final ConceptMapGroupComponent group : map.getGroup()) {
      final ConceptMapGroupComponent inverted = inverse.addGroup();
      inverted.setSource(group.getTarget());
      inverted.setSourceVersion(group.getTargetVersion());
      inverted.setTarget(group.getSource());
      inverted.setTargetVersion(group.getSourceVersion());
      // target code -> its new element, in order of first appearance; a source code only once per element
      final Map<String, SourceElementComponent> elements = new LinkedHashMap<>();
      final Map<String, Set<String>> added = new LinkedHashMap<>();
      for (final SourceElementComponent element : group.getElement()) {
        for (final TargetElementComponent target : element.getTarget()) {
          if (target.getCode() == null || target.getCode().isBlank()
              || target.getEquivalence() == ConceptMapEquivalence.UNMATCHED) {
            continue;
          }
          if (!added.computeIfAbsent(target.getCode(), c -> new LinkedHashSet<>()).add(element.getCode())) {
            continue;
          }
          final SourceElementComponent newElement = elements.computeIfAbsent(target.getCode(), code ->
              inverted.addElement().setCode(code).setDisplay(target.getDisplay()));
          final TargetElementComponent newTarget = newElement.addTarget()
              .setCode(element.getCode())
              .setDisplay(element.getDisplay())
              .setEquivalence(invert(target.getEquivalence()));
          if (target.hasComment()) {
            newTarget.setComment(target.getComment());
          }
        }
      }
    }
    return inverse;
  }

  /** The equivalence of the source to the target, seen from the target. */
  static ConceptMapEquivalence invert(ConceptMapEquivalence equivalence) {
    if (equivalence == null) {
      return null;
    }
    switch (equivalence) {
      case WIDER:
        return ConceptMapEquivalence.NARROWER;
      case NARROWER:
        return ConceptMapEquivalence.WIDER;
      case SUBSUMES:
        return ConceptMapEquivalence.SPECIALIZES;
      case SPECIALIZES:
        return ConceptMapEquivalence.SUBSUMES;
      default:
        return equivalence;
    }
  }
}
