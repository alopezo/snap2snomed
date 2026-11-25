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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.snomed.snap2snomed.model.ImportedCode;
import org.snomed.snap2snomed.model.Map;
import org.snomed.snap2snomed.problem.BadRequestDataProblem;
import org.snomed.snap2snomed.problem.Snap2SnomedProblem;
import org.snomed.snap2snomed.repository.ImportedCodeRepository;
import org.snomed.snap2snomed.repository.InheritanceDescendantsRepository;
import org.snomed.snap2snomed.repository.MapRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The top-level concepts of the source of a map from SNOMED CT: those with no ancestor in the source, for a
 * skim view of the source list. One ECL expansion, {@code (S) MINUS (< (S))} where S is the source ECL or its
 * codes joined by OR (sent inline, as it can be thousands of codes), cached with the map's descendants: the
 * source and the SNOMED CT version of a map never change.
 */
@Service
public class SourceRootsService {

  /** Cache key, in the descendants cache, of a map's top-level source concepts (not a concept id). */
  static final String CACHE_KEY = "!TOP_LEVEL";

  @Autowired
  MapRepository mapRepository;

  @Autowired
  ImportedCodeRepository importedCodeRepository;

  @Autowired
  InheritanceDescendantsRepository descendantsRepository;

  @Autowired
  InheritanceCacheStore cacheStore;

  @Autowired
  EclCodeSetService eclCodeSetService;

  public Set<String> topLevelCodes(Long mapId) {
    final Map map = mapRepository.findById(mapId)
        .orElseThrow(() -> Snap2SnomedProblem.of(HttpStatus.NOT_FOUND, "No Map found with id " + mapId));
    if (!map.isFromSnomed()) {
      throw new BadRequestDataProblem("Top-level source concepts are only available for maps from SNOMED CT");
    }
    final var cached = descendantsRepository.findByMapIdAndConceptCodeIn(mapId, Set.of(CACHE_KEY));
    if (!cached.isEmpty()) {
      return parse(cached.iterator().next().getDescendants());
    }
    final List<String> codes;
    if (map.getSourceEcl() != null) {
      final String source = map.getSourceEcl().trim();
      codes = eclCodeSetService.expandCodes(map.getToVersion(), "(" + source + ") MINUS (< (" + source + "))");
    } else {
      final String source = importedCodeRepository.findAllInCodeSet(map.getSource().getId()).stream()
          .map(ImportedCode::getCode).distinct().collect(Collectors.joining(" OR "));
      codes = source.isEmpty() ? List.of()
          : eclCodeSetService.expandCodesInline(map.getToVersion(), "(" + source + ") MINUS (< (" + source + "))");
    }
    cacheStore.store(mapId, CACHE_KEY, String.join("\n", codes));
    return new LinkedHashSet<>(codes);
  }

  static Set<String> parse(String content) {
    return Arrays.stream(content.split("\n")).map(String::trim).filter(s -> !s.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
