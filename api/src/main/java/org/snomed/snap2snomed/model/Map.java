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

package org.snomed.snap2snomed.model;

import java.time.Instant;
import java.util.List;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.*;
import org.hibernate.envers.Audited;
import org.snomed.snap2snomed.model.Project.ListView;
import org.snomed.snap2snomed.util.LanguageDialects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.rest.core.config.Projection;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Audited
@EntityListeners(AuditingEntityListener.class)
@Table(name = "map", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"project_id", "mapVersion"})
})
public class Map implements Snap2SnomedEntity {
  @Column(name = "created", nullable = false, updatable = false)
  @CreatedDate
  private Instant created;

  @Column(name = "modified")
  @LastModifiedDate
  private Instant modified;

  @Column(name = "created_by", updatable = false)
  @CreatedBy
  private String createdBy;

  @Column(name = "modified_by")
  @LastModifiedBy
  private String modifiedBy;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank(message = "Map version is mandatory")
  @Size(min = 1, max = 30, message = "Map version must be between 1 and 30 characters")
  private String mapVersion;

  @Pattern(regexp = "^http://snomed.info/(?<experimental>x?)sct/(?<edition>[0-9]{6,18})/version/(?<effectiveTime>[0-9]{8})",
      message = "Map version must be a SNOMED CT version URI")
  @Size(min = 47, max = 60)
  private String toVersion;

  @NotNull(message = "A Map must always have a target scope")
  @Size(min = 1, max = 1024, message = "A Map's target scope ECL expression must be between 1 and 1024 characters")
  private String toScope;

  /**
   * Ordered sequence of language dialects used to pick the display term of target concepts, in
   * Snowstorm format, e.g. "es-X-450828004,en-X-900000000000509007". Null means the English FSN.
   */
  @Size(max = 512, message = "A Map's display language must be at most 512 characters")
  private String displayLanguage;

  @PrePersist
  @PreUpdate
  void normalizeDisplayLanguage() {
    this.displayLanguage = LanguageDialects.normalize(this.displayLanguage);
  }

  @NotNull(message = "A Map must always belong to a project")
  @ManyToOne(cascade = {CascadeType.MERGE})
  private Project project;

  @ReadOnlyProperty
  @NotNull(message = "A Map should always have a source ImportedCodeSet")
  @ManyToOne
  private ImportedCodeSet source;

  @Projection(name = "listView", types = {Map.class})
  public interface ListViewWithSource {

    long getId();

    String getMapVersion();

    String getToVersion();

    String getToScope();

    String getDisplayLanguage();

    Instant getCreated();

    Instant getModified();

    ImportedCodeSet getSource();

    ListView getProject();

    @Value("#{target.getProject().getOwners()}")
    List<User> getOwners();

    @Value("#{target.getProject().getMembers()}")
    List<User> getMembers();

    @Value("#{target.getProject().getGuests()}")
    List<User> getGuests();

  }

}
