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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.snomed.snap2snomed.controller.dto.InheritanceDto;
import org.snomed.snap2snomed.util.EntityUtils;
import org.snomed.snap2snomed.security.WebSecurity;
import org.snomed.snap2snomed.security.AuthenticationFacade;
import org.snomed.snap2snomed.repository.TaskRepository;
import org.snomed.snap2snomed.repository.MapRowRepository;
import org.snomed.snap2snomed.problem.auth.NotAuthorisedProblem;
import org.snomed.snap2snomed.model.enumeration.InheritanceMode;
import org.snomed.snap2snomed.model.User;
import org.snomed.snap2snomed.model.Task;
import org.snomed.snap2snomed.model.MapRow;
import org.snomed.snap2snomed.model.enumeration.MapStatus;
import org.snomed.snap2snomed.model.enumeration.MappingRelationship;
import org.snomed.snap2snomed.problem.BadRequestDataProblem;
import org.snomed.snap2snomed.problem.Snap2SnomedProblem;
import org.snomed.snap2snomed.repository.InheritanceDescendantsRepository;
import org.snomed.snap2snomed.repository.MapRepository;
import org.snomed.snap2snomed.util.InheritanceCalculator;
import org.snomed.snap2snomed.util.InheritanceCalculator.Result;
import org.snomed.snap2snomed.util.InheritanceCalculator.Row;
import org.snomed.snap2snomed.util.InheritanceCalculator.State;
import org.snomed.snap2snomed.util.InheritanceCalculator.Target;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes inheritance for a map from SNOMED CT (see {@link InheritanceCalculator}) from its rows and
 * targets: for the rows of the map, and for the concepts generated at export time (descendants of mapped
 * or stopping concepts that are not rows, when the inheritance reaches beyond the source).
 *
 * <p>Inheritance always applies to every descendant; the map's {@link InheritanceMode} says whether only
 * the source's concepts count. The descendants of each mapped or stopping concept, with their terms in the
 * map's display language, come from a lazily filled cache table: a concept missing from it costs one ECL
 * expansion, {@code < concept}, or {@code (< concept) AND (source ECL)} within a source from an ECL. A
 * concept with more descendants than the limit is cached as too large, and inheritance is then refused for
 * it until it is mapped differently.
 */
@Service
public class InheritanceService {

  /** Cached content of a concept whose descendants are over the limit: the marker, then the total. */
  static final String TOO_LARGE = "!TOO_LARGE ";

  @PersistenceContext
  EntityManager entityManager;

  @Autowired
  MapRepository mapRepository;

  @Autowired
  InheritanceDescendantsRepository descendantsRepository;

  @Autowired
  EclCodeSetService eclCodeSetService;

  @Autowired
  InheritanceCacheStore cacheStore;

  @Autowired
  MapRowRepository mapRowRepository;

  @Autowired
  TaskRepository taskRepository;

  @Autowired
  AuthenticationFacade authenticationFacade;

  @Autowired
  WebSecurity webSecurity;

  @Value("${snap2snomed.inheritance.max-generated:50000}")
  int maxGenerated;

  /** Rows of a map with their own targets, in source order. */
  record LoadedRow(Long rowId, String display, Row row, MapStatus status, String acceptedInheritance) {
  }

  /** Descendants of the anchors (code to term), and the anchors over the limit (code to total). */
  record Descendants(Map<String, Map<String, String>> byAnchor, Map<String, Integer> tooLarge) {
  }

  /** Everything computed for a map, shared by the coverage, the preview and the export. */
  public record Computation(List<LoadedRow> loaded, List<Result> rows, List<Result> generated,
      Map<String, String> terms, Map<String, Integer> tooLarge, boolean generatedTooLarge) {

    /** Generation is possible: no anchor and not the total over the limit. */
    public boolean canGenerate() {
      return tooLarge.isEmpty() && !generatedTooLarge;
    }
  }

  @Transactional(readOnly = true)
  public InheritanceDto computeForMap(Long mapId) {
    return toDto(compute(mapId), maxGenerated);
  }

  /** What an export needs: the coverage of the rows and every generated concept. */
  public record ExportData(InheritanceDto inheritance, List<InheritanceDto.RowDto> generated, boolean canGenerate) {
  }

  @Transactional(readOnly = true)
  public ExportData exportData(Long mapId) {
    final Computation computation = compute(mapId);
    return new ExportData(toDto(computation, maxGenerated),
        computation.generated().stream().map(r -> toRowDto(null, r, computation.terms(), Map.of(), false)).toList(),
        computation.canGenerate());
  }

