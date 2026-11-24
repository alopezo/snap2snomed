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

import {Injectable, OnDestroy} from '@angular/core';
import {MatPaginatorIntl} from '@angular/material/paginator';
import {TranslateService} from '@ngx-translate/core';
import {Subscription} from 'rxjs';

/** Paginator labels in the current interface language (PAGINATOR.* keys). */
@Injectable()
export class TranslatedPaginatorIntl extends MatPaginatorIntl implements OnDestroy {

  private subscription: Subscription;

  constructor(private translate: TranslateService) {
    super();
    this.subscription = this.translate.stream('PAGINATOR').subscribe(() => this.updateLabels());
  }

  override getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length === 0 || pageSize === 0) {
      return this.translate.instant('PAGINATOR.RANGE_EMPTY', {length});
    }
    const start = page * pageSize;
    const end = start < length ? Math.min(start + pageSize, length) : start + pageSize;
    return this.translate.instant('PAGINATOR.RANGE', {start: start + 1, end, length});
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private updateLabels(): void {
    this.itemsPerPageLabel = this.translate.instant('PAGINATOR.ITEMS_PER_PAGE');
    this.nextPageLabel = this.translate.instant('PAGINATOR.NEXT_PAGE');
    this.previousPageLabel = this.translate.instant('PAGINATOR.PREVIOUS_PAGE');
    this.firstPageLabel = this.translate.instant('PAGINATOR.FIRST_PAGE');
    this.lastPageLabel = this.translate.instant('PAGINATOR.LAST_PAGE');
    this.changes.next();
  }
}
