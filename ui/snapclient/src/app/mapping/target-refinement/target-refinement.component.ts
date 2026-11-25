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

import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {Subject, Subscription, of} from 'rxjs';
import {catchError, switchMap} from 'rxjs/operators';
import {FhirService, RefinementOptions} from '../../_services/fhir.service';
import {SelectionService} from '../../_services/selection.service';
import {
  LATERALITY_VALUES, SEVERITY_VALUES, focusOf, isTargetExpression, toTargetExpressionCode
} from '../../_utils/target-expression';

const NO_OPTIONS: RefinementOptions = {laterality: false, severity: false};

/**
 * Refines the selected target concept with laterality and/or severity. The result is a close-to-user
 * expression (see target-expression.ts) that is dragged onto a relationship like any other target.
 */
@Component({
  selector: 'app-target-refinement',
  templateUrl: './target-refinement.component.html',
  styleUrls: ['./target-refinement.component.css'],
  standalone: false
})
export class TargetRefinementComponent implements OnInit, OnDestroy {
  @Input() version = '';
  @Input() displayLanguage: string | null | undefined = null;
  @Input() disabled = false;

  readonly lateralityValues = LATERALITY_VALUES;
  readonly severityValues = SEVERITY_VALUES;
  readonly dragZones = ['relationshipZone'];

  focus = '';
  focusDisplay = '';
  system = 'http://snomed.info/sct';
  options: RefinementOptions = NO_OPTIONS;
  loadingOptions = false;
  optionsFailed = false;

  laterality?: string;
  severity?: string;
  previewDisplay = '';
  loadingPreview = false;

  private readonly preview$ = new Subject<string | null>();
  private readonly subscription = new Subscription();

  constructor(private selectionService: SelectionService, private fhirService: FhirService) {
  }

  ngOnInit(): void {
    this.subscription.add(this.selectionService.subscribeWithCurrent({
      next: (selection: any) => this.select(selection)
    }));
    this.subscription.add(this.preview$.pipe(
      switchMap(code => code
        ? this.fhirService.getTargetDisplay(code, this.system, this.version, this.displayLanguage).pipe(catchError(() => of('')))
        : of(''))
    ).subscribe(display => {
      this.previewDisplay = display;
      this.loadingPreview = false;
    }));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /** The expression for the chosen refinements, or null when nothing is chosen. */
  get expression(): string | null {
    if (!this.focus) {
      return null;
    }
    return toTargetExpressionCode({
      focus: this.focus,
      laterality: this.options.laterality ? this.laterality : undefined,
      severity: this.options.severity ? this.severity : undefined,
    });
  }

  get canDrag(): boolean {
    return !this.disabled && !!this.expression && !!this.previewDisplay && !this.loadingPreview;
  }

  get dragData(): {code: string, display: string, system: string} | null {
    const code = this.expression;
    return code ? {code, display: this.previewDisplay, system: this.system} : null;
  }

  setLaterality(code: string | undefined): void {
    this.laterality = code || undefined;
    this.updatePreview();
  }

  setSeverity(code: string | undefined): void {
    this.severity = code || undefined;
    this.updatePreview();
  }

  private select(selection: any): void {
    const code: string | undefined = selection?.code;
    this.laterality = undefined;
    this.severity = undefined;
    this.previewDisplay = '';
    this.optionsFailed = false;
    this.options = NO_OPTIONS;
    if (!code || (selection.system && selection.system !== this.system)) {
      this.focus = '';
      this.focusDisplay = '';
      return;
    }
    this.focus = isTargetExpression(code) ? focusOf(code) : code;
    this.focusDisplay = isTargetExpression(code) ? '' : selection.display ?? '';
    this.loadingOptions = true;
    const focus = this.focus;
    // The term as it will appear in the map (the map's display language)
    this.fhirService.getTargetDisplay(focus, this.system, this.version, this.displayLanguage)
      .pipe(catchError(() => of('')))
      .subscribe(display => {
        if (focus === this.focus && display) {
          this.focusDisplay = display;
        }
      });
    this.fhirService.getRefinementOptions(focus, this.version).subscribe({
      next: options => {
        if (focus === this.focus) {
          this.options = options;
          this.loadingOptions = false;
        }
      },
      error: () => {
        if (focus === this.focus) {
          this.optionsFailed = true;
          this.loadingOptions = false;
        }
      }
    });
  }

  private updatePreview(): void {
    const code = this.expression;
    this.loadingPreview = !!code;
    this.previewDisplay = '';
    this.preview$.next(code);
  }
}
