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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ValueSet;
import org.ihtsdo.snomed.util.rf2.schema.RF2SchemaConstants;
import org.snomed.snap2snomed.config.Snap2snomedConfiguration;
import org.snomed.snap2snomed.controller.dto.TargetDisplayRefreshResult;
import org.snomed.snap2snomed.model.Map;
import org.snomed.snap2snomed.model.MapRowTarget;
import org.snomed.snap2snomed.problem.Snap2SnomedProblem;
import org.snomed.snap2snomed.repository.MapRepository;
import org.snomed.snap2snomed.util.LanguageDialects;
import org.snomed.snap2snomed.util.TargetExpression;
import org.snomed.snap2snomed.repository.MapRowTargetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Lists;

import lombok.extern.slf4j.Slf4j;

/**
 * Re-resolves the display term of a map's targets from the terminology server, using the map's
 * display language: the preferred term of the first dialect in the sequence that has one, or the
 * English FSN when the map has no display language.
 */
@Component
@Slf4j
public class TargetDisplayResolver {

  static final String FSN_TYPE = "900000000000003001";

  @Autowired
  TerminologyProvider terminologyProvider;

  @Autowired
  Snap2snomedConfiguration configuration;

  @Autowired
  MapRepository mapRepository;

  @Autowired
  MapRowTargetRepository mapRowTargetRepository;

  /**
   * Updates the stored target display of every target in the map whose term differs from the
   * one the terminology server returns. Codes the server does not return keep their display.
   *
   * @throws RuntimeException if the terminology server cannot be reached or fails; nothing is
   *     updated in that case
   */
  @Transactional
  public TargetDisplayRefreshResult resolveTargetDisplays(Long mapId) {
    return doResolve(mapId);
  }

  /**
   * Same as {@link #resolveTargetDisplays(Long)}, but a terminology server failure is only logged
   * and every target keeps its display. For flows that must not fail because of it (mapping
   * import, new map version).
   *
   * @return the refresh result, or null if the terminology server failed
   */
  @Transactional
  public TargetDisplayRefreshResult tryResolveTargetDisplays(Long mapId) {
    try {
      return doResolve(mapId);
    } catch (final Snap2SnomedProblem e) {
      throw e;
    } catch (final RuntimeException e) {
      // Caught here, inside the transactional proxy, so a terminology server failure never marks
      // the caller's transaction (e.g. a mapping import) as rollback-only.
      log.warn("Could not resolve target displays for map {}: {}", mapId, e.getMessage());
      return null;
    }
  }

  private TargetDisplayRefreshResult doResolve(Long mapId) {
    final Map map = mapRepository.findById(mapId)
        .orElseThrow(() -> Snap2SnomedProblem.of(HttpStatus.NOT_FOUND, "No Map found with id " + mapId));
    final Set<String> codes = mapRowTargetRepository.findByMapId(mapId).stream()
        .map(MapRowTarget::getTargetCode)
        .filter(code -> code != null && (FhirService.isValidSctId(code, RF2SchemaConstants.PartionIdentifier.CONCEPT)
            || TargetExpression.parse(code).isPresent()))
        .collect(Collectors.toSet());
    if (codes.isEmpty()) {
      return new TargetDisplayRefreshResult(0, 0, 0);
    }
    // A refined target needs the terms of its focus concept and of its refinement values
    final Set<String> concepts = new HashSet<>();
    codes.forEach(code -> TargetExpression.parse(code).ifPresentOrElse(expression -> {
      concepts.add(expression.getFocus());
      concepts.addAll(expression.getValues());
    }, () -> concepts.add(code)));
    // Fetch everything first: if the server fails, nothing has been updated yet
    final java.util.Map<String, String> conceptDisplays = fetchDisplays(concepts, map.getToVersion(), map.getDisplayLanguage());
    final java.util.Map<String, String> displays = targetDisplays(codes, conceptDisplays);
    int updated = 0;
    for (final java.util.Map.Entry<String, String> entry : displays.entrySet()) {
      updated += mapRowTargetRepository.updateTargetDisplayForMap(mapId, entry.getKey(), entry.getValue());
    }
    final TargetDisplayRefreshResult result = new TargetDisplayRefreshResult(codes.size(), updated, codes.size() - displays.size());
    log.info("Resolved target displays for map {} ({}, display language {})",
        mapId, result, map.getDisplayLanguage() == null ? "English FSN" : map.getDisplayLanguage());
    return result;
  }

