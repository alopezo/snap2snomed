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

package org.snomed.snap2snomed.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cached descendants of a concept within a map's ECL, in the map's SNOMED CT version: a comma separated
 * list of concept ids. Filled the first time inheritance needs them; never stale, since a map's version
 * and ECL do not change. Deleted with the map (foreign key cascade). A row with a key that is not a concept
 * id ("!TOP_LEVEL") holds the top-level concepts of the map's source instead (see SourceRootsService).
 */
@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "map_inheritance_descendants")
public class InheritanceDescendants {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "map_id", nullable = false)
  private Long mapId;

  @Column(name = "concept_code", nullable = false, length = 18)
  private String conceptCode;

  @Lob
  @Column(nullable = false, columnDefinition = "longtext")
  private String descendants;

  private Instant created;
}
