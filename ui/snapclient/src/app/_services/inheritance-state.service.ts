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

import {Injectable} from '@angular/core';
import {BehaviorSubject} from 'rxjs';
import {debounceTime, skip} from 'rxjs/operators';
import {Mapping} from '../_models/mapping';
import {InheritanceResult, InheritanceRow, MapService} from './map.service';
import {TargetChangedService} from './target-changed.service';

/**
 * Inheritance of the map being worked on (maps from SNOMED CT with inheritance), shared by the details card,
 * the table and the mapping detail. Recomputed by the API after every target change; the API caches the
 * SNOMED CT descendants, so a refresh only costs an expansion for a newly mapped or stopped concept.
 */
@Injectable({providedIn: 'root'})
export class InheritanceStateService {

  private mapId: string | null = null;
  private readonly subject = new BehaviorSubject<InheritanceResult | null>(null);
  readonly state$ = this.subject.asObservable();
  private byCode = new Map<string, InheritanceRow>();
  loading = false;

  constructor(private mapService: MapService, targetChangedService: TargetChangedService) {
    targetChangedService.targetChanged$.pipe(skip(1), debounceTime(300)).subscribe(() => this.refresh());
  }

  /** Follows a map: loads its inheritance if it has it switched on, clears the state otherwise. */
  watch(mapping: Mapping | null | undefined): void {
    const id = Mapping.hasInheritance(mapping) ? mapping?.id ?? null : null;
    if (id === this.mapId) {
      return;
    }
    this.mapId = id;
    this.byCode = new Map();
    this.subject.next(null);
    this.refresh();
  }

  refresh(): void {
    const mapId = this.mapId;
    if (!mapId) {
      return;
    }
    this.loading = true;
    this.mapService.getInheritance(mapId).subscribe({
      next: result => {
        if (mapId === this.mapId) {
          this.byCode = new Map(result.rows.map(row => [row.code, row]));
          this.loading = false;
          this.subject.next(result);
        }
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  rowFor(code: string | null | undefined): InheritanceRow | undefined {
    return code ? this.byCode.get(code) : undefined;
  }
}
