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

import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {UntypedFormControl} from '@angular/forms';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {Subscription} from 'rxjs';
import {debounceTime, distinctUntilChanged, filter} from 'rxjs/operators';
import {DisplayLanguagePreview, FhirService} from '../../_services/fhir.service';
import {SelectionService} from '../../_services/selection.service';
import {LanguageDialects} from '../../_utils/language-dialects';

export interface DisplayLanguageTestDialogData {
  versionUri: string;
  /** ECL scope for the concept search: the map's target scope (searching '*' in a large edition is slow). */
  scope: string;
  /** Dialect sequence to try; null for the English FSN behaviour. */
  displayLanguage: string | null;
}

/** Hypertensive disorder: translated in practically every extension. */
export const DEFAULT_TEST_CONCEPT = '38341003';

/**
 * Shows what a display language returns for a concept before it is saved: the term that would be
 * stored, the English term for comparison, and every designation of the concept. The concept can be
 * typed or picked with the concept search (which already searches in the language being tried).
 */
@Component({
  selector: 'app-display-language-test-dialog',
  templateUrl: './display-language-test-dialog.component.html',
  styleUrls: ['./display-language-test-dialog.component.css'],
  standalone: false
})
export class DisplayLanguageTestDialogComponent implements OnInit, OnDestroy {

  codeControl = new UntypedFormControl(DEFAULT_TEST_CONCEPT);
  preview: DisplayLanguagePreview | null = null;
  loading = false;
  failed = false;
  private subscription = new Subscription();

  constructor(@Inject(MAT_DIALOG_DATA) public data: DisplayLanguageTestDialogData,
              private fhirService: FhirService,
              private selectionService: SelectionService) {
  }

  ngOnInit(): void {
    this.subscription.add(this.codeControl.valueChanges.pipe(
      debounceTime(400),
      distinctUntilChanged()
    ).subscribe(code => this.run(code)));
    // Picking a concept in the search fills the code
    this.subscription.add(this.selectionService.subscribe({
      next: (selection: any) => {
        if (selection?.code && selection.code !== this.codeControl.value) {
          this.codeControl.setValue(selection.code);
        }
      }
    }));
    this.run(this.codeControl.value);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /** The sequence probably matched nothing: the term equals the English one for a non-English sequence. */
  get looksLikeFallback(): boolean {
    const language = LanguageDialects.primaryLanguage(this.data.displayLanguage);
    return !!this.preview && !!language && language !== 'en' && this.preview.display === this.preview.englishDisplay;
  }

  run(code: string): void {
    const trimmed = (code ?? '').trim();
    if (!/^\d{6,18}$/.test(trimmed)) {
      this.preview = null;
      return;
    }
    this.loading = true;
    this.failed = false;
    this.fhirService.previewDisplayLanguage(trimmed, this.data.versionUri, this.data.displayLanguage).subscribe({
      next: preview => {
        this.preview = preview;
        this.loading = false;
      },
      error: () => {
        this.preview = null;
        this.failed = true;
        this.loading = false;
      }
    });
  }
}
