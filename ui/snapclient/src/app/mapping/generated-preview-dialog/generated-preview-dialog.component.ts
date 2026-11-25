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

import {Component, Inject, OnInit} from '@angular/core';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {PageEvent} from '@angular/material/paginator';
import {InheritanceRow, MapService} from '../../_services/map.service';

export interface GeneratedPreviewDialogData {
  mapId: string;
  total: number;
}

/**
 * Preview of the concepts inheritance generates at export time (descendants of mapped or stopping concepts
 * within the inheritance scope that are not rows of the map): nothing is stored until the export.
 */
@Component({
  selector: 'app-generated-preview-dialog',
  templateUrl: './generated-preview-dialog.component.html',
  styleUrls: ['./generated-preview-dialog.component.css'],
  standalone: false
})
export class GeneratedPreviewDialogComponent implements OnInit {

  readonly states = ['', 'INHERITED', 'CONFLICT', 'BLOCKED'];
  readonly pageSize = 100;

  state = '';
  pageIndex = 0;
  total = 0;
  rows: InheritanceRow[] = [];
  loading = false;
  failed = false;

  constructor(@Inject(MAT_DIALOG_DATA) public data: GeneratedPreviewDialogData, private mapService: MapService) {
  }

  ngOnInit(): void {
    this.load();
  }

  selectState(state: string): void {
    this.state = state;
    this.pageIndex = 0;
    this.load();
  }

  page(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.load();
  }

  targets(row: InheritanceRow): string {
    return row.targets.map(t => t.code + ' ' + t.display).join(', ');
  }

  private load(): void {
    this.loading = true;
    this.failed = false;
    this.mapService.getGeneratedConcepts(this.data.mapId, this.state || null, this.pageIndex, this.pageSize).subscribe({
      next: page => {
        this.total = page.total;
        this.rows = page.rows;
        this.loading = false;
      },
      error: () => {
        this.failed = true;
        this.loading = false;
      }
    });
  }
}
