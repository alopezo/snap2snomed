/*
 * Copyright © 2022 SNOMED International
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

import {Pipe, PipeTransform} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';

const min = 1000 * 60;

/**
 * How long ago a date was, in the current interface language (LAST_UPDATED.* keys). Impure so
 * that it follows a language change without a page reload.
 */
@Pipe({
    name: 'lastupdated',
    pure: false,
    standalone: false
})
export class LastupdatedPipe implements PipeTransform {

  constructor(private translate: TranslateService) {
  }

  transform(datefield?: Date | null): string | null {
    if (!datefield) {
      return null;
    }
    const today = new Date();
    const diff = Math.floor(today.getTime() - new Date(datefield).getTime());
    const mins = Math.floor(diff / min);
    const hours = Math.floor(mins / 60);
    const days = Math.floor(hours / 24);
    const months = Math.floor(days / 31);
    const years = Math.floor(days / 365.25);

    let interval: string[] = [];
    if (days == 0) {
      if (hours > 0) {
        interval.push(this.unit('HOURS', hours % 24));
      }
      if (mins > 0) {
        interval.push(this.unit('MINUTES', mins % 60));
      }
    } else if (years == 0) {
      interval.push(this.unit('DAYS', days % 31));
    }
    if (months > 0) {
      interval.push(this.unit('MONTHS', months % 12));
    }
    if (years > 0) {
      interval.push(this.unit('YEARS', years));
    }
    interval = interval.slice(-2);
    if (interval.length) {
      return this.translate.instant('LAST_UPDATED.AGO', {interval: interval.join(', ')});
    } else {
      return this.translate.instant('LAST_UPDATED.JUST_NOW');
    }
  }

  private unit(unit: string, count: number): string {
    return this.translate.instant('LAST_UPDATED.' + unit, {count});
  }
}
