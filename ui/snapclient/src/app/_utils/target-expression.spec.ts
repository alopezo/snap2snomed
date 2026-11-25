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

import {
  composeDisplay, focusOf, isTargetExpression, parseTargetExpression, toTargetExpressionCode, withoutSemanticTag
} from './target-expression';

// Same cases as TargetExpressionTest.java in the API: keep both in sync
describe('target-expression', () => {

  it('builds the canonical code with laterality before severity', () => {
    expect(toTargetExpressionCode({focus: '71620000', laterality: '7771000', severity: '24484000'}))
      .toBe('71620000:272741003=7771000,246112005=24484000');
    expect(toTargetExpressionCode({focus: '71620000', severity: '24484000'})).toBe('71620000:246112005=24484000');
    expect(toTargetExpressionCode({focus: '71620000', laterality: '24028007'})).toBe('71620000:272741003=24028007');
  });

  it('refuses no refinement or unsupported values', () => {
    expect(toTargetExpressionCode({focus: '71620000'})).toBeNull();
    expect(toTargetExpressionCode({focus: '71620000', laterality: '24484000'})).toBeNull();
  });

  it('parses ignoring terms, spaces and order', () => {
    const cases: [string, string][] = [
      ['71620000:272741003=7771000', '71620000:272741003=7771000'],
      ['71620000:246112005=24484000,272741003=7771000', '71620000:272741003=7771000,246112005=24484000'],
      ['71620000 |Fracture of femur| : 272741003 |Laterality| = 7771000 |Left|', '71620000:272741003=7771000'],
    ];
    for (const [code, expected] of cases) {
      expect(toTargetExpressionCode(parseTargetExpression(code)!)).toBe(expected);
    }
  });

  it('does not parse concepts or unsupported refinements', () => {
    for (const code of [null, '', '71620000', '71620000:363698007=71341001', '71620000:272741003=24484000',
      '71620000:272741003=7771000,272741003=24028007', '71620000:', 'abc:272741003=7771000', '71620000:{272741003=51440002}']) {
      expect(parseTargetExpression(code)).toBeNull();
      expect(isTargetExpression(code)).toBe(false);
    }
  });

  it('returns the focus or the code itself', () => {
    expect(focusOf('71620000:272741003=7771000')).toBe('71620000');
    expect(focusOf('71620000')).toBe('71620000');
  });

  it('composes the display and removes semantic tags', () => {
    expect(composeDisplay('Fracture of femur', ['Left', 'Severe'])).toBe('Fracture of femur - Left - Severe');
    expect(withoutSemanticTag('Severe (severity modifier) (qualifier value)')).toBe('Severe (severity modifier)');
    expect(withoutSemanticTag('izquierdo')).toBe('izquierdo');
  });
});
