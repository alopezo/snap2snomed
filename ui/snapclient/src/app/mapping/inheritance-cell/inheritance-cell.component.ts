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
import {ACCEPTABLE_INHERITANCE, DescendantsInfo, InheritanceRow} from '../../_services/map.service';
import {InheritanceStateService} from '../../_services/inheritance-state.service';

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
  /** Rows of the map count their descendants when the popover opens; generated concepts (not rows) do not. */
  @Input() countDescendants = true;

  descendants: DescendantsInfo | null = null;
  descendantsLoading = false;
  descendantsFailed = false;

  constructor(private inheritanceState: InheritanceStateService) {
  }

  /** Counts the descendants of the row's concept the first time the popover opens (cached until a refresh). */
  loadDescendants(): void {
    if (!this.countDescendants || !this.row || this.descendantsLoading) {
      return;
    }
    this.descendantsLoading = true;
    this.descendantsFailed = false;
    this.inheritanceState.descendantsInfo(this.row.code).subscribe({
      next: info => {
        this.descendants = info;
        this.descendantsLoading = false;
      },
      error: () => {
        this.descendantsFailed = true;
        this.descendantsLoading = false;
      }
    });
  }

  get attention(): boolean {
    return this.row?.state === 'CONFLICT' || this.row?.state === 'UNCOVERED' || !!this.row?.inheritanceChanged;
  }

  /** Inherits (or is stopped) and was not accepted yet. */
  get pending(): boolean {
    return !!this.row && ACCEPTABLE_INHERITANCE.includes(this.row.state) && !this.row.accepted;
  }

  /** The row has a mapping of its own: its descendants inherit it now, not "if you map it". */
  get mapped(): boolean {
    return this.row?.state === 'ASSERTED';
  }

  get passesOn(): number {
    return (this.row?.inheritedBy ?? 0) + (this.row?.generatedBy ?? 0);
  }
}
