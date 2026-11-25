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

import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {MatCheckboxChange} from '@angular/material/checkbox';
import {Subscription} from 'rxjs';
import {InheritanceRow, MapService} from '../../_services/map.service';
import {InheritanceStateService} from '../../_services/inheritance-state.service';

/**
 * In the mapping detail of a map with inheritance: what the current concept inherits (or passes on), and
 * the switch that stops inheritance for it and its descendants.
 */
@Component({
  selector: 'app-row-inheritance',
  templateUrl: './row-inheritance.component.html',
  styleUrls: ['./row-inheritance.component.css'],
  standalone: false
})
export class RowInheritanceComponent implements OnInit, OnDestroy {
  @Input() code: string | null | undefined;
  @Input() rowId: string | null | undefined;
  @Input() disabled = false;

  saving = false;
  failed = false;
  private subscription = new Subscription();

  constructor(public inheritanceState: InheritanceStateService, private mapService: MapService) {
  }

  ngOnInit(): void {
    // re-render when the state is recomputed
    this.subscription.add(this.inheritanceState.state$.subscribe(() => this.failed = false));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  get row(): InheritanceRow | undefined {
    return this.inheritanceState.rowFor(this.code);
  }

  get inheritedTargets(): string {
    return (this.row?.targets ?? []).map(t => `${t.code} ${t.display}`).join(', ');
  }

  toggleStop(event: MatCheckboxChange): void {
    if (!this.rowId) {
      return;
    }
    this.saving = true;
    this.failed = false;
    this.mapService.updateStopInheritance(this.rowId, event.checked).subscribe({
      next: () => {
        this.saving = false;
        this.inheritanceState.refresh();
      },
      error: () => {
        this.saving = false;
        this.failed = true;
        event.source.checked = !event.checked;
      }
    });
  }
}
