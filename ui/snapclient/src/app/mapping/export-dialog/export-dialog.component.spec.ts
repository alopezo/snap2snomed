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

import {ComponentFixture, TestBed} from '@angular/core/testing';
import {FormsModule} from '@angular/forms';
import {By} from '@angular/platform-browser';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatRadioModule} from '@angular/material/radio';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatButtonModule} from '@angular/material/button';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {EXPORT_OPTIONS_STORAGE_KEY, ExportDialogComponent} from './export-dialog.component';

describe('ExportDialogComponent', () => {
  let fixture: ComponentFixture<ExportDialogComponent>;
  let component: ExportDialogComponent;
  const close = vi.fn();

  const relationshipCounts = vi.fn();

  async function create(refinedTargetCount: number): Promise<void> {
    await TestBed.configureTestingModule({
      declarations: [ExportDialogComponent],
      imports: [FormsModule, MatDialogModule, MatRadioModule, MatCheckboxModule, MatButtonModule, TranslateModule.forRoot()],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: {refinedTargetCount, relationshipCounts}},
        {provide: MatDialogRef, useValue: {close}},
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(ExportDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    localStorage.removeItem(EXPORT_OPTIONS_STORAGE_KEY);
    close.mockReset();
    relationshipCounts.mockReset();
    relationshipCounts.mockReturnValue(of({TARGET_EQUIVALENT: 28, TARGET_BROADER: 8, TARGET_NARROWER: 0,
      TARGET_INEXACT: 4, WITHOUT_TARGET: 3}));
  });

  it('hides the refined target choice when the map has none', async () => {
    await create(0);
    expect(fixture.nativeElement.textContent).not.toContain('EXPORT.REFINED_TARGETS');
    component.options.targetForm = 'focus';
    expect(component.effectiveOptions.targetForm).toBe('expression');
  });

  it('shows the refined target choice and the relationship note for focus only', async () => {
    await create(3);
    expect(fixture.nativeElement.textContent).toContain('EXPORT.REFINED_TARGETS');
    component.options.targetForm = 'focus';
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('.note'))).toBeTruthy();
  });

  it('drops options that do not apply to FHIR', async () => {
    await create(3);
    component.options = {format: 'fhir-json', extended: true, targetForm: 'both', inherited: true, inverse: false};
    expect(component.effectiveOptions).toEqual({format: 'fhir-json', extended: false, targetForm: 'expression', inherited: true, inverse: false});
    component.options = {format: 'fhir-json', extended: false, targetForm: 'focus', inherited: true, inverse: false};
    expect(component.effectiveOptions.targetForm).toBe('focus');
  });

  it('returns the options and remembers them', async () => {
    await create(1);
    component.options = {format: 'xlsx', extended: true, targetForm: 'both', inherited: false, inverse: false};
    component.export();
    // without inheritance on the map, inherited stays true (the option does not apply)
    expect(close).toHaveBeenCalledWith({format: 'xlsx', extended: true, targetForm: 'both', inherited: true, inverse: false});
    expect(ExportDialogComponent.readStored()).toEqual({format: 'xlsx', extended: true, targetForm: 'both', inherited: false, inverse: false});
  });

  it('ignores an unreadable stored choice', () => {
    localStorage.setItem(EXPORT_OPTIONS_STORAGE_KEY, '{"format":"pdf"}');
    expect(ExportDialogComponent.readStored()).toEqual({format: 'csv', extended: false, targetForm: 'expression', inherited: true, inverse: false});
  });
  it('offers the inverse ConceptMap for FHIR only, warning about the mappings that are not equivalent', async () => {
    await create(0);
    expect(fixture.nativeElement.querySelector('#exportInverse')).toBeNull();
    component.options.format = 'fhir-json';
    component.options.inverse = true;
    component.refreshCounts();
    fixture.detectChanges();
    expect(relationshipCounts).toHaveBeenCalledWith('expression', true);
    expect(component.notEquivalent).toBe(12);
    expect(component.withTarget).toBe(40);
    expect(fixture.nativeElement.querySelector('#inverseWarning').textContent).toContain('EXPORT.INVERSE_NOT_EQUIVALENT');
    expect(fixture.nativeElement.querySelector('#inverseLeftOut')).toBeTruthy();
    expect(component.effectiveOptions.inverse).toBe(true);
    // asked once per combination of options
    component.refreshCounts();
    expect(relationshipCounts).toHaveBeenCalledTimes(1);
  });

  it('shows no warning when every mapping is equivalent, and never inverts a tabular export', async () => {
    relationshipCounts.mockReturnValue(of({TARGET_EQUIVALENT: 10, TARGET_BROADER: 0, TARGET_NARROWER: 0,
      TARGET_INEXACT: 0, WITHOUT_TARGET: 0}));
    await create(0);
    component.options.format = 'fhir-json';
    component.options.inverse = true;
    component.refreshCounts();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#inverseWarning')).toBeNull();
    component.options.format = 'csv';
    expect(component.effectiveOptions.inverse).toBe(false);
  });
});
