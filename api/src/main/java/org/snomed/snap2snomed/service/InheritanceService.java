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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.snomed.snap2snomed.controller.dto.InheritanceDto;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes inheritance for a map from SNOMED CT (see {@link InheritanceCalculator}) from its rows and
 * targets. The descendants of each mapped or stopping concept come from a lazily filled cache table; a
 * concept missing from it costs one ECL expansion, {@code (< concept) AND (map ECL)}, in the map's version.
 */
@Service
public class InheritanceService {

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

  /** Rows of a map with their own targets, in source order. */
  record LoadedRow(Long rowId, String display, Row row) {
  }

  @Transactional(readOnly = true)
  public InheritanceDto computeForMap(Long mapId) {
    final org.snomed.snap2snomed.model.Map map = mapRepository.findById(mapId)
        .orElseThrow(() -> Snap2SnomedProblem.of(HttpStatus.NOT_FOUND, "No Map found with id " + mapId));
    if (!map.isFromSnomed() || !map.isInheritance()) {
      throw new BadRequestDataProblem("Inheritance is only computed for maps from SNOMED CT that have it switched on");
    }
    final List<LoadedRow> loaded = loadRows(mapId);
    final List<Row> rows = loaded.stream().map(LoadedRow::row).toList();
    final Map<String, Set<String>> descendants = descendants(map, InheritanceCalculator.anchorCodes(rows));
    final List<Result> results = InheritanceCalculator.compute(rows, code -> descendants.getOrDefault(code, Set.of()));
    return toDto(loaded, results);
  }

  /** The proper descendants of each concept within the map's ECL, from the cache or expanded and stored. */
  Map<String, Set<String>> descendants(org.snomed.snap2snomed.model.Map map, Set<String> codes) {
    final Map<String, Set<String>> result = new HashMap<>();
    if (codes.isEmpty()) {
      return result;
    }
    descendantsRepository.findByMapIdAndConceptCodeIn(map.getId(), codes)
        .forEach(cached -> result.put(cached.getConceptCode(), split(cached.getDescendants())));
    for (final String code : codes) {
      if (!result.containsKey(code)) {
        final List<String> expanded = eclCodeSetService.expandCodes(map.getToVersion(),
            "(< " + code + ") AND (" + map.getToScope() + ")");
        result.put(code, new HashSet<>(expanded));
        cacheStore.store(map.getId(), code, String.join(",", expanded));
      }
    }
    return result;
  }

  private static Set<String> split(String codes) {
    return codes == null || codes.isBlank() ? new HashSet<>() : new HashSet<>(Arrays.asList(codes.split(",")));
  }

  private List<LoadedRow> loadRows(Long mapId) {
    final List<Object[]> rowData = entityManager.createQuery(
            "select r.id, c.code, c.display, r.noMap, r.stopInheritance from MapRow r join r.sourceCode c "
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
            new Row((String) r[1], (Boolean) r[3], (Boolean) r[4], targets.getOrDefault((Long) r[0], List.of()))))
        .toList();
  }

  static InheritanceDto toDto(List<LoadedRow> loaded, List<Result> results) {
    final Map<String, String> displays = loaded.stream()
        .collect(Collectors.toMap(l -> l.row().code(), LoadedRow::display, (a, b) -> a));
    final Map<String, Integer> inheritedBy = new HashMap<>();
    results.stream().filter(r -> r.state() == State.INHERITED)
        .forEach(r -> inheritedBy.merge(r.from(), 1, Integer::sum));
    final Map<String, Integer> counts = new LinkedHashMap<>();
    for (final State state : State.values()) {
      counts.put(state.name(), 0);
    }
    final List<InheritanceDto.RowDto> rows = new ArrayList<>(results.size());
    for (int i = 0; i < results.size(); i++) {
      final Result result = results.get(i);
      final LoadedRow row = loaded.get(i);
      counts.merge(result.state().name(), 1, Integer::sum);
      rows.add(new InheritanceDto.RowDto(row.rowId(), result.code(), row.display(), result.state().name(),
          row.row().stopInheritance(), result.from(), result.from() == null ? null : displays.get(result.from()),
          result.targets().stream().map(t -> new InheritanceDto.TargetDto(t.code(), t.display(),
              t.relationship() == null ? null : t.relationship().name())).toList(),
          result.candidates(), inheritedBy.getOrDefault(result.code(), 0)));
    }
    return new InheritanceDto(counts, rows);
  }
}
