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

import {Component, Input} from '@angular/core';
import {MatSort} from '@angular/material/sort';

/**
 * In the "Source display" header of a map from SNOMED CT: sorts the rows by the length of the source term
 * (shortest, usually the most general concepts, first; then longest first; then unsorted). It drives the
 * table's MatSort with its own sort key, so the URL, paging and the API sort work as for any column; the
 * header itself keeps sorting alphabetically.
 */
@Component({
  selector: 'app-length-sort',
  templateUrl: './length-sort.component.html',
  styleUrls: ['./length-sort.component.css'],
  standalone: false
})
export class LengthSortComponent {

  /** The API sort key: the length of the source display. */
  static readonly SORT_ID = 'sourceDisplayLength';

  @Input() sort: MatSort | null | undefined;

  get direction(): 'asc' | 'desc' | null {
    if (this.sort?.active !== LengthSortComponent.SORT_ID) {
      return null;
    }
    return this.sort.direction === 'asc' || this.sort.direction === 'desc' ? this.sort.direction : null;
  }

  get tooltip(): string {
    switch (this.direction) {
      case 'asc':
        return 'TABLE.SORT_BY_LENGTH_ASC';
      case 'desc':
        return 'TABLE.SORT_BY_LENGTH_DESC';
      default:
        return 'TABLE.SORT_BY_LENGTH';
    }
  }

  toggle(event: Event): void {
    event.stopPropagation();
    this.sort?.sort({id: LengthSortComponent.SORT_ID, start: 'asc', disableClear: false});
  }
}
