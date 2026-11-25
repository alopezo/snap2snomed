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

import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {Mapping} from '../_models/mapping';
import {InheritanceStateService} from './inheritance-state.service';
import {MapService} from './map.service';
import {TargetChangedService} from './target-changed.service';

describe('InheritanceStateService', () => {
  const mapService = {getInheritance: vi.fn()};
  let service: InheritanceStateService;

  function mapping(inheritance: boolean, dual = false): Mapping {
    const m = new Mapping();
    m.id = '5';
    m.direction = 'FROM_SNOMED';
    m.inheritance = inheritance;
    m.project.dualMapMode = dual;
    return m;
  }

  beforeEach(() => {
    mapService.getInheritance.mockReset().mockReturnValue(of({
      counts: {ASSERTED: 1, INHERITED: 1},
      rows: [{rowId: 1, code: '195967001', state: 'ASSERTED', inheritedBy: 1, targets: [], candidates: []},
        {rowId: 2, code: '389145006', state: 'INHERITED', fromCode: '195967001', targets: [{code: 'J45.9'}], candidates: []}]
    }));
    TestBed.configureTestingModule({providers: [{provide: MapService, useValue: mapService}]});
    service = TestBed.inject(InheritanceStateService);
  });

  it('loads the inheritance of a map that has it and finds rows by code', () => {
    service.watch(mapping(true));
    expect(mapService.getInheritance).toHaveBeenCalledWith('5');
    expect(service.rowFor('389145006')?.fromCode).toBe('195967001');
    service.watch(mapping(true));
    expect(mapService.getInheritance).toHaveBeenCalledTimes(1);
  });

  it('ignores maps without inheritance, and dual author maps', () => {
    service.watch(mapping(false));
    service.watch(mapping(true, true));
    expect(mapService.getInheritance).not.toHaveBeenCalled();
    expect(service.rowFor('389145006')).toBeUndefined();
  });

  it('recomputes after a target change', async () => {
    service.watch(mapping(true));
    TestBed.inject(TargetChangedService).changeTarget({});
    await new Promise(resolve => setTimeout(resolve, 350));
    expect(mapService.getInheritance).toHaveBeenCalledTimes(2);
  });
});
