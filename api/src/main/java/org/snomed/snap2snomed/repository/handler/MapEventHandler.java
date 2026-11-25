/*
 * Copyright © 2022 SNOMED International
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

package org.snomed.snap2snomed.repository.handler;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.snomed.snap2snomed.controller.dto.ValidationResult;
import org.snomed.snap2snomed.model.ImportedCode;
import org.snomed.snap2snomed.model.Map;
import org.snomed.snap2snomed.model.enumeration.MapDirection;
import org.snomed.snap2snomed.problem.BadRequestDataProblem;
import org.snomed.snap2snomed.problem.auth.NotAuthorisedProblem;
import org.snomed.snap2snomed.repository.ImportedCodeRepository;
import org.snomed.snap2snomed.repository.ImportedCodeSetRepository;
import org.snomed.snap2snomed.repository.MapRowRepository;
import org.snomed.snap2snomed.repository.ProjectRepository;
import org.snomed.snap2snomed.security.AuthenticationFacade;
import org.snomed.snap2snomed.security.WebSecurity;
import org.snomed.snap2snomed.service.EclCodeSetService;
import org.snomed.snap2snomed.service.FhirService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.core.annotation.HandleAfterCreate;
import org.springframework.data.rest.core.annotation.HandleBeforeCreate;
import org.springframework.data.rest.core.annotation.HandleBeforeLinkDelete;
import org.springframework.data.rest.core.annotation.HandleBeforeLinkSave;
import org.springframework.data.rest.core.annotation.HandleBeforeSave;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;

@RepositoryEventHandler
@Slf4j
public class MapEventHandler {

  @Autowired
  MapRowRepository mapRowRepository;

  @Autowired
  ImportedCodeRepository importedCodeRepository;

  @Autowired
  FhirService fhirService;

  @Autowired
  ImportedCodeSetRepository importedCodeSetRepository;

  @Autowired
  ProjectRepository projectRepository;

  @Autowired
  AuthenticationFacade authenticationFacade;

  @Autowired
  WebSecurity webSecurity;

  @HandleBeforeCreate
  public void handleMapBeforeCreate(Map map) {
    if (map.getProject() == null || map.getProject().getId() == null) {
      throw new BadRequestDataProblem("No project specified to create a map");
    }

    if (map.getSource() == null || map.getSource().getId() == null) {
      throw new BadRequestDataProblem("No code set specified to create a map");
    }

    if (!webSecurity.isProjectOwnerForId(map.getProject().getId()) && !webSecurity.isAdminUser()) {
      throw new NotAuthorisedProblem("Only a project owner can create a map under a project");
    }

    if (!webSecurity.isAuthorisedForImportedCodeSet(map.getSource().getId()) && !webSecurity.isAdminUser()) {
      throw new NotAuthorisedProblem("User is not authorised to create a map using this code set, "
          + "the user must have uploaded the code set or be a member of a project that uses this code set");
    }

    if (map.getDirection() == null) {
      map.setDirection(MapDirection.TO_SNOMED);
    }
    if (map.isFromSnomed()) {
      if (map.getTargetCodeSet() == null || map.getTargetCodeSet().getId() == null) {
        throw new BadRequestDataProblem("No target code set specified to create a map from SNOMED CT");
      }
      if (!webSecurity.isAuthorisedForImportedCodeSet(map.getTargetCodeSet().getId()) && !webSecurity.isAdminUser()) {
        throw new NotAuthorisedProblem("User is not authorised to create a map using this target code set, "
            + "the user must have uploaded the code set or be a member of a project that uses this code set");
      }
    } else if (map.getTargetCodeSet() != null) {
      throw new BadRequestDataProblem("A target code set can only be specified for a map from SNOMED CT");
    }
    if (map.isFromSnomed() && map.getSourceEcl() == null) {
      validateSnomedSourceCodes(map);
    }
    if (map.isFromSnomed()) {
      // The scope of a map from SNOMED CT describes its source: the ECL, or every concept for a list of codes
      map.setToScope(map.getSourceEcl() != null ? map.getSourceEcl() : "*");
    }
    if (map.isInheritance() && (!map.isFromSnomed() || map.getProject().getDualMapMode())) {
      throw new BadRequestDataProblem("Inheritance is only available for single author maps from SNOMED CT");
    }

  }

  /**
   * A map from SNOMED CT whose source is an imported list of codes: every code must be an active concept in
   * the map's SNOMED CT version, and the list at most {@link EclCodeSetService#MAX_CONCEPTS} long.
   */
  private void validateSnomedSourceCodes(Map map) {
    final List<ImportedCode> codes = importedCodeRepository.findAllInCodeSet(map.getSource().getId());
    if (codes.isEmpty() || codes.size() > EclCodeSetService.MAX_CONCEPTS) {
      throw new BadRequestDataProblem("The source of a map from SNOMED CT must have between 1 and "
          + EclCodeSetService.MAX_CONCEPTS + " codes; this one has " + codes.size());
    }
    final ValidationResult result;
    try {
      result = fhirService.validateValueSetComposition(
          codes.stream().map(ImportedCode::getCode).collect(Collectors.toCollection(HashSet::new)), map.getToVersion(), "*");
    } catch (final IOException e) {
      throw new BadRequestDataProblem("The source codes could not be checked against the terminology server");
    }
    final List<String> problems = new ArrayList<>();
    result.getInvalid().forEach(code -> problems.add(code + " (not a concept id)"));
    result.getAbsent().forEach(code -> problems.add(code + " (not in this SNOMED CT version)"));
    result.getInactive().forEach(code -> problems.add(code + " (inactive)"));
    if (!problems.isEmpty()) {
      throw new BadRequestDataProblem("The source must contain active SNOMED CT concepts of " + map.getToVersion()
          + "; " + problems.size() + " codes are not: " + String.join(", ", problems.subList(0, Math.min(20, problems.size())))
          + (problems.size() > 20 ? ", ..." : ""));
    }
  }

  @HandleBeforeSave
  public void handleBeforeSave(Map map) {
    if (map.getProject() == null || map.getProject().getId() == null) {
      throw new BadRequestDataProblem("No project specified to create a map");
    }

    if (!webSecurity.isProjectOwnerForId(map.getProject().getId())) {
      throw new NotAuthorisedProblem("Only a project owner can modify a map");
    }
  }

  @HandleAfterCreate
  public void handleMapAfterCreate(Map map) {

    if (map.getProject().getDualMapMode()) {
      mapRowRepository.createMapRows(map.getId(), map.getSource().getId(), Instant.now(), authenticationFacade.getPrincipalSubject(), true); 
      mapRowRepository.createMapRowsDualMap(map.getId());
    }
    else {
      mapRowRepository.createMapRows(map.getId(), map.getSource().getId(), Instant.now(), authenticationFacade.getPrincipalSubject(), false); 
    }

  }


  @HandleBeforeLinkSave
  public void handleBeforeLinkSave(Map map) {
    throw new NotAuthorisedProblem("Note links cannot be modified");
  }

  @HandleBeforeLinkDelete
  public void handleBeforeLinkDelete(Map map) {
    throw new NotAuthorisedProblem("Note links cannot be modified");
  }
}
