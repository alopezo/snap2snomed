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
import org.snomed.snap2snomed.model.InheritanceDescendants;
import org.snomed.snap2snomed.repository.InheritanceDescendantsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

/** Writes the inheritance descendants cache in its own transaction, so reads can stay read-only. */
@Component
@Slf4j
public class InheritanceCacheStore {

  @Autowired
  InheritanceDescendantsRepository descendantsRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void store(Long mapId, String code, String descendants) {
    try {
      descendantsRepository.saveAndFlush(InheritanceDescendants.builder()
          .mapId(mapId).conceptCode(code).descendants(descendants).created(Instant.now()).build());
    } catch (final DataIntegrityViolationException e) {
      // stored meanwhile by another request: same content, nothing to do
      log.debug("Descendants of {} for map {} already cached", code, mapId);
    }
  }
}