  /** A page of the generated concepts, optionally of one state. */
  @Transactional(readOnly = true)
  public InheritanceDto.GeneratedPage generatedPage(Long mapId, String state, int page, int size) {
    final Computation computation = compute(mapId);
    final List<Result> filtered = computation.generated().stream()
        .filter(r -> state == null || state.isBlank() || r.state().name().equals(state))
        .toList();
    final int from = Math.min(Math.max(page, 0) * size, filtered.size());
    final int to = Math.min(from + size, filtered.size());
    return new InheritanceDto.GeneratedPage(filtered.size(),
        filtered.subList(from, to).stream().map(r -> toRowDto(null, r, computation.terms(), Map.of(), false)).toList());
  }

  /**
   * Accepts the inheritance of rows without targets (inheriting, blocked or stopped): they become MAPPED and
   * record what they inherit. With a task, only its author's rows in an author state; without one, only a
   * project owner. Rows that cannot be accepted are counted by reason.
   */
  @Transactional
  public InheritanceDto.AcceptResult accept(Long mapId, InheritanceDto.AcceptRequest request) {
    final User user = authenticationFacade.getAuthenticatedUser();
    final Task task = request.getTaskId() == null ? null : taskRepository.findById(request.getTaskId())
        .orElseThrow(() -> Snap2SnomedProblem.of(HttpStatus.NOT_FOUND, "No task found with id " + request.getTaskId()));
    if (task != null) {
      if (!task.getMap().getId().equals(mapId) || !EntityUtils.isTaskAssignee(user, task)) {
        throw new NotAuthorisedProblem("Inheritance can only be accepted in an author task of this map assigned to the user");
      }
    } else if (!webSecurity.isAdminUser() && !webSecurity.isProjectOwnerForMapId(mapId)) {
      throw new NotAuthorisedProblem("Only a project owner can accept inheritance outside of a task");
    }
    final Computation computation = compute(mapId);
    final Set<Long> wanted = request.getRowIds() == null || request.getRowIds().isEmpty()
        ? null : new java.util.HashSet<>(request.getRowIds());
    final Map<String, Integer> skipped = new LinkedHashMap<>();
    int accepted = 0;
    for (int i = 0; i < computation.rows().size(); i++) {
      final LoadedRow loaded = computation.loaded().get(i);
      final Result result = computation.rows().get(i);
      if (wanted != null && !wanted.contains(loaded.rowId())) {
        continue;
      }
      if (!ACCEPTABLE.contains(result.state())) {
        skipped.merge(result.state().name(), 1, Integer::sum);
        continue;
      }
      final MapRow row = mapRowRepository.findById(loaded.rowId()).orElseThrow();
      if (task != null && (row.getAuthorTask() == null || !task.getId().equals(row.getAuthorTask().getId()))) {
        skipped.merge("NOT_IN_TASK", 1, Integer::sum);
        continue;
      }
      if (!row.getStatus().isAuthorState() && row.getStatus() != MapStatus.REJECTED) {
        skipped.merge("NOT_EDITABLE", 1, Integer::sum);
        continue;
      }
      row.setAcceptedInheritance(fingerprint(result));
      row.setStatus(MapStatus.MAPPED);
      row.setLastAuthor(user);
      mapRowRepository.save(row);
      accepted++;
    }
    return new InheritanceDto.AcceptResult(accepted, skipped);
  }

  @Transactional(readOnly = true)
  public Computation compute(Long mapId) {
    final org.snomed.snap2snomed.model.Map map = mapRepository.findById(mapId)
        .orElseThrow(() -> Snap2SnomedProblem.of(HttpStatus.NOT_FOUND, "No Map found with id " + mapId));
    if (!map.isFromSnomed() || !map.isInheritance()) {
      throw new BadRequestDataProblem("Inheritance is only computed for maps from SNOMED CT that have it switched on");
    }
    final List<LoadedRow> loaded = loadRows(mapId);
    final List<Row> rows = loaded.stream().map(LoadedRow::row).toList();
    final Descendants descendants = descendants(map, InheritanceCalculator.anchorCodes(rows));
    final Map<String, Set<String>> codes = new HashMap<>();
    final Map<String, String> terms = new HashMap<>();
    loaded.forEach(l -> terms.put(l.row().code(), l.display()));
    final Set<String> rowCodes = rows.stream().map(Row::code).collect(Collectors.toSet());
    // Within the source only its concepts (the rows) inherit, so nothing is generated
    final boolean withinSource = map.getInheritanceMode() != InheritanceMode.BEYOND_SOURCE;
    descendants.byAnchor().forEach((anchor, concepts) -> {
      if (withinSource) {
        codes.put(anchor, concepts.keySet().stream().filter(rowCodes::contains).collect(Collectors.toSet()));
      } else {
        codes.put(anchor, concepts.keySet());
        concepts.forEach(terms::putIfAbsent);
      }
    });
    final List<Result> results = InheritanceCalculator.compute(rows, code -> codes.getOrDefault(code, Set.of()));
    final long generatedCount = codes.values().stream().flatMap(Set::stream).filter(c -> !rowCodes.contains(c)).distinct().count();
    final boolean generatedTooLarge = generatedCount > maxGenerated;
    final List<Result> generated = descendants.tooLarge().isEmpty() && !generatedTooLarge
        ? InheritanceCalculator.computeGenerated(rows, code -> codes.getOrDefault(code, Set.of()))
        : List.of();
    return new Computation(loaded, results, generated, terms, descendants.tooLarge(), generatedTooLarge);
  }

