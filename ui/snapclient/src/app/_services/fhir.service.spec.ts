/*
 * Copyright © 2022 SNOMED International
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

import {FhirService} from './fhir.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import {provideMockStore} from '@ngrx/store/testing';
import {initialAppState} from '../store/app.state';
import { APP_CONFIG } from '../app.config';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

describe('FhirService', () => {
  let service: FhirService;

  const initialState = initialAppState;

  beforeEach(() => {
    TestBed.configureTestingModule({
    imports: [],
    providers: [
        provideMockStore({ initialState }),
        FhirService,
        { provide: APP_CONFIG, useValue: {fhirBaseUrl: 'https://tx.example.org/fhir'} },
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting()
    ]
});
    service = TestBed.inject(FhirService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('display language', () => {
    const dialects = 'es-X-450828004,en-X-900000000000509007';
    let httpMock: HttpTestingController;

    beforeEach(() => httpMock = TestBed.inject(HttpTestingController));
    afterEach(() => httpMock.verify());

    const flushMetadata = (softwareName: string) =>
      httpMock.expectOne(req => req.url === 'https://tx.example.org/fhir/metadata')
        .flush({resourceType: 'CapabilityStatement', software: {name: softwareName}});

    it('keeps the browser language for browsing when the map has no display language', () => {
      let result: string | undefined = 'unset';
      service.acceptLanguageForSearch(null).subscribe(value => result = value);
      expect(result).toBeUndefined();
    });

    it('asks for English terms to store when the map has no display language', () => {
      let result = '';
      service.acceptLanguageForMap(undefined).subscribe(value => result = value);
      expect(result).toBe('en-US');
    });

    it('sends the dialect sequence as is to Snowstorm', () => {
      let result: string | undefined;
      service.acceptLanguageForSearch(dialects).subscribe(value => result = value);
      flushMetadata('SnowstormX FHIR Server');
      expect(result).toBe(dialects);
    });

    it('converts the dialect sequence for Ontoserver and detects the server only once', () => {
      const results: (string | undefined)[] = [];
      service.acceptLanguageForSearch(dialects).subscribe(value => results.push(value));
      flushMetadata('Ontoserver®');
      service.acceptLanguageForMap(dialects).subscribe(value => results.push(value));
      expect(results).toEqual([
        'es-x-sctlang-45082800-4,en-x-sctlang-90000000-00005090-07',
        'es-x-sctlang-45082800-4,en-x-sctlang-90000000-00005090-07',
      ]);
    });

    it('falls back to the Snowstorm format when the server metadata cannot be read', () => {
      let result: string | undefined;
      service.acceptLanguageForSearch(dialects).subscribe(value => result = value);
      httpMock.expectOne(req => req.url === 'https://tx.example.org/fhir/metadata')
        .flush('down', {status: 503, statusText: 'Service Unavailable'});
      expect(result).toBe(dialects);
    });
  });
});
