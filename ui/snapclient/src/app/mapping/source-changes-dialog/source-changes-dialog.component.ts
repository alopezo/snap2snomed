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

import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {SourceChanges} from '../../_services/map.service';

/**
 * Shown after a new version of a map from SNOMED CT: how many source concepts kept their rows, how many
 * are new (unmapped) and which are gone from the ECL result, so nothing disappears unnoticed.
 */
@Component({
  selector: 'app-source-changes-dialog',
  templateUrl: './source-changes-dialog.component.html',
  styleUrls: ['./source-changes-dialog.component.css'],
  standalone: false
})
export class SourceChangesDialogComponent {

  copied = false;

  constructor(@Inject(MAT_DIALOG_DATA) public data: SourceChanges) {
  }

  /** The removed concepts as tab separated lines (code, term), for pasting into a spreadsheet. */
  get removedText(): string {
    return this.data.removed.map(c => `${c.code}\t${c.display}`).join('\n');
  }
}
