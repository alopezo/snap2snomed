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

import {Component, Input, OnDestroy} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {Subscription} from 'rxjs';
import {SnomedUtils} from '../_utils/snomed_utils';

@Component({
    selector: 'app-source-version',
    templateUrl: './target-version.component.html',
    styleUrls: ['./target-version.component.css'],
    standalone: false
})
export class TargetVersionComponent implements OnDestroy {
  @Input() system = 'http://snomed.info/sct';

  @Input() set version(v: string | null | undefined) {
    v = v ?? 'http://snomed.info/sct?fhir_vs';
    const {experimental, edition, effectiveTime} = SnomedUtils.parserVersionUri(v);
    this.parsed = {experimental: !!experimental, edition: edition ?? null, effectiveTime: effectiveTime ?? null};
    this.country = (edition && SnomedUtils.toCountry(edition)) ?? null;
    this.updateTexts();
  };

  // Set (translated) by the version input, and again when the interface language changes
  label = '';
  country: string | null = null;
  effectiveTime: Date | string = '';
  experimental = '';

  private parsed: { experimental: boolean, edition: string | null, effectiveTime: string | null } | null = null;
  private langChange: Subscription;

  constructor(private translate: TranslateService) {
    this.langChange = this.translate.onLangChange.subscribe(() => this.updateTexts());
  }

  ngOnDestroy(): void {
    this.langChange.unsubscribe();
  }

  private updateTexts(): void {
    if (!this.parsed) {
      return;
    }
    const {experimental, edition, effectiveTime} = this.parsed;
    const editionKey = 'EDITION.' + edition;
    const keys = [editionKey, 'EDITION.EXPERIMENTAL', 'SHELL_TARGET_VERSION.UNKNOWN_EDITION',
      'SHELL_TARGET_VERSION.LATEST_AVAILABLE'];
    this.translate.get(keys).subscribe((texts) => {
      this.experimental = experimental ? texts['EDITION.EXPERIMENTAL'] : '';
      // Fall back to the generic label when the edition is missing or has no translation
      this.label = edition && texts[editionKey] !== editionKey
        ? texts[editionKey]
        : texts['SHELL_TARGET_VERSION.UNKNOWN_EDITION'];
      this.effectiveTime = effectiveTime
        ? this.formatEffectiveTime(effectiveTime)
        : texts['SHELL_TARGET_VERSION.LATEST_AVAILABLE'];
    });
  }

  /** Formats a yyyyMMdd or yyyyMMdd'T'HHmmss'Z' effective time in the interface language. */
  private formatEffectiveTime(effectiveTime: string): string {
    const year = +effectiveTime.slice(0, 4);
    const month = +effectiveTime.slice(4, 6) - 1;
    const day = +effectiveTime.slice(6, 8);
    const options: Intl.DateTimeFormatOptions = {year: 'numeric', month: 'short', day: '2-digit', timeZone: 'UTC'};
    let date = Date.UTC(year, month, day);
    if (effectiveTime.length === 16) {
      date = Date.UTC(year, month, day, +effectiveTime.slice(9, 11), +effectiveTime.slice(11, 13),
        +effectiveTime.slice(13, 15));
      Object.assign(options, {hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit'});
    }
    const lang = this.translate.currentLang ?? this.translate.getDefaultLang() ?? 'en';
    try {
      return new Intl.DateTimeFormat(lang, options).format(date);
    } catch (e) {
      return new Intl.DateTimeFormat('en', options).format(date);
    }
  }
}
