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

import {DOCUMENT, registerLocaleData} from '@angular/common';
import {Inject, Injectable} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import localeEs from '@angular/common/locales/es';
import localeFr from '@angular/common/locales/fr';
import localeDa from '@angular/common/locales/da';
import localeNl from '@angular/common/locales/nl';
import localeHu from '@angular/common/locales/hu';
import localeSv from '@angular/common/locales/sv';

/** A language the user interface is translated into (src/assets/i18n/<code>.json). */
export interface UiLanguage {
  code: string;
  /** The language's name in that language, as shown in the language selector. */
  nativeName: string;
}

/** Languages the user interface is available in. English is the source and fallback language. */
export const UI_LANGUAGES: UiLanguage[] = [
  {code: 'en', nativeName: 'English'},
  {code: 'es', nativeName: 'Español'},
  {code: 'fr', nativeName: 'Français'},
  {code: 'da', nativeName: 'Dansk'},
  {code: 'nl', nativeName: 'Nederlands'},
  {code: 'hu', nativeName: 'Magyar'},
  {code: 'sv', nativeName: 'Svenska'},
];

export const FALLBACK_UI_LANGUAGE = 'en';

/** Where the chosen interface language is kept; it survives logging in and out. */
export const UI_LANGUAGE_STORAGE_KEY = 'snap2snomed.uiLanguage';

[localeEs, localeFr, localeDa, localeNl, localeHu, localeSv].forEach(locale => registerLocaleData(locale));

/**
 * Chooses and switches the user interface language (not to be confused with a map's display
 * language, which selects the SNOMED CT terms shown for concepts).
 */
@Injectable({providedIn: 'root'})
export class UiLanguageService {

  readonly languages = UI_LANGUAGES;

  constructor(private translate: TranslateService,
              @Inject(DOCUMENT) private document: Document) {
  }

  /** Picks the stored choice, else the browser language, else the configured default. */
  init(defaultLang?: string): void {
    const fallback = UiLanguageService.isSupported(defaultLang) ? defaultLang as string : FALLBACK_UI_LANGUAGE;
    this.translate.setDefaultLang(FALLBACK_UI_LANGUAGE);
    this.translate.addLangs(UI_LANGUAGES.map(l => l.code));
    const stored = this.readStored();
    const browser = this.translate.getBrowserLang();
    const lang = [stored, browser].find(l => UiLanguageService.isSupported(l)) ?? fallback;
    this.apply(lang as string);
  }

  get current(): string {
    return this.translate.currentLang ?? FALLBACK_UI_LANGUAGE;
  }

  /** Switches the interface language and remembers the choice in this browser. */
  use(code: string): void {
    if (!UiLanguageService.isSupported(code)) {
      return;
    }
    try {
      localStorage.setItem(UI_LANGUAGE_STORAGE_KEY, code);
    } catch (e) {
      // Storage may be unavailable (private mode); the choice then lasts for this page only.
    }
    this.apply(code);
  }

  static isSupported(code?: string | null): boolean {
    return !!code && UI_LANGUAGES.some(l => l.code === code);
  }

  private apply(code: string): void {
    this.translate.use(code);
    this.document.documentElement.lang = code;
  }

  private readStored(): string | null {
    try {
      return localStorage.getItem(UI_LANGUAGE_STORAGE_KEY);
    } catch (e) {
      return null;
    }
  }
}
