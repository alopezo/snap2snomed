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

import {applyInheritanceColumn} from './inheritance-column';

describe('applyInheritanceColumn', () => {
  it('adds the column after the target display once, and removes it', () => {
    const columns = [{columnId: 'targetCode', columnDisplay: '', displayed: true},
      {columnId: 'targetDisplay', columnDisplay: '', displayed: true}, {columnId: 'status', columnDisplay: '', displayed: true}];
    const filtered = ['filter-targetCode', 'filter-targetDisplay', 'filter-status'];
    const hideShow = ['targetCode', 'targetDisplay'];
    expect(applyInheritanceColumn(true, columns, filtered, hideShow)).toBe(true);
    expect(applyInheritanceColumn(true, columns, filtered, hideShow)).toBe(false);
    expect(columns.map(c => c.columnId)).toEqual(['targetCode', 'targetDisplay', 'inheritance', 'status']);
    expect(filtered).toEqual(['filter-targetCode', 'filter-targetDisplay', 'filter-inheritance', 'filter-status']);
    expect(hideShow).toEqual(['targetCode', 'targetDisplay', 'inheritance']);
    expect(applyInheritanceColumn(false, columns, filtered, hideShow)).toBe(true);
    expect(columns.map(c => c.columnId)).toEqual(['targetCode', 'targetDisplay', 'status']);
    expect(filtered).toEqual(['filter-targetCode', 'filter-targetDisplay', 'filter-status']);
  });
});
