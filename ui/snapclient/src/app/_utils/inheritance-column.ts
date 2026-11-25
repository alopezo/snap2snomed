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

import {TableColumn} from '../mapping/mapping-table/mapping-table.component';

export const INHERITANCE_COLUMN = 'inheritance';

/**
 * Adds the inheritance column after the target display (or removes it) in a table's column lists, in place.
 * @return true when the lists changed
 */
export function applyInheritanceColumn(on: boolean, columns: TableColumn[], filtered: string[], hideShow: string[]): boolean {
  const index = columns.findIndex(c => c.columnId === INHERITANCE_COLUMN);
  if (on === index >= 0) {
    return false;
  }
  if (on) {
    columns.splice(columns.findIndex(c => c.columnId === 'targetDisplay') + 1, 0,
      {columnId: INHERITANCE_COLUMN, columnDisplay: 'INHERITANCE.TITLE', displayed: true});
    filtered.splice(filtered.indexOf('filter-targetDisplay') + 1, 0, 'filter-' + INHERITANCE_COLUMN);
    hideShow.splice(hideShow.indexOf('targetDisplay') + 1, 0, INHERITANCE_COLUMN);
  } else {
    columns.splice(index, 1);
    const f = filtered.indexOf('filter-' + INHERITANCE_COLUMN);
    if (f >= 0) {
      filtered.splice(f, 1);
    }
    const h = hideShow.indexOf(INHERITANCE_COLUMN);
    if (h >= 0) {
      hideShow.splice(h, 1);
    }
  }
  return true;
}
