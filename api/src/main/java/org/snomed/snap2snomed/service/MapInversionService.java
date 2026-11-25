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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.snomed.snap2snomed.controller.dto.InvertMapDto;
import org.snomed.snap2snomed.model.ImportedCodeSet;
import org.snomed.snap2snomed.model.MapRowTarget;
import org.snomed.snap2snomed.model.Project;
import org.snomed.snap2snomed.model.User;
import org.snomed.snap2snomed.model.enumeration.InheritanceMode;
import org.snomed.snap2snomed.model.enumeration.MapDirection;
import org.snomed.snap2snomed.model.enumeration.MapStatus;
import org.snomed.snap2snomed.model.enumeration.MappingRelationship;
import org.snomed.snap2snomed.problem.BadRequestDataProblem;
import org.snomed.snap2snomed.problem.Snap2SnomedProblem;
import org.snomed.snap2snomed.problem.auth.NotAuthorisedProblem;
import org.snomed.snap2snomed.repository.MapRepository;
import org.snomed.snap2snomed.repository.MapRowRepository;
import org.snomed.snap2snomed.repository.MapRowTargetRepository;
import org.snomed.snap2snomed.repository.ProjectRepository;
import org.snomed.snap2snomed.security.AuthenticationFacade;
import org.snomed.snap2snomed.security.WebSecurity;
import org.snomed.snap2snomed.util.TargetExportForm;
import org.snomed.snap2snomed.util.TargetExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds a map from SNOMED CT from a map to SNOMED CT, in a new project: its source is the SNOMED CT concepts
 * the original map targets, and each of them is mapped back to the local codes that targeted it, with the
 * relationship inverted. A refined target (an expression) is mapped from its focus concept, its relationship
 * adjusted first as the focus is broader. No-map rows, rows without targets and concepts that are not active in
 * the version are left out. The new map is an ordinary map from SNOMED CT: its rows are DRAFT, to be reviewed.
 */
@Service
@Slf4j
public class MapInversionService {

  private static final Pattern CONCEPT_ID = Pattern.compile("[0-9]{6,18}");

  @Autowired
  MapRepository mapRepository;

  @Autowired
  ProjectRepository projectRepository;

  @Autowired
  MapRowRepository mapRowRepository;

  @Autowired
  MapRowTargetRepository mapRowTargetRepository;

  @Autowired
  EclCodeSetService eclCodeSetService;

  @Autowired
  CodeSetImportService codeSetImportService;

  @Autowired
  AuthenticationFacade authenticationFacade;

  @Autowired
  WebSecurity webSecurity;

  /** A row of the new map: a SNOMED CT concept mapped to an original source code. */
  record Inverted(String concept, String sourceCode, String sourceDisplay, MappingRelationship relationship,
      boolean refined) {
  }

  /** The relationship of the original source code to the concept, from the concept to the source code. */
  static MappingRelationship invert(MappingRelationship relationship) {
    if (relationship == null) {
      return MappingRelationship.TARGET_INEXACT;
    }
    switch (relationship) {
      case TARGET_BROADER:
        return MappingRelationship.TARGET_NARROWER;
      case TARGET_NARROWER:
        return MappingRelationship.TARGET_BROADER;
      default:
        return relationship;
    }
  }

  /**
   * The rows of the inverted map, one per concept and source code (the first wins: a plain target before a
   * refined one with the same focus, as it is listed first).
   */
  static List<Inverted> invertTargets(List<MapRowTarget> targets) {
    final Map<String, Inverted> byPair = new LinkedHashMap<>();
    targets.stream()
        .filter(t -> t.getTargetCode() != null && !t.getTargetCode().isBlank() && !t.getRow().isNoMap())
        .sorted((a, b) -> Boolean.compare(TargetExpression.looksLikeExpression(a.getTargetCode()),
            TargetExpression.looksLikeExpression(b.getTargetCode())))
        .forEach(t -> {
          final boolean refined = TargetExpression.looksLikeExpression(t.getTargetCode());
          final String concept = TargetExpression.focusOf(t.getTargetCode());
          final MappingRelationship relationship = refined
              ? invert(TargetExportForm.focusRelationship(t.getRelationship()))
              : invert(t.getRelationship());
          final String sourceCode = t.getRow().getSourceCode().getCode();
          byPair.putIfAbsent(concept + "|" + sourceCode,
              new Inverted(concept, sourceCode, t.getRow().getSourceCode().getDisplay(), relationship, refined));
        });
    return new ArrayList<>(byPair.values());
  }