  /**
   * The descendants of each concept, from the cache or expanded and stored: within the source ECL when the
   * inheritance stays within a source from an ECL (they can't be more than the source), all of them otherwise.
   */
  Descendants descendants(org.snomed.snap2snomed.model.Map map, Set<String> anchors) {
    final Map<String, Map<String, String>> byAnchor = new HashMap<>();
    final Map<String, Integer> tooLarge = new LinkedHashMap<>();
    if (anchors.isEmpty()) {
      return new Descendants(byAnchor, tooLarge);
    }
    descendantsRepository.findByMapIdAndConceptCodeIn(map.getId(), anchors).forEach(cached -> {
      if (cached.getDescendants().startsWith(TOO_LARGE)) {
        tooLarge.put(cached.getConceptCode(), Integer.parseInt(cached.getDescendants().substring(TOO_LARGE.length()).trim()));
      } else {
        byAnchor.put(cached.getConceptCode(), parse(cached.getDescendants()));
      }
    });
    for (final String code : anchors) {
      if (byAnchor.containsKey(code) || tooLarge.containsKey(code)) {
        continue;
      }
      final String ecl = map.getInheritanceMode() != InheritanceMode.BEYOND_SOURCE && map.getSourceEcl() != null
          ? "(< " + code + ") AND (" + map.getSourceEcl() + ")"
          : "< " + code;
      final EclCodeSetService.Expansion expansion = eclCodeSetService.expandConcepts(map.getToVersion(),
          ecl, map.getDisplayLanguage(), maxGenerated);
      if (expansion.tooLarge()) {
        tooLarge.put(code, expansion.total());
        cacheStore.store(map.getId(), code, TOO_LARGE + expansion.total());
      } else {
        byAnchor.put(code, expansion.concepts());
        cacheStore.store(map.getId(), code, format(expansion.concepts()));
      }
    }
    return new Descendants(byAnchor, tooLarge);
  }

  /** States whose inheritance an author can accept (the row then needs no targets of its own). */
  public static final Set<State> ACCEPTABLE = Set.of(State.INHERITED, State.BLOCKED, State.STOPPED);

  /**
   * What a row inherits, recorded when its inheritance is accepted: the ancestor and target codes, the
   * blocking row, or the stop. Compared later to warn when the inheritance changed since.
   */
  public static String fingerprint(Result result) {
    switch (result.state()) {
      case INHERITED:
        return "INHERITED " + result.from() + " " + result.targets().stream().map(Target::code).sorted()
            .collect(Collectors.joining(","));
      case BLOCKED:
        return "BLOCKED " + result.from();
      case STOPPED:
        return "STOPPED";
      default:
        return null;
    }
  }

  /** An accepted row whose inheritance is no longer what was accepted. */
  static boolean inheritanceChanged(LoadedRow row, Result result) {
    return row.acceptedInheritance() != null && row.row().targets().isEmpty()
        && !row.acceptedInheritance().equals(fingerprint(result));
  }

  static String format(Map<String, String> concepts) {
    return concepts.entrySet().stream()
        .map(e -> e.getKey() + "\t" + (e.getValue() == null ? "" : e.getValue().replace('\n', ' ').replace('\t', ' ')))
        .collect(Collectors.joining("\n"));
  }

  static Map<String, String> parse(String cached) {
    final Map<String, String> concepts = new LinkedHashMap<>();
    if (cached != null && !cached.isBlank()) {
      for (final String line : cached.split("\n")) {
        final int tab = line.indexOf('\t');
        if (tab < 0) {
          concepts.put(line.trim(), null);
        } else {
          concepts.put(line.substring(0, tab), line.substring(tab + 1));
        }
      }
    }
    return concepts;
  }

