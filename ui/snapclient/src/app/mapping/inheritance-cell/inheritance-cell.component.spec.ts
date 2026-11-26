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
import {MatMenuModule} from '@angular/material/menu';
import {MatIconModule} from '@angular/material/icon';
import {TranslateModule} from '@ngx-translate/core';
import {of} from 'rxjs';
import {InheritanceCellComponent} from './inheritance-cell.component';
import {InheritanceStateService} from '../../_services/inheritance-state.service';

describe('InheritanceCellComponent', () => {
  let fixture: ComponentFixture<InheritanceCellComponent>;
  const descendantsInfo = vi.fn();

  beforeEach(async () => {
    descendantsInfo.mockReset();
    await TestBed.configureTestingModule({
      declarations: [InheritanceCellComponent],
      imports: [MatMenuModule, MatIconModule, TranslateModule.forRoot()],
      providers: [{provide: InheritanceStateService, useValue: {descendantsInfo}}]
    }).compileComponents();
    fixture = TestBed.createComponent(InheritanceCellComponent);
  });

  it('summarises an inherited row with the ancestor code', () => {
    fixture.componentInstance.row = {rowId: 1, code: '389145006', display: 'Allergic asthma', state: 'INHERITED',
      stopInheritance: false, fromCode: '195967001', fromDisplay: 'Asthma', candidates: [], inheritedBy: 0,
      targets: [{code: 'J45.9', display: 'Asthma unspecified', relationship: 'TARGET_BROADER'}]};
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('195967001');
    expect(fixture.componentInstance.attention).toBe(false);
  });

  it('highlights conflicts and counts what a mapped row passes on', () => {
    fixture.componentInstance.row = {rowId: 1, code: 'A', display: 'A', state: 'CONFLICT', stopInheritance: false,
      fromCode: null, fromDisplay: null, candidates: ['B', 'C'], inheritedBy: 0, targets: []};
    expect(fixture.componentInstance.attention).toBe(true);
    fixture.componentInstance.row = {rowId: 1, code: 'A', display: 'A', state: 'ASSERTED', stopInheritance: false,
      fromCode: null, fromDisplay: null, candidates: [], inheritedBy: 3, generatedBy: 40, targets: []};
    expect(fixture.componentInstance.passesOn).toBe(43);
  });

  it('renders nothing without a row', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe('');
  });

  it('tells pending, accepted and changed inheritance apart', () => {
    const base = {rowId: 1, code: 'A', display: 'A', state: 'INHERITED' as const, stopInheritance: false, fromCode: 'R',
      fromDisplay: 'Root', candidates: [], inheritedBy: 0, targets: []};
    fixture.componentInstance.row = {...base};
    expect(fixture.componentInstance.pending).toBe(true);
    fixture.componentInstance.row = {...base, accepted: true};
    expect(fixture.componentInstance.pending).toBe(false);
    expect(fixture.componentInstance.attention).toBe(false);
    fixture.componentInstance.row = {...base, accepted: true, inheritanceChanged: true};
    expect(fixture.componentInstance.attention).toBe(true);
  });
  it('counts the descendants of a row when asked, once, and not for generated concepts', () => {
    const info = {code: 'A', tooLarge: false, descendants: 12, inSource: 12, beyondSource: 0, wouldInherit: 9,
      wouldGenerate: 0, ownMapping: 2, closerMapping: 0, stopped: 1, conflict: 0};
    descendantsInfo.mockReturnValue(of(info));
    const cell = fixture.componentInstance;
    cell.row = {rowId: 1, code: 'A', display: 'A', state: 'UNCOVERED', stopInheritance: false, fromCode: null,
      fromDisplay: null, candidates: [], inheritedBy: 0, targets: []};
    cell.loadDescendants();
    expect(descendantsInfo).toHaveBeenCalledWith('A');
    expect(cell.descendants).toEqual(info);
    expect(cell.mapped).toBe(false);

    descendantsInfo.mockClear();
    cell.countDescendants = false;
    cell.loadDescendants();
    expect(descendantsInfo).not.toHaveBeenCalled();
  });
});
