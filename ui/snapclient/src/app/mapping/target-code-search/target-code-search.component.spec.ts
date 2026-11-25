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
import {ReactiveFormsModule} from '@angular/forms';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {Mapping} from '../../_models/mapping';
import {Source} from '../../_models/source';
import {MapService} from '../../_services/map.service';
import {SelectionService} from '../../_services/selection.service';
import {DraggableDirective} from '../../_directives/draggable.directive';
import {DroppableDirective} from '../../_directives/droppable.directive';
import {TargetCodeSearchComponent} from './target-code-search.component';

describe('TargetCodeSearchComponent', () => {
  let fixture: ComponentFixture<TargetCodeSearchComponent>;
  let component: TargetCodeSearchComponent;
  const mapService = {searchTargetCodes: vi.fn()};

  function fromSnomedMapping(): Mapping {
    const mapping = new Mapping();
    mapping.id = '5';
    mapping.direction = 'FROM_SNOMED';
    mapping.targetCodeSet = {...new Source(), id: '7', name: 'ICD-10', version: '2019', systemUri: 'http://hl7.org/fhir/sid/icd-10'} as any;
    return mapping;
  }

  beforeEach(async () => {
    mapService.searchTargetCodes.mockReturnValue(of([
      {code: 'J45', display: 'Asthma', additionalColumns: []},
      {code: 'J46', display: 'Status asthmaticus', additionalColumns: []},
    ]));
    await TestBed.configureTestingModule({
      declarations: [TargetCodeSearchComponent, DraggableDirective],
      imports: [DroppableDirective, ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatIconModule, MatProgressBarModule,
        NoopAnimationsModule, TranslateModule.forRoot()],
      providers: [{provide: MapService, useValue: mapService}]
    }).compileComponents();
    fixture = TestBed.createComponent(TargetCodeSearchComponent);
    component = fixture.componentInstance;
    component.mapping = fromSnomedMapping();
    component.ngOnChanges({mapping: {} as any});
    fixture.detectChanges();
  });

  it('lists the codes of the target code set', () => {
    expect(mapService.searchTargetCodes).toHaveBeenCalledWith('5', {text: ''}, 50);
    expect(fixture.nativeElement.querySelectorAll('.result').length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('Status asthmaticus');
  });

  it('selects a code with the target code set system', () => {
    const selections: any[] = [];
    TestBed.inject(SelectionService).subscribe({next: s => selections.push(s)});
    component.select({code: 'J45', display: 'Asthma', additionalColumns: []});
    expect(selections.pop()).toEqual({code: 'J45', display: 'Asthma', system: 'http://hl7.org/fhir/sid/icd-10'});
    expect(component.dragData({code: 'J45', display: 'Asthma', additionalColumns: []}).system).toBe('http://hl7.org/fhir/sid/icd-10');
  });

  it('takes dropped source text as the search text', () => {
    component.onDrop({data: 'wheezing'} as any);
    expect(component.searchControl.value).toBe('wheezing');
  });
});
