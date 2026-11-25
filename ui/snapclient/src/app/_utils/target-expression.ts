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

/**
 * A map target refined with laterality and/or severity, kept as a close-to-user SNOMED CT expression
 * without terms, for example 71620000:272741003=7771000,246112005=24484000.
 *
 * Only the refinements offered in the interface are accepted, and the canonical form always lists
 * laterality before severity, so the same refinement always gives the same code.
 * Same rules as TargetExpression.java in the API: keep both in sync.
 */

export const LATERALITY = '272741003';
export const SEVERITY = '246112005';

/** A refinement value offered in the interface; labelKey is its translation key. */
export interface RefinementValue {
  code: string;
  labelKey: string;
}

export const LATERALITY_VALUES: RefinementValue[] = [
  {code: '7771000', labelKey: 'REFINEMENT.LEFT'},
  {code: '24028007', labelKey: 'REFINEMENT.RIGHT'},
  {code: '51440002', labelKey: 'REFINEMENT.RIGHT_AND_LEFT'},
];

export const SEVERITY_VALUES: RefinementValue[] = [
  {code: '255604002', labelKey: 'REFINEMENT.MILD'},
  {code: '6736007', labelKey: 'REFINEMENT.MODERATE'},
  {code: '24484000', labelKey: 'REFINEMENT.SEVERE'},
];

/** Allowed values of each refinement attribute, in canonical attribute order. */
const ATTRIBUTE_VALUES: [string, string[]][] = [
  [LATERALITY, LATERALITY_VALUES.map(v => v.code)],
  [SEVERITY, SEVERITY_VALUES.map(v => v.code)],
];

/** Between the focus term and each value term in the display of a refined target. */
export const DISPLAY_SEPARATOR = ' - ';

const EXPRESSION = /^(\d{6,18}):(\d{6,18}=\d{6,18}(?:,\d{6,18}=\d{6,18})*)$/;

export interface TargetExpression {
  focus: string;
  laterality?: string;
  severity?: string;
}

/** Builds the canonical code; returns null when there is no refinement or a value is not offered. */
export function toTargetExpressionCode(expression: TargetExpression): string | null {
  const refinements: Record<string, string | undefined> = {
    [LATERALITY]: expression.laterality,
    [SEVERITY]: expression.severity,
  };
  const parts: string[] = [];
  for (const [attribute, allowed] of ATTRIBUTE_VALUES) {
    const value = refinements[attribute];
    if (value) {
      if (!allowed.includes(value)) {
        return null;
      }
      parts.push(`${attribute}=${value}`);
    }
  }
  return parts.length ? `${expression.focus}:${parts.join(',')}` : null;
}

/**
 * Reads a target code written as an expression. Terms between pipes and white space are ignored.
 * Returns null for a plain concept id and for anything that is not a supported refinement.
 */
export function parseTargetExpression(code: string | null | undefined): TargetExpression | null {
  if (!code) {
    return null;
  }
  const bare = code.replace(/\|[^|]*\|/g, '').replace(/\s+/g, '');
  const match = EXPRESSION.exec(bare);
  if (!match) {
    return null;
  }
  const refinements: Record<string, string> = {};
  for (const pair of match[2].split(',')) {
    const [attribute, value] = pair.split('=');
    if (refinements[attribute] !== undefined) {
      return null; // the same attribute twice
    }
    refinements[attribute] = value;
  }
  if (Object.keys(refinements).some(attribute => !ATTRIBUTE_VALUES.some(([a]) => a === attribute))) {
    return null;
  }
  const expression: TargetExpression = {
    focus: match[1],
    laterality: refinements[LATERALITY],
    severity: refinements[SEVERITY],
  };
  return toTargetExpressionCode(expression) ? expression : null;
}

/** True when the code is a supported refined target. */
export function isTargetExpression(code: string | null | undefined): boolean {
  return parseTargetExpression(code) !== null;
}

/** The focus concept of an expression, or the code itself when it is not an expression. */
export function focusOf(code: string): string;
export function focusOf(code: string | null | undefined): string | null | undefined;
export function focusOf(code: string | null | undefined): string | null | undefined {
  return parseTargetExpression(code)?.focus ?? code;
}

/** The refinement values of an expression, in canonical order (laterality, then severity). */
export function valuesOf(expression: TargetExpression): string[] {
  return [expression.laterality, expression.severity].filter((v): v is string => !!v);
}

/** The display of a refined target: the focus term, then each value term, joined by " - ". */
export function composeDisplay(focusDisplay: string, valueDisplays: string[]): string {
  return [focusDisplay, ...valueDisplays].join(DISPLAY_SEPARATOR);
}

/** A term without its trailing semantic tag, e.g. "Left (qualifier value)" gives "Left". */
export function withoutSemanticTag(display: string): string {
  return display.replace(/\s*\([^()]*\)$/, '');
}
