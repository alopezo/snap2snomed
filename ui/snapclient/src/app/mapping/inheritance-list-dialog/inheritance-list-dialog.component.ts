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

import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {InheritanceRow} from '../../_services/map.service';

export interface InheritanceListDialogData {
  titleKey: string;
  rows: InheritanceRow[];
}

/** Lists the rows of a map with inheritance in one state (uncovered, in conflict or stopped by a row). */
@Component({
  selector: 'app-inheritance-list-dialog',
  templateUrl: './inheritance-list-dialog.component.html',
  styleUrls: ['./inheritance-list-dialog.component.css'],
  standalone: false
})
export class InheritanceListDialogComponent {

  copied = false;

  constructor(@Inject(MAT_DIALOG_DATA) public data: InheritanceListDialogData) {
  }

  detail(row: InheritanceRow): string {
    if (row.state === 'CONFLICT') {
      return row.candidates.join(', ');
    }
    return row.fromCode ? `${row.fromCode} ${row.fromDisplay ?? ''}`.trim() : '';
  }

  get text(): string {
    return this.data.rows.map(row => [row.code, row.display, this.detail(row)].join('\t')).join('\n');
  }
}
