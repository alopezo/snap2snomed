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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.snomed.snap2snomed.controller.dto.AutomapResultDto;
import org.snomed.snap2snomed.model.MapRow;
import org.snomed.snap2snomed.model.MapRowTarget;
import org.snomed.snap2snomed.model.Task;
import org.snomed.snap2snomed.model.User;
import org.snomed.snap2snomed.model.enumeration.MapStatus;
import org.snomed.snap2snomed.model.enumeration.MappingRelationship;
import org.snomed.snap2snomed.model.enumeration.TaskType;
import org.snomed.snap2snomed.problem.BadRequestDataProblem;
import org.snomed.snap2snomed.problem.Snap2SnomedProblem;
import org.snomed.snap2snomed.problem.auth.NotAuthorisedProblem;
import org.snomed.snap2snomed.repository.ImportedCodeRepository;
import org.snomed.snap2snomed.repository.MapRowRepository;
import org.snomed.snap2snomed.repository.MapRowTargetRepository;
import org.snomed.snap2snomed.repository.TaskRepository;
import org.snomed.snap2snomed.security.AuthenticationFacade;
import org.snomed.snap2snomed.security.WebSecurity;
import org.snomed.snap2snomed.util.InheritanceCalculator;
import org.snomed.snap2snomed.util.InheritanceCalculator.Result;
import org.snomed.snap2snomed.util.InheritanceCalculator.Row;
import org.snomed.snap2snomed.util.InheritanceCalculator.Target;
import org.snomed.snap2snomed.util.TermMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Automap for the author tasks of maps from SNOMED CT: the term of each unmapped concept is matched against
 * the displays of the target code set (see {@link TermMatcher}), and a match over the threshold becomes an
 * INEXACT target, its row DRAFT, as automap does for maps to SNOMED CT.
 *
 * <p>In a map with inheritance a match is left out when the row already inherits exactly that code, taking
 * into account the other matches of the same run: a target of its own would add nothing and would stop the
 * row passing on its ancestor's mapping. Rows marked "stop inheritance" are left alone.
 */
@Service
@Slf4j
public class FromSnomedAutomapService {

  @Autowired
  TaskRepository taskRepository;

  @Autowired
  MapRowRepository mapRowRepository;

  @Autowired
  MapRowTargetRepository mapRowTargetRepository;

  @Autowired
  ImportedCodeRepository importedCodeRepository;

  @Autowired
  InheritanceService inheritanceService;

  @Autowired
  AuthenticationFacade authenticationFacade;

  @Autowired
  WebSecurity webSecurity;

  @Transactional
  public AutomapResultDto automap(Long taskId) {
    final Task task = taskRepository.findById(taskId)
        .orElseThrow(() -> Snap2SnomedProblem.of(HttpStatus.NOT_FOUND, "No Task found with id " + taskId));
    if (!webSecurity.isAdminUser() && !webSecurity.isTaskAssignee(taskId)) {
      throw new NotAuthorisedProblem("Not authorised to automap, user is not admin or assignee for the task!");
    }
    final org.snomed.snap2snomed.model.Map map = task.getMap();
    if (!map.isFromSnomed() || map.getTargetCodeSet() == null) {
      throw new BadRequestDataProblem("This automap is for maps from SNOMED CT");
    }
    if (task.getType() != TaskType.AUTHOR) {
      throw new BadRequestDataProblem("Only an author task can be automapped");
    }

    final List<MapRow> rows = mapRowRepository.findUnmappedAuthorTaskMapRows(taskId).stream()
        .filter(r -> !r.isNoMap() && !r.isStopInheritance())
        .toList();
    final TermMatcher matcher = new TermMatcher(importedCodeRepository.findAllInCodeSet(map.getTargetCodeSet().getId())
        .stream().map(c -> new TermMatcher.Candidate(c.getCode(), c.getDisplay())).toList());
    // matches by source concept code (a concept is one row of a single author map)
    final Map<String, TermMatcher.Match> matches = new LinkedHashMap<>();
    final Map<String, MapRow> rowByCode = new HashMap<>();
    for (final MapRow row : rows) {
      final String code = row.getSourceCode().getCode();
      matcher.best(row.getSourceCode().getDisplay(), TermMatcher.DEFAULT_THRESHOLD).ifPresent(m -> {
        matches.put(code, m);
        rowByCode.put(code, row);
      });
    }

    final Set<String> alreadyInherited = map.isInheritance() && !map.getProject().getDualMapMode()
        ? alreadyInherited(map, matches) : Set.of();

    final User user = authenticationFacade.getAuthenticatedUser();
    int created = 0;
    for (final Map.Entry<String, TermMatcher.Match> entry : matches.entrySet()) {
      if (alreadyInherited.contains(entry.getKey())) {
        continue;
      }
      final MapRow row = rowByCode.get(entry.getKey());
      mapRowTargetRepository.save(MapRowTarget.builder()
          .row(row)
          .targetCode(entry.getValue().code())
          .targetDisplay(entry.getValue().display())
          .relationship(MappingRelationship.TARGET_INEXACT)
          .lastAuthor(user)
          .build());
      row.setStatus(MapStatus.DRAFT);
      row.setLastAuthor(user);
      mapRowRepository.save(row);
      created++;
    }
    log.info("Automap of task {} (map {} from SNOMED CT): {} rows, {} matched, {} created, {} already inherited",
        taskId, map.getId(), rows.size(), matches.size(), created, alreadyInherited.size());
    return new AutomapResultDto(rows.size(), matches.size(), created, alreadyInherited.size());
  }

  /**
   * The matched rows that, without a target of their own, would inherit exactly the matched code from the
   * map's rows with the other matches applied (skipping one of them leaves what it passes on unchanged, as
   * it inherits that same code).
   */
  private Set<String> alreadyInherited(org.snomed.snap2snomed.model.Map map, Map<String, TermMatcher.Match> matchByCode) {
    final List<Row> rows = new ArrayList<>();
    for (final InheritanceService.LoadedRow loaded : inheritanceService.loadRows(map.getId())) {
      final Row row = loaded.row();
      final TermMatcher.Match match = matchByCode.get(row.code());
      rows.add(match == null ? row : new Row(row.code(), row.noMap(), row.stopInheritance(),
          List.of(new Target(match.code(), match.display(), MappingRelationship.TARGET_INEXACT))));
    }
    // descendants of the anchors (over the limit: unknown, so nothing is left out because of them)
    final InheritanceService.Descendants descendants = inheritanceService.descendants(map, InheritanceCalculator.anchorCodes(rows));
    final Map<String, Result> inherited = InheritanceCalculator.inheritedWithoutOwnTargets(rows, matchByCode.keySet(),
        code -> Optional.ofNullable(descendants.byAnchor().get(code)).map(Map::keySet).orElse(Set.of()));
    return inherited.entrySet().stream()
        .filter(e -> e.getValue().state() == InheritanceCalculator.State.INHERITED
            && e.getValue().targets().stream().map(Target::code).collect(Collectors.toSet())
                .equals(Set.of(matchByCode.get(e.getKey()).code())))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }
}
