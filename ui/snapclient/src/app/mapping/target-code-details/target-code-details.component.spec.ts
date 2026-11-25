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
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {Mapping} from '../../_models/mapping';
import {Source} from '../../_models/source';
import {MapService} from '../../_services/map.service';
import {SelectionService} from '../../_services/selection.service';
import {DraggableDirective} from '../../_directives/draggable.directive';
import {TargetCodeDetailsComponent} from './target-code-details.component';

describe('TargetCodeDetailsComponent', () => {
  let fixture: ComponentFixture<TargetCodeDetailsComponent>;
  let component: TargetCodeDetailsComponent;
  let selectionService: SelectionService;
  const mapService = {searchTargetCodes: vi.fn()};

  beforeEach(async () => {
    mapService.searchTargetCodes.mockReturnValue(of([
      {code: 'J45', display: 'Asthma', additionalColumns: [{name: 'Chapter', value: 'X'}]},
    ]));
    await TestBed.configureTestingModule({
      declarations: [TargetCodeDetailsComponent, DraggableDirective],
      imports: [MatCardModule, MatIconModule, MatProgressBarModule, TranslateModule.forRoot()],
      providers: [{provide: MapService, useValue: mapService}]
    }).compileComponents();
    selectionService = TestBed.inject(SelectionService);
    selectionService.select(null);
    fixture = TestBed.createComponent(TargetCodeDetailsComponent);
    component = fixture.componentInstance;
    const mapping = new Mapping();
    mapping.id = '5';
    mapping.direction = 'FROM_SNOMED';
    mapping.targetCodeSet = {...new Source(), id: '7', systemUri: 'http://hl7.org/fhir/sid/icd-10'} as any;
    component.mapping = mapping;
    fixture.detectChanges();
  });

  it('shows the selected target code with its additional columns', () => {
    selectionService.select({code: 'J45', system: 'http://hl7.org/fhir/sid/icd-10'});
    fixture.detectChanges();
    expect(mapService.searchTargetCodes).toHaveBeenCalledWith('5', {code: 'J45'});
    expect(fixture.nativeElement.textContent).toContain('Asthma');
    expect(fixture.nativeElement.textContent).toContain('Chapter');
  });

  it('ignores selections of other code systems', () => {
    mapService.searchTargetCodes.mockClear();
    selectionService.select({code: '195967001', system: 'http://snomed.info/sct'});
    expect(mapService.searchTargetCodes).not.toHaveBeenCalled();
    expect(component.code).toBeNull();
  });
});
