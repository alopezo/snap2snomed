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

-- A map is either from imported codes to SNOMED CT (existing maps) or from SNOMED CT to another
-- imported code set (target_code_set_id)
alter table map add column direction varchar(20) not null default 'TO_SNOMED';
alter table map add column target_code_set_id bigint;
alter table map add constraint fk_map_target_code_set foreign key (target_code_set_id) references imported_code_set (id);
alter table map_aud add column direction varchar(20);
alter table map_aud add column target_code_set_id bigint;
