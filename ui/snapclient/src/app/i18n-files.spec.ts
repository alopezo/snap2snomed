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

import en from '../assets/i18n/en.json';
import es from '../assets/i18n/es.json';
import fr from '../assets/i18n/fr.json';
import da from '../assets/i18n/da.json';
import nl from '../assets/i18n/nl.json';
import hu from '../assets/i18n/hu.json';
import sv from '../assets/i18n/sv.json';
import {UI_LANGUAGES} from './_services/ui-language.service';

type Translations = { [key: string]: string | Translations };

const TRANSLATIONS: { [code: string]: Translations } = {en, es, fr, da, nl, hu, sv};

/** Flattens nested translations into 'SECTION.KEY' -> text. */
function flatten(translations: Translations, prefix = ''): Map<string, string> {
  const result = new Map<string, string>();
  Object.entries(translations).forEach(([key, value]) => {
    if (typeof value === 'string') {
      result.set(prefix + key, value);
    } else {
      flatten(value, prefix + key + '.').forEach((text, k) => result.set(k, text));
    }
  });
  return result;
}

function params(text: string): string[] {
  return (text.match(/{{\s*\w+\s*}}/g) ?? []).map(p => p.replace(/[{}\s]/g, '')).sort();
}

describe('i18n files', () => {
  const source = flatten(en);

  it('should have a translation file for every interface language', () => {
    expect(Object.keys(TRANSLATIONS).sort()).toEqual(UI_LANGUAGES.map(l => l.code).sort());
  });

  UI_LANGUAGES.filter(l => l.code !== 'en').forEach(language => {
    describe(language.code, () => {
      const target = flatten(TRANSLATIONS[language.code]);

      it('should have every English key', () => {
        expect([...source.keys()].filter(k => !target.has(k))).toEqual([]);
      });

      it('should have no key that English lacks', () => {
        expect([...target.keys()].filter(k => !source.has(k))).toEqual([]);
      });

      it('should have no empty text', () => {
        expect([...target.entries()].filter(([, text]) => !text.trim()).map(([k]) => k)).toEqual([]);
      });

      it('should keep the English interpolation parameters', () => {
        const mismatched = [...source.entries()]
          .filter(([k, text]) => target.has(k) && params(text).join() !== params(target.get(k) as string).join())
          .map(([k]) => k);
        expect(mismatched).toEqual([]);
      });
    });
  });
});
