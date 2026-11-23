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

import {LanguageDialects} from './language-dialects';

// Same cases as LanguageDialectsTest.java in the API: keep both in sync
describe('LanguageDialects', () => {

  it('normalizes no language to null', () => {
    [null, undefined, '', ' ', ' , ,'].forEach(value => expect(LanguageDialects.normalize(value)).toBeNull());
  });

  it('trims entries and drops empty ones', () => {
    expect(LanguageDialects.normalize('es-X-5641000179103, es-X-450828004,,en-X-900000000000509007 '))
      .toBe('es-X-5641000179103,es-X-450828004,en-X-900000000000509007');
  });

  it('converts every entry to the Ontoserver format', () => {
    const cases: [string, string][] = [
      ['en-X-900000000000509007', 'en-x-sctlang-90000000-00005090-07'],
      ['es-X-450828004', 'es-x-sctlang-45082800-4'],
      ['sv-X-46011000052107', 'sv-x-sctlang-46011000-052107'],
      ['en-x-32570271000036106', 'en-x-sctlang-32570271-00003610-6'],
      ['es-X-450828004, en-X-900000000000509007', 'es-x-sctlang-45082800-4,en-x-sctlang-90000000-00005090-07'],
      ['es-AR,en-X-900000000000509007', 'es-AR,en-x-sctlang-90000000-00005090-07'],
    ];
    cases.forEach(([input, expected]) => expect(LanguageDialects.toServerFormat(input, 'ontoserver')).toBe(expected));
  });

  it('keeps the Snowstorm format for other servers', () => {
    (['snowstorm', 'snowstorm-lite', 'other'] as const).forEach(kind =>
      expect(LanguageDialects.toServerFormat('es-X-450828004, en-X-900000000000509007', kind))
        .toBe('es-X-450828004,en-X-900000000000509007'));
    expect(LanguageDialects.toServerFormat(null, 'ontoserver')).toBeNull();
  });

  it('returns the primary language', () => {
    expect(LanguageDialects.primaryLanguage('es-X-450828004,en-X-900000000000509007')).toBe('es');
    expect(LanguageDialects.primaryLanguage('NL-X-31000146106')).toBe('nl');
    expect(LanguageDialects.primaryLanguage('sv')).toBe('sv');
    expect(LanguageDialects.primaryLanguage(null)).toBeNull();
  });

  it('accepts well formed sequences', () => {
    [null, '', 'es', 'es,en', 'es-AR', ' es-X-450828004 , en-X-900000000000509007 ',
      'sv-X-63451000052100,sv-X-46011000052107,en-X-900000000000509007', 'en-x-sctlang-90000000-00005090-07']
      .forEach(value => expect(LanguageDialects.isValid(value)).toBe(true));
  });

  it('rejects malformed sequences', () => {
    ['not a lang!!', 'es;q=0.9', 'es,,en', ',es', 'e', 'es_AR', 'español', 'es-']
      .forEach(value => expect(LanguageDialects.isValid(value)).toBe(false));
  });

  it('detects the server kind from the software name', () => {
    expect(LanguageDialects.serverKindFromSoftwareName('Ontoserver®')).toBe('ontoserver');
    expect(LanguageDialects.serverKindFromSoftwareName('Snowstorm Lite FHIR Terminology Server')).toBe('snowstorm-lite');
    expect(LanguageDialects.serverKindFromSoftwareName('SnowstormX FHIR Server')).toBe('snowstorm');
    expect(LanguageDialects.serverKindFromSoftwareName('HAPI FHIR Server')).toBe('other');
    expect(LanguageDialects.serverKindFromSoftwareName(undefined)).toBe('other');
  });

  it('extracts the edition URI from a version URI', () => {
    expect(LanguageDialects.editionUri('http://snomed.info/sct/11000221109/version/20260520'))
      .toBe('http://snomed.info/sct/11000221109');
    expect(LanguageDialects.editionUri(null)).toBeNull();
  });
});
