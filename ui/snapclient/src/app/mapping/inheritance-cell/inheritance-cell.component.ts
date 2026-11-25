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
import {InheritanceRow} from '../../_services/map.service';

/**
 * The inheritance column of the mapping tables: a short summary of a row's inheritance that opens a popover
 * with the details (where it inherits from, what it passes on, conflicts, stops).
 */
@Component({
  selector: 'app-inheritance-cell',
  templateUrl: './inheritance-cell.component.html',
  styleUrls: ['./inheritance-cell.component.css'],
  standalone: false
})
export class InheritanceCellComponent {
  @Input() row: InheritanceRow | undefined;

  get attention(): boolean {
    return this.row?.state === 'CONFLICT' || this.row?.state === 'UNCOVERED';
  }

  get passesOn(): number {
    return (this.row?.inheritedBy ?? 0) + (this.row?.generatedBy ?? 0);
  }
}
