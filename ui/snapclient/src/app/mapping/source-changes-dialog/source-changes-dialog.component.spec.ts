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
import {MAT_DIALOG_DATA, MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {ClipboardModule} from '@angular/cdk/clipboard';
import {TranslateModule} from '@ngx-translate/core';
import {SourceChangesDialogComponent} from './source-changes-dialog.component';

describe('SourceChangesDialogComponent', () => {
  async function create(data: any): Promise<ComponentFixture<SourceChangesDialogComponent>> {
    await TestBed.configureTestingModule({
      declarations: [SourceChangesDialogComponent],
      imports: [MatDialogModule, MatIconModule, MatButtonModule, ClipboardModule, TranslateModule.forRoot()],
      providers: [{provide: MAT_DIALOG_DATA, useValue: data}]
    }).compileComponents();
    const fixture = TestBed.createComponent(SourceChangesDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the removed concepts and offers to copy them', async () => {
    const fixture = await create({keptCount: 120, addedCount: 3,
      removed: [{code: '1', display: 'Gone asthma'}, {code: '2', display: 'Other'}]});
    expect(fixture.nativeElement.querySelectorAll('.removed tr').length).toBe(2);
    expect(fixture.componentInstance.removedText).toBe('1\tGone asthma\n2\tOther');
    expect(fixture.nativeElement.textContent).toContain('SOURCE_CHANGES.COPY_REMOVED');
  });

  it('shows only the counts when nothing was removed', async () => {
    const fixture = await create({keptCount: 128, addedCount: 0, removed: []});
    expect(fixture.nativeElement.querySelector('.removed')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('SOURCE_CHANGES.COPY_REMOVED');
  });
});
