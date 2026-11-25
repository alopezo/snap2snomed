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

-- Maps from SNOMED CT: the source is either an ECL expansion (source_ecl) or an imported list of SNOMED CT
-- codes (source_ecl null); to_scope becomes the scope inheritance can expand into at export time
alter table map add column source_ecl varchar(1024);
alter table map_aud add column source_ecl varchar(1024);
update map set source_ecl = to_scope where direction = 'FROM_SNOMED';

-- The cached descendants now carry their terms ("code<TAB>term" lines); the cache refills lazily
delete from map_inheritance_descendants;
