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

import {Component, Input, OnChanges, OnDestroy, SimpleChanges, forwardRef} from '@angular/core';
import {
  AbstractControl, ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormControl, ValidationErrors,
  Validator
} from '@angular/forms';
import {MatDialog} from '@angular/material/dialog';
import {Subscription} from 'rxjs';
import {LanguageContext, LanguageMetadataService} from '../../_services/language-metadata.service';
import {FhirService} from '../../_services/fhir.service';
import {LanguageDialects} from '../../_utils/language-dialects';
import {DisplayLanguageTestDialogComponent, DisplayLanguageTestDialogData} from '../display-language-test-dialog/display-language-test-dialog.component';

/** Value of the "Custom sequence" option; never stored. */
export const CUSTOM_DISPLAY_LANGUAGE = '__custom__';

/**
 * Picks a map's display language: English (FSN), one of the edition's named contexts from the
 * language metadata, or a custom dialect sequence typed by the user (with help to build it from the
 * edition's language reference sets). The form value is the normalized sequence, or null for the
 * English FSN. A malformed custom sequence makes the control invalid.
 */
@Component({
  selector: 'app-display-language-picker',
  templateUrl: './display-language-picker.component.html',
  styleUrls: ['./display-language-picker.component.css'],
  standalone: false,
  providers: [
    {provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DisplayLanguagePickerComponent), multi: true},
    {provide: NG_VALIDATORS, useExisting: forwardRef(() => DisplayLanguagePickerComponent), multi: true},
  ]
})
export class DisplayLanguagePickerComponent implements ControlValueAccessor, Validator, OnChanges, OnDestroy {

  readonly CUSTOM = CUSTOM_DISPLAY_LANGUAGE;

  /** SNOMED CT version URI of the map; contexts and refsets depend on its edition. */
  @Input() versionUri: string | null = null;
  /** Target scope (ECL) of the map, used by the concept search of the test dialog. */
  @Input() scope: string | null = null;
  @Input() label = '';
  @Input() hint = '';

  contexts: LanguageContext[] = [];
  refsets: {code: string, display: string}[] = [];
  refsetsLoading = false;
  selection = '';
  disabled = false;

  customControl = new UntypedFormControl('');
  builderLanguage = new UntypedFormControl('');
  builderRefset = new UntypedFormControl('');

  private value: string | null = null;
  private onChange: (value: string | null) => void = () => {};
  private onTouched: () => void = () => {};
  private onValidatorChange: () => void = () => {};
  private subscription = new Subscription();

  constructor(private languageMetadataService: LanguageMetadataService,
              private fhirService: FhirService,
              private dialog: MatDialog) {
    this.subscription.add(this.customControl.valueChanges.subscribe(text => {
      if (this.selection === CUSTOM_DISPLAY_LANGUAGE) {
        this.emit(LanguageDialects.normalize(text));
      }
    }));
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.versionUri) {
      this.refsets = [];
      this.languageMetadataService.contextsForVersion(this.versionUri).subscribe(contexts => {
        this.contexts = contexts;
        this.syncSelection();
        if (this.selection === CUSTOM_DISPLAY_LANGUAGE) {
          this.loadRefsets();
        }
      });
    }
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  writeValue(value: string | null): void {
    this.value = LanguageDialects.normalize(value);
    this.syncSelection();
  }

  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  registerOnValidatorChange(fn: () => void): void {
    this.onValidatorChange = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    isDisabled ? this.customControl.disable({emitEvent: false}) : this.customControl.enable({emitEvent: false});
  }

  validate(_: AbstractControl): ValidationErrors | null {
    if (this.selection !== CUSTOM_DISPLAY_LANGUAGE) {
      return null;
    }
    const text = this.customControl.value;
    if (!LanguageDialects.normalize(text)) {
      return {displayLanguageRequired: true};
    }
    return LanguageDialects.isValid(text) ? null : {displayLanguageFormat: true};
  }

  get customEmpty(): boolean {
    return this.selection === CUSTOM_DISPLAY_LANGUAGE && !LanguageDialects.normalize(this.customControl.value);
  }

  get customInvalid(): boolean {
    return this.selection === CUSTOM_DISPLAY_LANGUAGE && !!this.validate(this.customControl);
  }

  onSelect(selection: string): void {
    this.selection = selection;
    this.onTouched();
    if (selection === CUSTOM_DISPLAY_LANGUAGE) {
      // Start from the current value so a context can be tweaked
      this.customControl.setValue(this.value ?? '', {emitEvent: false});
      this.loadRefsets();
      this.emit(LanguageDialects.normalize(this.customControl.value));
    } else {
      this.emit(LanguageDialects.normalize(selection));
    }
    this.onValidatorChange();
  }

  /** Appends "<language>-X-<refset>" to the custom sequence. */
  addEntry(): void {
    const language = (this.builderLanguage.value ?? '').trim().toLowerCase();
    const refset = this.builderRefset.value;
    if (!/^[a-z]{2,3}$/.test(language)) {
      return;
    }
    const entry = refset ? `${language}-X-${refset}` : language;
    const current = LanguageDialects.normalize(this.customControl.value);
    this.customControl.setValue(current ? `${current},${entry}` : entry);
    this.builderRefset.setValue('');
  }

  get canAddEntry(): boolean {
    return /^[a-z]{2,3}$/i.test((this.builderLanguage.value ?? '').trim());
  }

  get canTest(): boolean {
    return !!this.versionUri && !this.customInvalid;
  }

  openTest(): void {
    if (!this.versionUri) {
      return;
    }
    const data: DisplayLanguageTestDialogData = {versionUri: this.versionUri, scope: this.scope || '*', displayLanguage: this.value};
    this.dialog.open(DisplayLanguageTestDialogComponent, {width: '760px', maxWidth: '95vw', data});
  }

  private loadRefsets(): void {
    if (!this.versionUri || this.refsets.length > 0 || this.refsetsLoading) {
      return;
    }
    this.refsetsLoading = true;
    this.fhirService.languageRefsets(this.versionUri).subscribe(refsets => {
      this.refsets = refsets;
      this.refsetsLoading = false;
    });
  }

  /** Shows the stored value as a context when it matches one, as a custom sequence otherwise. */
  private syncSelection(): void {
    if (!this.value) {
      if (this.selection !== CUSTOM_DISPLAY_LANGUAGE) {
        this.selection = '';
      }
      return;
    }
    if (this.contexts.some(context => context.languageDialects === this.value)) {
      this.selection = this.value;
    } else {
      this.selection = CUSTOM_DISPLAY_LANGUAGE;
      if (LanguageDialects.normalize(this.customControl.value) !== this.value) {
        this.customControl.setValue(this.value, {emitEvent: false});
      }
    }
  }

  private emit(value: string | null): void {
    this.value = value;
    this.onChange(value);
  }
}
