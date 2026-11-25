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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes the inheritance descendants cache in its own transaction, so reads can stay read-only. A row stored
 * meanwhile by a concurrent request is ignored: the failed insert is caught outside its transaction, which
 * is rolled back alone (caught inside, the transaction would only fail on commit).
 */
@Component
@Slf4j
public class InheritanceCacheStore {

  @Autowired
  InheritanceDescendantsRepository descendantsRepository;

  @Autowired
  PlatformTransactionManager transactionManager;

  public void store(Long mapId, String code, String descendants) {
    final TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    try {
      transaction.executeWithoutResult(status -> descendantsRepository.saveAndFlush(InheritanceDescendants.builder()
          .mapId(mapId).conceptCode(code).descendants(descendants).created(Instant.now()).build()));
    } catch (final DataIntegrityViolationException e) {
      // stored meanwhile by another request: same content, nothing to do
      log.debug("Descendants of {} for map {} already cached", code, mapId);
    }
  }
}
