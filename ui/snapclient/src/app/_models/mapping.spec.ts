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

import {Mapping} from './mapping';
import {Source} from './source';

describe('Mapping', () => {

  it('is to SNOMED CT by default', () => {
    const mapping = new Mapping();
    expect(mapping.direction).toBe('TO_SNOMED');
    expect(Mapping.isFromSnomed(mapping)).toBe(false);
    expect(Mapping.targetSystem(mapping)).toBe('http://snomed.info/sct');
    expect(Mapping.isFromSnomed(null)).toBe(false);
  });

  it('uses the target code set system for maps from SNOMED CT', () => {
    const mapping = new Mapping();
    mapping.direction = 'FROM_SNOMED';
    mapping.targetCodeSet = {...new Source(), id: '7', systemUri: 'http://hl7.org/fhir/sid/icd-10'} as any;
    expect(Mapping.isFromSnomed(mapping)).toBe(true);
    expect(Mapping.targetSystem(mapping)).toBe('http://hl7.org/fhir/sid/icd-10');
    mapping.targetCodeSet = {...new Source(), id: '7'};
    expect(Mapping.targetSystem(mapping)).toBe('urn:snap2snomed:code-set:7');
  });

  it('sends the target code set as a link', () => {
    const mapping = new Mapping();
    mapping.direction = 'FROM_SNOMED';
    mapping.targetCodeSet = {...new Source(), id: '7'};
    mapping.source.id = '3';
    const json = JSON.parse(JSON.stringify(mapping, Mapping.replacer));
    expect(json.targetCodeSet).toBe('/importedCodeSets/7');
    expect(json.source).toBe('/importedCodeSets/3');
    expect(json.direction).toBe('FROM_SNOMED');
  });
});
