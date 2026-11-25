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

-- Maps from SNOMED CT can have inheritance: unmapped concepts take the targets of their nearest mapped
-- ancestor, unless a row stops it for its subtree
alter table map add column inheritance boolean not null default false;
alter table map_aud add column inheritance boolean;
alter table map_row add column stop_inheritance boolean not null default false;
alter table map_row_aud add column stop_inheritance boolean;

-- Lazily filled cache of the descendants of a concept within a map's ECL in the map's SNOMED CT version
-- (they never change for a map, whose version is fixed); deleted with the map
create table map_inheritance_descendants (
    id bigint not null auto_increment,
    map_id bigint not null,
    concept_code varchar(18) not null,
    descendants longtext not null,
    created datetime(6),
    primary key (id),
    constraint uk_map_inheritance_concept unique (map_id, concept_code),
    constraint fk_map_inheritance_map foreign key (map_id) references map (id) on delete cascade
) engine=InnoDB;
