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

-- Inheritance of maps from SNOMED CT reaches every descendant, within the source or beyond it, instead of
-- an inheritance scope ECL. The scope (to_scope) of a map from SNOMED CT now describes its source: the
-- source ECL, or every concept for an imported list of codes, which was also the default scope before.
alter table map add column inheritance_mode varchar(20) not null default 'WITHIN_SOURCE';
alter table map_aud add column inheritance_mode varchar(20);
update map set inheritance_mode = 'BEYOND_SOURCE' where direction = 'FROM_SNOMED' and source_ecl is null;
update map set to_scope = coalesce(source_ecl, '*') where direction = 'FROM_SNOMED';
-- The cached descendants were computed within the old scopes
delete from map_inheritance_descendants;
