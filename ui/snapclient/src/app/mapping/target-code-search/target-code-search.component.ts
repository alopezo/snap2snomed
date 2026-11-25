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

import {Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges} from '@angular/core';
import {UntypedFormControl} from '@angular/forms';
import {Subscription, of} from 'rxjs';
import {catchError, debounceTime, distinctUntilChanged, switchMap, tap} from 'rxjs/operators';
import {Mapping} from '../../_models/mapping';
import {MapService, TargetCode} from '../../_services/map.service';
import {SelectionService} from '../../_services/selection.service';
import {DroppableEventObject} from '../../_directives/droppable.directive';

export const TARGET_CODE_PAGE_SIZE = 50;

/**
 * Searches the target code set of a map from SNOMED CT (by code or text). Results are dragged onto a
 * relationship or a table row like SNOMED CT concepts in maps to SNOMED CT.
 */
@Component({
  selector: 'app-target-code-search',
  templateUrl: './target-code-search.component.html',
  styleUrls: ['./target-code-search.component.css'],
  standalone: false
})
export class TargetCodeSearchComponent implements OnInit, OnChanges, OnDestroy {
  @Input() mapping: Mapping | null | undefined;
  @Input() active = true;

  readonly pageSize = TARGET_CODE_PAGE_SIZE;
  readonly dragZones = ['conceptZone', 'relationshipZone'];

  searchControl = new UntypedFormControl('');
  results: TargetCode[] = [];
  loading = false;
  failed = false;
  selectedCode: string | null = null;

  private subscription = new Subscription();

  constructor(private mapService: MapService, private selectionService: SelectionService) {
  }

  get system(): string {
    return Mapping.targetSystem(this.mapping);
  }

  ngOnInit(): void {
    this.subscription.add(this.searchControl.valueChanges.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      tap(() => {
        this.loading = true;
        this.failed = false;
      }),
      switchMap(text => this.search(text))
    ).subscribe(results => {
      this.results = results;
      this.loading = false;
    }));
    this.subscription.add(this.selectionService.subscribe({
      next: (selection: any) => this.selectedCode = selection?.system === this.system ? selection.code : null
    }));
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.mapping && this.mapping?.id) {
      this.loading = true;
      this.search(this.searchControl.value).subscribe(results => {
        this.results = results;
        this.loading = false;
      });
    }
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  select(code: TargetCode): void {
    if (this.active) {
      this.selectionService.select({code: code.code, display: code.display, system: this.system});
    }
  }

  dragData(code: TargetCode): {code: string, display: string, system: string} {
    return {code: code.code, display: code.display, system: this.system};
  }

  /** Text dragged from the source column of the table becomes the search text. */
  onDrop(event: DroppableEventObject): void {
    if (typeof event.data === 'string') {
      this.searchControl.setValue(event.data);
    }
  }

  private search(text: string) {
    if (!this.mapping?.id) {
      return of([] as TargetCode[]);
    }
    return this.mapService.searchTargetCodes(this.mapping.id, {text: (text ?? '').trim()}, TARGET_CODE_PAGE_SIZE).pipe(
      catchError(() => {
        this.failed = true;
        return of([] as TargetCode[]);
      })
    );
  }
}