  List<LoadedRow> loadRows(Long mapId) {
    final List<Object[]> rowData = entityManager.createQuery(
            "select r.id, c.code, c.display, r.noMap, r.stopInheritance, r.status, r.acceptedInheritance from MapRow r join r.sourceCode c "
                + "where r.map.id = :mapId order by c.index", Object[].class)
        .setParameter("mapId", mapId).getResultList();
    final Map<Long, List<Target>> targets = new HashMap<>();
    entityManager.createQuery(
            "select t.row.id, t.targetCode, t.targetDisplay, t.relationship from MapRowTarget t "
                + "where t.row.map.id = :mapId and t.targetCode is not null", Object[].class)
        .setParameter("mapId", mapId).getResultList()
        .forEach(t -> targets.computeIfAbsent((Long) t[0], k -> new ArrayList<>())
            .add(new Target((String) t[1], (String) t[2], (MappingRelationship) t[3])));
    return rowData.stream().map(r -> new LoadedRow((Long) r[0], (String) r[2],
            new Row((String) r[1], (Boolean) r[3], (Boolean) r[4], targets.getOrDefault((Long) r[0], List.of())),
            (MapStatus) r[5], (String) r[6]))
        .toList();
  }

  static InheritanceDto toDto(Computation computation, int maxGenerated) {
    final Map<String, Integer> inheritedBy = new HashMap<>();
    computation.rows().stream().filter(r -> r.state() == State.INHERITED)
        .forEach(r -> inheritedBy.merge(r.from(), 1, Integer::sum));
    final Map<String, Integer> generatedBy = new HashMap<>();
    computation.generated().stream().filter(r -> r.state() == State.INHERITED)
        .forEach(r -> generatedBy.merge(r.from(), 1, Integer::sum));
    final Map<String, Integer> counts = new LinkedHashMap<>();
    for (final State state : State.values()) {
      counts.put(state.name(), 0);
    }
    final List<InheritanceDto.RowDto> rows = new ArrayList<>(computation.rows().size());
    int acceptedCount = 0;
    int changedCount = 0;
    for (int i = 0; i < computation.rows().size(); i++) {
      final Result result = computation.rows().get(i);
      final LoadedRow row = computation.loaded().get(i);
      counts.merge(result.state().name(), 1, Integer::sum);
      final InheritanceDto.RowDto dto = toRowDto(row.rowId(), result, computation.terms(), inheritedBy, row.row().stopInheritance());
      dto.setGeneratedBy(generatedBy.getOrDefault(result.code(), 0));
      dto.setStatus(row.status() == null ? null : row.status().name());
      dto.setAccepted(row.acceptedInheritance() != null && row.row().targets().isEmpty());
      dto.setInheritanceChanged(inheritanceChanged(row, result));
      if (dto.isAccepted()) {
        acceptedCount++;
      }
      if (dto.isInheritanceChanged()) {
        changedCount++;
      }
      rows.add(dto);
    }
    final Map<String, Integer> generatedCounts = new LinkedHashMap<>();
    computation.generated().forEach(r -> generatedCounts.merge(r.state().name(), 1, Integer::sum));
    final List<InheritanceDto.TooLarge> tooLarge = computation.tooLarge().entrySet().stream()
        .map(e -> new InheritanceDto.TooLarge(e.getKey(), computation.terms().get(e.getKey()), e.getValue()))
        .toList();
    final InheritanceDto dto = new InheritanceDto(counts, rows);
    dto.setGeneratedCounts(generatedCounts);
    dto.setGeneratedTotal(computation.generated().size());
    dto.setTooLarge(tooLarge);
    dto.setGeneratedTooLarge(computation.generatedTooLarge());
    dto.setMaxGenerated(maxGenerated);
    dto.setAcceptedCount(acceptedCount);
    dto.setChangedCount(changedCount);
    return dto;
  }

  static InheritanceDto.RowDto toRowDto(Long rowId, Result result, Map<String, String> terms,
      Map<String, Integer> inheritedBy, boolean stopInheritance) {
    return new InheritanceDto.RowDto(rowId, result.code(), terms.get(result.code()), result.state().name(),
        stopInheritance, result.from(), result.from() == null ? null : terms.get(result.from()),
        result.targets().stream().map(t -> new InheritanceDto.TargetDto(t.code(), t.display(),
            t.relationship() == null ? null : t.relationship().name())).toList(),
        result.candidates(), inheritedBy.getOrDefault(result.code(), 0), 0, null, false, false);
  }
}
