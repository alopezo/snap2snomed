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
import {Subscription, of} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {Mapping} from '../../_models/mapping';
import {MapService, TargetCode} from '../../_services/map.service';
import {SelectionService} from '../../_services/selection.service';

/** Shows the selected code of the target code set of a map from SNOMED CT, with its additional columns. */
@Component({
  selector: 'app-target-code-details',
  templateUrl: './target-code-details.component.html',
  styleUrls: ['./target-code-details.component.css'],
  standalone: false
})
export class TargetCodeDetailsComponent implements OnInit, OnDestroy {
  @Input() mapping: Mapping | null | undefined;
  @Input() active = true;

  readonly dragZones = ['conceptZone', 'relationshipZone'];

  code: TargetCode | null = null;
  loading = false;
  notFound = false;

  private subscription = new Subscription();

  constructor(private mapService: MapService, private selectionService: SelectionService) {
  }

  get system(): string {
    return Mapping.targetSystem(this.mapping);
  }

  ngOnInit(): void {
    this.subscription.add(this.selectionService.subscribeWithCurrent({
      next: (selection: any) => this.show(selection)
    }));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private show(selection: any): void {
    if (!this.active || !selection?.code || selection.system !== this.system || !this.mapping?.id) {
      return;
    }
    const wanted: string = selection.code;
    this.loading = true;
    this.notFound = false;
    this.mapService.searchTargetCodes(this.mapping.id, {code: wanted}).pipe(catchError(() => of([] as TargetCode[])))
      .subscribe(codes => {
        this.loading = false;
        this.code = codes[0] ?? null;
        this.notFound = !this.code;
      });
  }
}