  /**
   * The display of each target code: the concept's term, or for a refined target the focus term followed
   * by the value terms (without semantic tag). Targets missing any term are left out.
   */
  static java.util.Map<String, String> targetDisplays(Collection<String> codes, java.util.Map<String, String> conceptDisplays) {
    final java.util.Map<String, String> displays = new HashMap<>();
    for (final String code : codes) {
      final Optional<TargetExpression> expression = TargetExpression.parse(code);
      if (expression.isEmpty()) {
        if (conceptDisplays.containsKey(code)) {
          displays.put(code, conceptDisplays.get(code));
        }
        continue;
      }
      final String focusDisplay = conceptDisplays.get(expression.get().getFocus());
      final List<String> valueDisplays = expression.get().getValues().stream()
          .map(conceptDisplays::get)
          .map(TargetDisplayResolver::withoutSemanticTag)
          .collect(Collectors.toList());
      if (focusDisplay != null && !valueDisplays.contains(null)) {
        displays.put(code, TargetExpression.composeDisplay(focusDisplay, valueDisplays));
      }
    }
    return displays;
  }

  static String withoutSemanticTag(String display) {
    return display == null ? null : display.replaceFirst("\\s*\\([^()]*\\)$", "");
  }

  /** Fetches the display term of each code, in batches of {@code expandBatchSize} codes. */
  java.util.Map<String, String> fetchDisplays(Collection<String> codes, String codeSystemVersion, String displayLanguage) {
    final boolean englishFsn = LanguageDialects.normalize(displayLanguage) == null;
    final String acceptLanguage = englishFsn
        ? "en"
        : LanguageDialects.toServerFormat(displayLanguage, terminologyProvider.getServerKind());
    final int batchSize = configuration.getDefaultTerminologyServer().getExpandBatchSize().intValue();

    final java.util.Map<String, String> displays = new HashMap<>();
    for (final List<String> batch : Lists.partition(new ArrayList<>(codes), batchSize)) {
      final ValueSet.ConceptSetComponent include = new ValueSet.ConceptSetComponent();
      include.setSystem(FhirService.DEFAULT_CODE_SYSTEM);
      include.setVersion(codeSystemVersion);
      batch.forEach(code -> include.addConcept(new ValueSet.ConceptReferenceComponent(new CodeType(code))));
      final ValueSet valueSet = new ValueSet();
      valueSet.getCompose().addInclude(include);

      final Parameters parameters = new Parameters();
      parameters.addParameter().setName("valueSet").setResource(valueSet);
      parameters.addParameter("count", new IntegerType(batch.size()));
      parameters.addParameter("includeDesignations", new BooleanType(englishFsn));

      final ValueSet expansion = terminologyProvider.getGenericClient().operation()
          .onType(ValueSet.class)
          .named("$expand")
          .withParameters(parameters)
          .returnResourceType(ValueSet.class)
          .withAdditionalHeader("Accept-Language", acceptLanguage)
          .execute();

      for (final ValueSet.ValueSetExpansionContainsComponent concept : expansion.getExpansion().getContains()) {
        final String display = englishFsn ? englishFsn(concept) : concept.getDisplay();
        if (display != null && !display.isBlank()) {
          displays.put(concept.getCode(), display);
        }
      }
    }
    return displays;
  }

  static String englishFsn(ValueSet.ValueSetExpansionContainsComponent concept) {
    return concept.getDesignation().stream()
        .filter(designation -> designation.hasUse() && FSN_TYPE.equals(designation.getUse().getCode()))
        .filter(designation -> designation.getLanguage() != null && designation.getLanguage().startsWith("en"))
        .map(ValueSet.ConceptReferenceDesignationComponent::getValue)
        .findFirst()
        .orElse(null);
  }
}