  static String truncate(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max);
  }

  @Transactional
  public InvertMapDto.Result invert(Long mapId, InvertMapDto.Request request) {
    final org.snomed.snap2snomed.model.Map original = mapRepository.findById(mapId)
        .orElseThrow(() -> Snap2SnomedProblem.of(HttpStatus.NOT_FOUND, "No Map found with id " + mapId));
    if (!webSecurity.isAdminUser() && !webSecurity.isProjectOwnerForMapId(mapId)) {
      throw new NotAuthorisedProblem("Only a project owner can create a map from SNOMED CT from this map");
    }
    if (original.isFromSnomed()) {
      throw new BadRequestDataProblem("Only a map to SNOMED CT can be inverted");
    }
    if (original.getProject().getDualMapMode()) {
      throw new BadRequestDataProblem("A dual author map cannot be inverted yet");
    }
    if (request.title() == null || request.title().isBlank() || request.title().length() > 100) {
      throw new BadRequestDataProblem("The new project needs a title of at most 100 characters");
    }

    final List<MapRowTarget> targets = mapRowTargetRepository.findByMapId(mapId);
    final int noMapRows = (int) targets.stream().filter(t -> t.getRow().isNoMap())
        .map(t -> t.getRow().getId()).distinct().count();
    final List<Inverted> inverted = invertTargets(targets).stream()
        .filter(i -> CONCEPT_ID.matcher(i.concept()).matches()).toList();
    final Set<String> concepts = inverted.stream().map(Inverted::concept).collect(Collectors.toCollection(LinkedHashSet::new));
    if (concepts.isEmpty()) {
      throw new BadRequestDataProblem("The map has no SNOMED CT targets to invert");
    }
    if (concepts.size() > EclCodeSetService.MAX_CONCEPTS) {
      throw new BadRequestDataProblem("The map targets " + concepts.size() + " SNOMED CT concepts; a map from SNOMED CT "
          + "accepts at most " + EclCodeSetService.MAX_CONCEPTS);
    }

    // The active concepts, with their terms in the map's display language
    final Map<String, String> active = eclCodeSetService.expandConceptsInline(original.getToVersion(),
        String.join(" OR ", concepts), original.getDisplayLanguage());
    final List<Inverted> kept = inverted.stream().filter(i -> active.containsKey(i.concept())).toList();
    final List<Map.Entry<String, String>> sourceCodes = concepts.stream().filter(active::containsKey)
        .map(code -> Map.entry(code, active.get(code))).collect(Collectors.toList());
    if (sourceCodes.isEmpty()) {
      throw new BadRequestDataProblem("None of the SNOMED CT concepts the map targets is active in its version");
    }

    final User user = authenticationFacade.getAuthenticatedUser();
    final String title = request.title().trim();
    final ImportedCodeSet source = codeSetImportService.saveCodeSet(ImportedCodeSet.builder()
        .name(truncate(title + " (SNOMED CT)", 100))
        .version(EclCodeSetService.effectiveTime(original.getToVersion()))
        .systemUri(EclCodeSetService.SNOMED_SYSTEM)
        .additionalColumnsMetadata(new ArrayList<>())
        .build(), sourceCodes);

    final Project project = projectRepository.save(Project.builder()
        .title(title)
        .description(request.description())
        .dualMapMode(false)
        .owners(new HashSet<>(Set.of(user)))
        .members(new HashSet<>())
        .guests(new HashSet<>())
        .build());

    final org.snomed.snap2snomed.model.Map map = mapRepository.save(org.snomed.snap2snomed.model.Map.builder()
        .project(project)
        .mapVersion("1")
        .toVersion(original.getToVersion())
        .toScope("*")
        .displayLanguage(original.getDisplayLanguage())
        .source(source)
        .direction(MapDirection.FROM_SNOMED)
        .targetCodeSet(original.getSource())
        .inheritance(request.inheritance())
        .inheritanceMode(request.inheritanceMode() == null ? InheritanceMode.WITHIN_SOURCE : request.inheritanceMode())
        .build());
    mapRowRepository.createMapRows(map.getId(), source.getId(), Instant.now(), authenticationFacade.getPrincipalSubject(), false);

    final List<CodeSetImportService.SeedTarget> seeds = kept.stream()
        .map(i -> new CodeSetImportService.SeedTarget(i.concept(), MapRowTarget.builder()
            .targetCode(i.sourceCode()).targetDisplay(i.sourceDisplay()).relationship(i.relationship()).build(),
            MapStatus.DRAFT))
        .toList();
    final int inserted = codeSetImportService.insertTargets(map.getId(), seeds);
    final int refined = (int) kept.stream().filter(Inverted::refined).count();
    log.info("Map {} inverted into map {} of project {}: {} concepts, {} targets ({} from refined targets), {} inactive "
        + "concepts left out", mapId, map.getId(), project.getId(), sourceCodes.size(), inserted, refined,
        concepts.size() - sourceCodes.size());
    return new InvertMapDto.Result(project.getId(), map.getId(), sourceCodes.size(), inserted, refined,
        concepts.size() - sourceCodes.size(), noMapRows);
  }
}
