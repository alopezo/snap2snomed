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
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatRadioModule} from '@angular/material/radio';
import {MatInputModule} from '@angular/material/input';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslateModule} from '@ngx-translate/core';
import {of, throwError} from 'rxjs';
import {InvertMapDialogComponent} from './invert-map-dialog.component';
import {MapService} from '../../_services/map.service';

describe('InvertMapDialogComponent', () => {
  const result = {projectId: 7, mapId: 12, concepts: 12, targets: 12, refinedTargets: 2, inactiveConcepts: 1, noMapRows: 0};
  let mapService: {invertMap: ReturnType<typeof vi.fn>};
  let dialogRef: {close: ReturnType<typeof vi.fn>};

  async function create(): Promise<ComponentFixture<InvertMapDialogComponent>> {
    mapService = {invertMap: vi.fn()};
    dialogRef = {close: vi.fn()};
    await TestBed.configureTestingModule({
      declarations: [InvertMapDialogComponent],
      imports: [MatDialogModule, FormsModule, MatCheckboxModule, MatRadioModule, MatInputModule, NoopAnimationsModule,
        TranslateModule.forRoot()],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: {mapId: '3', projectTitle: 'Emergency'}},
        {provide: MatDialogRef, useValue: dialogRef},
        {provide: MapService, useValue: mapService}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();
    const fixture = TestBed.createComponent(InvertMapDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('proposes a title and sends the inheritance chosen', async () => {
    const fixture = await create();
    const component = fixture.componentInstance;
    expect(component.title).toBe('Emergency (from SNOMED CT)');
    mapService.invertMap.mockReturnValue(of(result));
    component.inheritanceMode = 'WITHIN_SOURCE';
    component.create();
    expect(mapService.invertMap).toHaveBeenCalledWith('3', {title: 'Emergency (from SNOMED CT)', description: null,
      inheritance: true, inheritanceMode: 'WITHIN_SOURCE'});
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#invertResult').textContent).toContain('INVERT_MAP.RESULT_INACTIVE');
  });

  it('closes asking the list to open the new map', async () => {
    const fixture = await create();
    mapService.invertMap.mockReturnValue(of(result));
    fixture.componentInstance.create();
    fixture.componentInstance.openNewMap();
    expect(dialogRef.close).toHaveBeenCalledWith({...result, open: true});
  });

  it('shows the error and stays open when the map cannot be created', async () => {
    const fixture = await create();
    mapService.invertMap.mockReturnValue(throwError(() => ({error: {detail: 'A dual author map cannot be inverted yet'}})));
    fixture.componentInstance.create();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#invertError').textContent).toContain('A dual author map cannot be inverted yet');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
