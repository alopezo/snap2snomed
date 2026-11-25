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

import {Component, Input, OnChanges, OnDestroy, OnInit} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {Subscription} from 'rxjs';
import {Mapping} from '../../_models/mapping';
import {InheritanceResult, InheritanceState} from '../../_services/map.service';
import {InheritanceStateService} from '../../_services/inheritance-state.service';
import {InheritanceListDialogComponent} from '../inheritance-list-dialog/inheritance-list-dialog.component';

/** Coverage of a map with inheritance: rows mapped, inherited, stopped, in conflict and uncovered. */
@Component({
  selector: 'app-inheritance-summary',
  templateUrl: './inheritance-summary.component.html',
  styleUrls: ['./inheritance-summary.component.css'],
  standalone: false
})
export class InheritanceSummaryComponent implements OnInit, OnChanges, OnDestroy {
  @Input() mapping: Mapping | null | undefined;

  /** States shown, in order; the ones that need attention open a list. */
  readonly states: {state: InheritanceState, key: string, attention: boolean}[] = [
    {state: 'ASSERTED', key: 'INHERITANCE.ASSERTED', attention: false},
    {state: 'INHERITED', key: 'INHERITANCE.INHERITED', attention: false},
    {state: 'UNCOVERED', key: 'INHERITANCE.UNCOVERED', attention: true},
    {state: 'CONFLICT', key: 'INHERITANCE.CONFLICT', attention: true},
    {state: 'BLOCKED', key: 'INHERITANCE.BLOCKED', attention: true},
    {state: 'STOPPED', key: 'INHERITANCE.STOPPED', attention: false},
    {state: 'NO_MAP', key: 'INHERITANCE.NO_MAP', attention: false},
  ];

  result: InheritanceResult | null = null;
  private subscription = new Subscription();

  constructor(public inheritanceState: InheritanceStateService, private dialog: MatDialog) {
  }

  ngOnInit(): void {
    this.subscription.add(this.inheritanceState.state$.subscribe(result => this.result = result));
  }

  ngOnChanges(): void {
    this.inheritanceState.watch(this.mapping);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  count(state: InheritanceState): number {
    return this.result?.counts?.[state] ?? 0;
  }

  open(state: InheritanceState, key: string): void {
    const rows = this.result?.rows.filter(row => row.state === state) ?? [];
    if (rows.length) {
      this.dialog.open(InheritanceListDialogComponent, {width: '640px', data: {titleKey: key, rows}});
    }
  }
}
