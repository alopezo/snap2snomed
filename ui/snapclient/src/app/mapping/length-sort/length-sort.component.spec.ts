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
import {MatSort, MatSortModule, Sort} from '@angular/material/sort';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {LengthSortComponent} from './length-sort.component';

describe('LengthSortComponent', () => {
  let fixture: ComponentFixture<LengthSortComponent>;
  let sort: MatSort;
  let emitted: Sort[];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [LengthSortComponent],
      imports: [MatSortModule, MatIconModule, MatTooltipModule, TranslateModule.forRoot()]
    }).compileComponents();
    fixture = TestBed.createComponent(LengthSortComponent);
    sort = new MatSort();
    emitted = [];
    sort.sortChange.subscribe(e => emitted.push(e));
    fixture.componentInstance.sort = sort;
    fixture.detectChanges();
  });

  function click(): void {
    fixture.nativeElement.querySelector('#lengthSort').click();
    fixture.detectChanges();
  }

  it('cycles shortest first, longest first and unsorted through the table sort', () => {
    click();
    expect(emitted.pop()).toEqual({active: 'sourceDisplayLength', direction: 'asc'});
    expect(fixture.componentInstance.direction).toBe('asc');
    click();
    expect(emitted.pop()).toEqual({active: 'sourceDisplayLength', direction: 'desc'});
    click();
    expect(emitted.pop()).toEqual({active: 'sourceDisplayLength', direction: ''});
    expect(fixture.componentInstance.direction).toBeNull();
  });

  it('is not active while the table is sorted by another column', () => {
    sort.active = 'sourceDisplay';
    sort.direction = 'asc';
    fixture.detectChanges();
    expect(fixture.componentInstance.direction).toBeNull();
    expect(fixture.nativeElement.querySelector('.direction-icon')).toBeNull();
    click();
    expect(emitted.pop()).toEqual({active: 'sourceDisplayLength', direction: 'asc'});
  });
});
