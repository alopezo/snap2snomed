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

import {SimpleChange} from '@angular/core';
import {UntypedFormControl} from '@angular/forms';
import {of} from 'rxjs';
import {CUSTOM_DISPLAY_LANGUAGE, DisplayLanguagePickerComponent} from './display-language-picker.component';

describe('DisplayLanguagePickerComponent', () => {
  const spanish = 'es-X-450828004,en-X-450828004,en-X-900000000000509007';
  let component: DisplayLanguagePickerComponent;
  let changes: (string | null)[];
  let dialog: {open: ReturnType<typeof vi.fn>};

  beforeEach(() => {
    const metadata = {contextsForVersion: vi.fn(() => of([{name: 'Spanish', languageDialects: spanish}]))};
    const fhir = {languageRefsets: vi.fn(() => of([{code: '450828004', display: 'Latin American Spanish'}]))};
    dialog = {open: vi.fn()};
    component = new DisplayLanguagePickerComponent(metadata as any, fhir as any, dialog as any);
    changes = [];
    component.registerOnChange(value => changes.push(value));
    component.versionUri = 'http://snomed.info/sct/11000221109/version/20260520';
    component.ngOnChanges({versionUri: new SimpleChange(null, component.versionUri, true)});
  });

  const errors = () => component.validate(new UntypedFormControl());

  it('shows English (FSN) for no display language', () => {
    component.writeValue(null);
    expect(component.selection).toBe('');
    expect(errors()).toBeNull();
  });

  it('selects the named context matching the stored sequence', () => {
    component.writeValue(' es-X-450828004, en-X-450828004,en-X-900000000000509007');
    expect(component.selection).toBe(spanish);
  });

  it('shows a stored sequence that is not a context as a custom sequence', () => {
    component.writeValue('es,en');
    expect(component.selection).toBe(CUSTOM_DISPLAY_LANGUAGE);
    expect(component.customControl.value).toBe('es,en');
  });

  it('validates the custom sequence and emits it normalized', () => {
    component.onSelect(CUSTOM_DISPLAY_LANGUAGE);
    component.customControl.setValue('');
    expect(errors()).toEqual({displayLanguageRequired: true});

    component.customControl.setValue('not a lang!!');
    expect(errors()).toEqual({displayLanguageFormat: true});
    expect(component.canTest).toBe(false);

    component.customControl.setValue(' es-AR , en ');
    expect(errors()).toBeNull();
    expect(changes[changes.length - 1]).toBe('es-AR,en');
  });

  it('starts a custom sequence from the current context and loads the edition refsets', () => {
    component.writeValue(spanish);
    component.onSelect(CUSTOM_DISPLAY_LANGUAGE);
    expect(component.customControl.value).toBe(spanish);
    expect(component.refsets).toEqual([{code: '450828004', display: 'Latin American Spanish'}]);
  });

  it('builds entries from a language and an optional refset', () => {
    component.onSelect(CUSTOM_DISPLAY_LANGUAGE);
    component.builderLanguage.setValue('ES');
    component.builderRefset.setValue('450828004');
    component.addEntry();
    component.builderLanguage.setValue('en');
    component.addEntry();
    expect(component.customControl.value).toBe('es-X-450828004,en');
    expect(changes[changes.length - 1]).toBe('es-X-450828004,en');
  });

  it('opens the test dialog with the current sequence and scope', () => {
    component.scope = '<< 404684003';
    component.writeValue(spanish);
    component.openTest();
    expect(dialog.open).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({
      data: {versionUri: component.versionUri, scope: '<< 404684003', displayLanguage: spanish}
    }));
  });
});
