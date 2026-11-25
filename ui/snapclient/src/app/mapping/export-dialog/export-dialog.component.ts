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

export type ExportFormat = 'csv' | 'tsv' | 'xlsx' | 'fhir-json';
/** How refined targets (expressions) are written; see TargetExportForm in the API. */
export type TargetForm = 'expression' | 'focus' | 'both';

export interface ExportOptions {
  format: ExportFormat;
  /** Adds notes, authors and reviewers (tabular formats only). */
  extended: boolean;
  targetForm: TargetForm;
}

export interface ExportDialogData {
  /** Targets of the map written as expressions; the refined target choice is shown only when there are some. */
  refinedTargetCount: number;
}

export const EXPORT_EXTRA_COLUMNS = ['notes', 'lastAuthor', 'lastReviewer', 'assignedAuthor', 'assignedReviewer'];

/** Where the last export choice is kept, in this browser only. */
export const EXPORT_OPTIONS_STORAGE_KEY = 'snap2snomed.exportOptions';

const DEFAULT_OPTIONS: ExportOptions = {format: 'csv', extended: false, targetForm: 'expression'};

@Component({
  selector: 'app-export-dialog',
  templateUrl: './export-dialog.component.html',
  styleUrls: ['./export-dialog.component.css'],
  standalone: false
})
export class ExportDialogComponent {

  readonly formats: {value: ExportFormat, labelKey: string}[] = [
    {value: 'csv', labelKey: 'MAP.EXPORT_CSV'},
    {value: 'tsv', labelKey: 'MAP.EXPORT_TSV'},
    {value: 'xlsx', labelKey: 'MAP.EXPORT_XLSX'},
    {value: 'fhir-json', labelKey: 'MAP.EXPORT_FHIR_JSON'},
  ];

  options: ExportOptions;

  constructor(@Inject(MAT_DIALOG_DATA) public data: ExportDialogData,
              private dialogRef: MatDialogRef<ExportDialogComponent, ExportOptions>) {
    this.options = ExportDialogComponent.readStored();
  }

  get tabular(): boolean {
    return this.options.format !== 'fhir-json';
  }

  get hasRefinedTargets(): boolean {
    return this.data.refinedTargetCount > 0;
  }

  /** The options actually sent: choices that do not apply to the format are dropped. */
  get effectiveOptions(): ExportOptions {
    const targetForm = !this.hasRefinedTargets ? 'expression'
      : (!this.tabular && this.options.targetForm === 'both') ? 'expression' : this.options.targetForm;
    return {format: this.options.format, extended: this.tabular && this.options.extended, targetForm};
  }

  export(): void {
    try {
      localStorage.setItem(EXPORT_OPTIONS_STORAGE_KEY, JSON.stringify(this.options));
    } catch (e) {
      // Storage may be unavailable; the choice is then not remembered.
    }
    this.dialogRef.close(this.effectiveOptions);
  }

  static readStored(): ExportOptions {
    try {
      const stored = JSON.parse(localStorage.getItem(EXPORT_OPTIONS_STORAGE_KEY) ?? 'null');
      if (stored && ['csv', 'tsv', 'xlsx', 'fhir-json'].includes(stored.format)
        && ['expression', 'focus', 'both'].includes(stored.targetForm)) {
        return {format: stored.format, extended: !!stored.extended, targetForm: stored.targetForm};
      }
    } catch (e) {
      // Unreadable or unavailable storage: use the defaults
    }
    return {...DEFAULT_OPTIONS};
  }
}
