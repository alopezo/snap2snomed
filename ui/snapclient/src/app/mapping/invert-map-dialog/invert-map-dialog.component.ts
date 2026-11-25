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
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {InheritanceMode} from '../../_models/mapping';
import {InvertMapResult, MapService} from '../../_services/map.service';

export interface InvertMapDialogData {
  mapId: string;
  projectTitle: string;
}

/**
 * Creates a map from SNOMED CT from a map to SNOMED CT, in a new project: the concepts it targets become the
 * source, mapped back to the local codes. Then shows what was carried over and opens the new map.
 */
@Component({
  selector: 'app-invert-map-dialog',
  templateUrl: './invert-map-dialog.component.html',
  styleUrls: ['./invert-map-dialog.component.css'],
  standalone: false
})
export class InvertMapDialogComponent {

  title: string;
  description = '';
  inheritance = true;
  inheritanceMode: InheritanceMode = 'BEYOND_SOURCE';
  creating = false;
  error: string | null = null;
  result: InvertMapResult | null = null;

  constructor(@Inject(MAT_DIALOG_DATA) public data: InvertMapDialogData,
              private dialogRef: MatDialogRef<InvertMapDialogComponent>,
              private mapService: MapService) {
    this.title = `${data.projectTitle} (from SNOMED CT)`.substring(0, 100);
  }

  create(): void {
    this.creating = true;
    this.error = null;
    this.mapService.invertMap(this.data.mapId, {
      title: this.title.trim(),
      description: this.description.trim() || null,
      inheritance: this.inheritance,
      inheritanceMode: this.inheritanceMode
    }).subscribe({
      next: result => {
        this.creating = false;
        this.result = result;
      },
      error: err => {
        this.creating = false;
        this.error = err?.error?.detail ?? err?.message ?? '';
      }
    });
  }

  openNewMap(): void {
    if (this.result) {
      // the list opens it once the new project is loaded: the route guard checks the map against them
      this.dialogRef.close({...this.result, open: true});
    }
  }
}
