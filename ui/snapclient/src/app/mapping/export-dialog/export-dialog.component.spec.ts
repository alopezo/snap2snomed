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
import {EXPORT_OPTIONS_STORAGE_KEY, ExportDialogComponent} from './export-dialog.component';

describe('ExportDialogComponent', () => {
  let fixture: ComponentFixture<ExportDialogComponent>;
  let component: ExportDialogComponent;
  const close = vi.fn();

  async function create(refinedTargetCount: number): Promise<void> {
    await TestBed.configureTestingModule({
      declarations: [ExportDialogComponent],
      imports: [FormsModule, MatDialogModule, MatRadioModule, MatCheckboxModule, MatButtonModule, TranslateModule.forRoot()],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: {refinedTargetCount}},
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
    component.options = {format: 'fhir-json', extended: true, targetForm: 'both'};
    expect(component.effectiveOptions).toEqual({format: 'fhir-json', extended: false, targetForm: 'expression'});
    component.options = {format: 'fhir-json', extended: false, targetForm: 'focus'};
    expect(component.effectiveOptions.targetForm).toBe('focus');
  });

  it('returns the options and remembers them', async () => {
    await create(1);
    component.options = {format: 'xlsx', extended: true, targetForm: 'both'};
    component.export();
    expect(close).toHaveBeenCalledWith({format: 'xlsx', extended: true, targetForm: 'both'});
    expect(ExportDialogComponent.readStored()).toEqual({format: 'xlsx', extended: true, targetForm: 'both'});
  });

  it('ignores an unreadable stored choice', () => {
    localStorage.setItem(EXPORT_OPTIONS_STORAGE_KEY, '{"format":"pdf"}');
    expect(ExportDialogComponent.readStored()).toEqual({format: 'csv', extended: false, targetForm: 'expression'});
  });
});
