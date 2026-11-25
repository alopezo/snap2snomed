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
import {By} from '@angular/platform-browser';
import {MatCardModule} from '@angular/material/card';
import {MatChipsModule} from '@angular/material/chips';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {FhirService} from '../../_services/fhir.service';
import {SelectionService} from '../../_services/selection.service';
import {DraggableDirective} from '../../_directives/draggable.directive';
import {TargetRefinementComponent} from './target-refinement.component';

describe('TargetRefinementComponent', () => {
  let fixture: ComponentFixture<TargetRefinementComponent>;
  let component: TargetRefinementComponent;
  let selectionService: SelectionService;
  const fhirService = {
    getRefinementOptions: vi.fn(),
    getTargetDisplay: vi.fn(),
  };

  beforeEach(async () => {
    fhirService.getRefinementOptions.mockReturnValue(of({laterality: true, severity: true}));
    fhirService.getTargetDisplay.mockImplementation((code: string) => of(
      code === '71620000' ? 'Fracture of femur' : 'Fracture of femur - Left'));
    await TestBed.configureTestingModule({
      declarations: [TargetRefinementComponent, DraggableDirective],
      imports: [MatCardModule, MatChipsModule, MatIconModule, MatTooltipModule, MatProgressSpinnerModule,
        TranslateModule.forRoot()],
      providers: [{provide: FhirService, useValue: fhirService}]
    }).compileComponents();
    selectionService = TestBed.inject(SelectionService);
    fixture = TestBed.createComponent(TargetRefinementComponent);
    component = fixture.componentInstance;
    component.version = 'http://snomed.info/sct/900000000000207008/version/20250101';
    fixture.detectChanges();
  });

  it('asks for a concept when nothing is selected', () => {
    expect(fixture.nativeElement.textContent).toContain('REFINEMENT.SELECT_CONCEPT');
    expect(component.expression).toBeNull();
  });

  it('builds the expression and offers the drag handle', () => {
    selectionService.select({code: '71620000', display: 'Fracture of femur', system: 'http://snomed.info/sct'});
    fixture.detectChanges();
    expect(fhirService.getRefinementOptions).toHaveBeenCalledWith('71620000', component.version);
    expect(component.focusDisplay).toBe('Fracture of femur');
    expect(fixture.debugElement.query(By.css('.grip'))).toBeNull();

    component.setLaterality('7771000');
    fixture.detectChanges();
    expect(component.expression).toBe('71620000:272741003=7771000');
    expect(component.dragData).toEqual({code: '71620000:272741003=7771000', display: 'Fracture of femur - Left',
      system: 'http://snomed.info/sct'});
    expect(fixture.debugElement.query(By.css('.grip'))).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('71620000:272741003=7771000');

    component.setSeverity('24484000');
    expect(component.expression).toBe('71620000:272741003=7771000,246112005=24484000');
    component.setLaterality(undefined);
    expect(component.expression).toBe('71620000:246112005=24484000');
  });

  it('does not offer a refinement the concept does not accept', () => {
    fhirService.getRefinementOptions.mockReturnValue(of({laterality: false, severity: true}));
    selectionService.select({code: '22298006', display: 'Myocardial infarction', system: 'http://snomed.info/sct'});
    fixture.detectChanges();
    component.setLaterality('7771000');
    expect(component.expression).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('REFINEMENT.NOT_LATERALIZABLE');
  });

  it('refines the focus of a selected refined target', () => {
    selectionService.select({code: '71620000:272741003=7771000', display: 'Fracture of femur - Left', system: 'http://snomed.info/sct'});
    expect(component.focus).toBe('71620000');
  });

  it('cannot be dragged when editing is disabled', () => {
    component.disabled = true;
    selectionService.select({code: '71620000', display: 'Fracture of femur', system: 'http://snomed.info/sct'});
    component.setLaterality('7771000');
    expect(component.canDrag).toBe(false);
  });
});
