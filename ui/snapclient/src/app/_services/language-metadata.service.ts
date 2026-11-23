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

import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, of} from 'rxjs';
import {catchError, map, shareReplay} from 'rxjs/operators';
import {LanguageDialects} from '../_utils/language-dialects';

/** A named display language option: an ordered sequence of language dialects. */
export interface LanguageContext {
  name: string;
  languageDialects: string;
}

interface LanguageMetadata {
  editions: {
    title: string;
    moduleUri: string;
    contexts: LanguageContext[];
  }[];
}

/**
 * Language contexts per SNOMED CT edition, from assets/language/national-language-metadata.json
 * (see the README next to it for its source and local changes).
 */
@Injectable({
  providedIn: 'root'
})
export class LanguageMetadataService {

  private metadata$?: Observable<LanguageMetadata>;

  constructor(private http: HttpClient) {
  }

  /** Contexts for the edition of a version URI; empty if the edition is not in the metadata. */
  contextsForVersion(versionUri: string | null | undefined): Observable<LanguageContext[]> {
    const editionUri = LanguageDialects.editionUri(versionUri);
    if (!editionUri) {
      return of([]);
    }
    return this.getMetadata().pipe(
      map(metadata => (metadata.editions.find(edition => edition.moduleUri === editionUri)?.contexts ?? [])
        .map(context => ({name: context.name, languageDialects: LanguageDialects.normalize(context.languageDialects) ?? ''}))
        .filter(context => context.languageDialects.length > 0))
    );
  }

  /** Name of the context matching a stored dialect sequence, or null if there is none. */
  contextName(versionUri: string | null | undefined, dialects: string | null | undefined): Observable<string | null> {
    const normalized = LanguageDialects.normalize(dialects);
    if (!normalized) {
      return of(null);
    }
    return this.contextsForVersion(versionUri).pipe(
      map(contexts => contexts.find(context => context.languageDialects === normalized)?.name ?? null)
    );
  }

  private getMetadata(): Observable<LanguageMetadata> {
    if (!this.metadata$) {
      this.metadata$ = this.http.get<LanguageMetadata>('assets/language/national-language-metadata.json').pipe(
        catchError(error => {
          console.error('Could not load the language metadata', error);
          return of({editions: []});
        }),
        shareReplay(1)
      );
    }
    return this.metadata$;
  }
}
