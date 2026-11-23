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

import {Inject, Injectable} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {Observable, forkJoin, of} from 'rxjs';
import {ServiceUtils} from '../_utils/service_utils';
import {APP_CONFIG, AppConfig} from '../app.config';
import {R4} from '@ahryman40k/ts-fhir-types';
import {catchError, concatMap, map, mergeMap, shareReplay, tap} from 'rxjs/operators';
import {ErrorDetail} from '../_models/error_detail';
import {BundleTypeKind, Bundle_RequestMethodKind} from '@ahryman40k/ts-fhir-types/lib/R4';
import {Coding, ConceptNode, Match} from '../store/fhir-feature/fhir.reducer';
import {LanguageDialects, TerminologyServerKind} from '../_utils/language-dialects';

export const SEQUENCE = 'http://ontoserver.csiro.au/fhir/ConceptMap/automapstrategy-seq;automapstrategy-default;automapstrategy-MML'
export const DEFAULT = 'http://ontoserver.csiro.au/fhir/ConceptMap/automapstrategy-default';
export const MML = 'http://ontoserver.csiro.au/fhir/ConceptMap/automapstrategy-MML';

export interface Release {
  edition: string; // edition is often a country
  version: string; // version is often a date
  uri: string;
  countryCode: string;
}

@Injectable({
  providedIn: 'root'
})
export class FhirService {

  private isOntoserver: boolean;
  // Snowstorm Lite supports fuzzy matching by appending '~' to the filter text
  private isLite: boolean;
  // Detected once from CapabilityStatement.software.name; drives the language dialect format
  private serverKind$?: Observable<TerminologyServerKind>;

  constructor(
    @Inject(APP_CONFIG) private config: AppConfig,
    private http: HttpClient
  ) {
    this.isOntoserver = this.config && this.config.fhirBaseUrl ?
      this.config.fhirBaseUrl.indexOf('ontoserver') > 0 : false;
    this.isLite = this.config && this.config.fhirBaseUrl ?
      this.config.fhirBaseUrl.indexOf('lite') > 0 : false;
  }

  findReplacementConcepts(conceptId: string, scope: string, version: string) {

    return forkJoin({
      sameAs: this.findSameAsConcepts(conceptId, scope, version),
      replacedBy: this.findReplacedByConcepts(conceptId, scope, version),
      possiblyEquivalentTo: this.findPossiblyEquivalentTo(conceptId, scope, version),
      alternative: this.findAlternative(conceptId, scope, version),
      code: conceptId
    });

  }

  //Same As : http://snomed.info/sct[/edition[/version]]?fhir_cm=900000000000527005 (target ValueSet is http://snomed.info/sct?fhir_vs)
  findSameAsConcepts(conceptId: string, scope: string, version: string) {
    return this.findHistoricalAssociation("900000000000527005", conceptId, scope, version);
  }

  //Replaced By : http://snomed.info/sct[/edition[/version]]?fhir_cm=900000000000526001 (target ValueSet is http://snomed.info/sct?fhir_vs)
  findReplacedByConcepts(conceptId: string, scope: string, version: string) {
    return this.findHistoricalAssociation("900000000000526001", conceptId, scope, version);
  }

  //Possibly Equivalent To : http://snomed.info/sct[/edition[/version]]?fhir_cm=900000000000523009 (target ValueSet is http://snomed.info/sct?fhir_vs)
  findPossiblyEquivalentTo(conceptId: string, scope: string, version: string) {
    return this.findHistoricalAssociation("900000000000523009", conceptId, scope, version);
  }

  //Alternative : http://snomed.info/sct[/edition[/version]]?fhir_cm=900000000000530003 (target ValueSet is http://snomed.info/sct?fhir_vs)
  findAlternative(conceptId: string, scope: string, version: string) {
    return this.findHistoricalAssociation("900000000000530003", conceptId, scope, version);
  }


  findHistoricalAssociation(cmId: string, conceptId: string, scope: string, version: string) {
    const url = `${this.config.fhirBaseUrl}/ConceptMap/$translate`;
    const body: R4.IParameters = {
      resourceType: "Parameters",
      parameter: [
        {
          name: "url",
          valueUri: version + "?fhir_cm=" + cmId
        },
        {
          name: "coding",
          valueCoding: {
            system: "http://snomed.info/sct",
            code: conceptId
          }
        }
      ]
    };

    const options = ServiceUtils.getHTTPHeaders();
    options.headers = options.headers
      .set('Content-Type', 'application/fhir+json');
    return this.http.post<R4.IParameters>(url, body, options);
  }


  findOutliers(toSystem: string, toVersion: string, targets: string[], toScope: string) {
    const valueSet: R4.IValueSet = {
      resourceType: 'ValueSet',
      compose: {
        include: [{
          system: toSystem,
          version: toVersion,
          concept: targets.map(code => ({code: code}))
        }],
        exclude: [{
          valueSet: [FhirService.toValueSet(toVersion, `(${toScope}){{C active=true}}`)],
        }]
      }
    };

    const url = `${this.config.fhirBaseUrl}/ValueSet/$expand`;
    const params = {
      'count': 1000000,
    };
    const options = ServiceUtils.getHTTPHeaders();
    options.headers = options.headers
      .set('Accept', ['application/fhir+json', 'application/json']);
    options.params = {...options.params, ...params};
    return this.http.post<R4.IValueSet>(url, valueSet, options);
  }

  fetchVersions(): Observable<R4.IBundle> {
    const url = `${this.config.fhirBaseUrl}/CodeSystem`;
    const params = {
      'url': 'http://snomed.info/sct',
      '_summary': 'true',
      '_sort': 'title,-version',
    };
    const options = ServiceUtils.getHTTPHeaders();
    options.headers = options.headers
      .set('Accept', ['application/fhir+json', 'application/json']);
    options.params = {...options.params, ...params};
    return this.http.get<R4.IBundle>(url, options);
  }

  /**
   * Terminology server kind, detected from its CapabilityStatement and cached. Falls back to
   * 'other' (Snowstorm dialect format) if the metadata cannot be read.
   */
  getServerKind(): Observable<TerminologyServerKind> {
    if (!this.serverKind$) {
      const options = ServiceUtils.getHTTPHeaders();
      options.headers = options.headers.set('Accept', ['application/fhir+json', 'application/json']);
      options.params = {'_summary': 'true'};
      this.serverKind$ = this.http.get<R4.ICapabilityStatement>(`${this.config.fhirBaseUrl}/metadata`, options).pipe(
        map(statement => LanguageDialects.serverKindFromSoftwareName(statement?.software?.name)),
        catchError(() => of<TerminologyServerKind>('other')),
        shareReplay(1)
      );
    }
    return this.serverKind$;
  }

  /**
   * Accept-Language value for a map's display language: the dialect sequence in the server's
   * format, or 'en-US' when the map has no display language (English terms, legacy behaviour).
   */
  acceptLanguageForMap(displayLanguage: string | null | undefined): Observable<string> {
    if (!LanguageDialects.normalize(displayLanguage)) {
      return of('en-US');
    }
    return this.getServerKind().pipe(
      map(kind => LanguageDialects.toServerFormat(displayLanguage, kind) ?? 'en-US')
    );
  }

  /**
   * Accept-Language value for browsing (search, suggestions, properties, hierarchy): the map's
   * dialect sequence in the server's format, or undefined to keep the browser's language when the
   * map has no display language.
   */
  acceptLanguageForSearch(displayLanguage: string | null | undefined): Observable<string | undefined> {
    if (!LanguageDialects.normalize(displayLanguage)) {
      return of(undefined);
    }
    return this.getServerKind().pipe(
      map(kind => LanguageDialects.toServerFormat(displayLanguage, kind) ?? undefined)
    );
  }

  /**
   * Term to store as a target display: the preferred term in the map's display language, or the
   * English FSN when the map has no display language. Empty string if the server returns none.
   */
  getTargetDisplay(code: string, system: string, version: string, displayLanguage: string | null | undefined): Observable<string> {
    if (!LanguageDialects.normalize(displayLanguage)) {
      return this.getEnglishFsn(code, system, version);
    }
    return this.acceptLanguageForMap(displayLanguage).pipe(
      mergeMap(acceptLanguage => this.lookupConcept(code, system, version, [], acceptLanguage)),
      map(parameters => parameters.parameter?.find(parameter => parameter.name === 'display')?.valueString ?? '')
    );
  }

  /**
   * @param acceptLanguage when set, sent as Accept-Language so the returned displays are in that
   * language (see acceptLanguageForMap); otherwise the browser's language applies.
   */
  autoSuggest(text: string, version: string, scope: string, strategy: string, activeOnly: boolean = true, count: number = 5, acceptLanguage?: string): Observable<Match[]> {
    if (this.isOntoserver) {
      const isMatch = (match: Match | null): match is Match => !!match;
      const ecl = activeOnly ? `(${scope}){{C active=true}}` : scope;

      const url = `${this.config.fhirBaseUrl}/ConceptMap/$translate`;
      const params = {
        'code': text,
        'system': 'http://ontoserver.csiro.au/fhir/CodeSystem/codesystem-terms',
        'url': strategy,
        'target': FhirService.toValueSet(version, ecl),
      };
      const options = ServiceUtils.getHTTPHeaders();
      options.headers = options.headers
        .set('Accept', ['application/fhir+json', 'application/json']);
      if (acceptLanguage) {
        options.headers = options.headers.set('Accept-Language', acceptLanguage);
      }
      options.params = {...options.params, ...params};
      return this.http.get<R4.IParameters>(url, options).pipe(
        map(parameters => {
          return (parameters?.parameter ?? [])
            .filter(p => 'match' === p.name)
            .map(FhirService.paramToMatch)
            .filter(isMatch);
        })
      );
    } else {
      return this.findConcepts(text, version, scope, activeOnly, count, acceptLanguage).pipe(
        map(valueset => {
          return valueset.expansion?.contains?.slice(0, count).map(entry => {
            return {
              code: entry.code,
              system: entry.system,
              version: entry.version,
              display: entry.display,
              label: entry.display,
              inactive: activeOnly ? false : !!entry.inactive,
            };
          }) ?? [];
        })
      )
    }
  }

  /**
   * On Snowstorm Lite, an exact search that finds nothing is retried as a fuzzy search ('~' suffix).
   * Fuzzy is not used up front because it ranks exact matches lower (e.g. 'Hypertension~' ranks
   * 'Low blood pressure' first), which matters for automap as it takes the top match.
   * Text the user already ends with '~' is sent as is.
   */
  findConcepts(text: string, version: string, scope: string, activeOnly: boolean = true, count: number = 100, acceptLanguage?: string): Observable<R4.IValueSet> {
    const trimmed = text?.trim() ?? '';
    if (!this.isLite || trimmed.length === 0 || trimmed.endsWith('~')) {
      return this.expandWithFilter(text, version, scope, activeOnly, count, acceptLanguage);
    }
    return this.expandWithFilter(trimmed, version, scope, activeOnly, count, acceptLanguage).pipe(
      mergeMap(valueset => (valueset.expansion?.contains?.length ?? 0) > 0
        ? of(valueset)
        : this.expandWithFilter(`${trimmed}~`, version, scope, activeOnly, count, acceptLanguage))
    );
  }

  private expandWithFilter(text: string, version: string, scope: string, activeOnly: boolean, count: number,
                           acceptLanguage?: string): Observable<R4.IValueSet> {
    const url = `${this.config.fhirBaseUrl}/ValueSet/$expand`;
    const params = {
      'filter': text,
      'url': FhirService.toValueSet(version, scope),
      'includeDesignations': true,
      'count': count,
      'activeOnly': activeOnly,
    };
    const options = ServiceUtils.getHTTPHeaders();
    options.headers = options.headers
      .set('Accept', ['application/fhir+json', 'application/json']);
    if (acceptLanguage) {
      options.headers = options.headers.set('Accept-Language', acceptLanguage);
    }
    options.params = {...options.params, ...params};
    return this.http.get<R4.IValueSet>(url, options);
  }

  static conceptNodeId(code: string, system: string): string {
    return system + '|' + code;
  }

  displayResolvedLookupConcept(code: string, system: string, version: string, properties: string[] = [],
                               acceptLanguage?: string): Observable<R4.IParameters> {

    let expandList = "";
    let conceptDisplayMap = new Map<string, string>();

    return this.lookupConcept(code, system, version, properties, acceptLanguage).pipe(
      tap(parameters => {
        // Step 1. find all the concept codes in the properties so their display can be looked up
        parameters.parameter?.map(param => {
          if ("property" === param.name) {
            let value;
            
            param.part?.forEach(part => {
              if (part.name) {
                if ('value' === part.name) {
                  if (part.valueCode) {
                    value = part.valueCode
                    if (expandList.length > 0) {
                      expandList += " OR ";
                    }
                    expandList += value;
                  }
                }
                else if ('subproperty' === part.name) {
                  part.part?.map(subPropPart => {
                    if (subPropPart.hasOwnProperty('valueCode')) {
                      if (expandList.length > 0) {
                        expandList += " OR ";
                      }
                      expandList += subPropPart.valueCode;
                    }
                  })
                }
                else if ('code' === part.name && part.valueCode && !isNaN(+part.valueCode)) {
                  // pick up the attribute relationships that do not reside in a role group
                  if ('609096000' !== part.valueCode) { // role group
                    if (expandList.length > 0) {
                      expandList += " OR ";
                    }
                    expandList += part.valueCode;

                  }

                }
              }

            })

          }
        })
      }),
      concatMap(parameters => {

        // 2. Look up the display for the concept codes 

        const url = `${this.config.fhirBaseUrl}/ValueSet/$expand`;
        // Resolve the attribute value displays in the same edition (and language) as the concept
        const params: any = {
          url: FhirService.toValueSet(acceptLanguage && version ? version : 'http://snomed.info/sct', expandList),
        };
    
        const options = ServiceUtils.getHTTPHeaders();
        options.headers = options.headers
          .set('Accept', ['application/fhir+json', 'application/json']);
        if (acceptLanguage) {
          options.headers = options.headers.set('Accept-Language', acceptLanguage);
        }
    
        options.params = {...options.params, ...params};
        return this.http.get<R4.IValueSet | R4.IOperationOutcome>(url, options).pipe( 
          tap(res => {
            if (res.resourceType === 'ValueSet') {
              res.expansion?.contains?.map(param => {
                if (param.code && param.display) {
                  conceptDisplayMap.set(param.code, param.display);
                }
              })
            }
        }),
        map(res => {

          // 3. update the parameters with display terms for display in the UI
          
          parameters.parameter?.map(param => {
            if ("property" === param.name) {
              let parameterCode : string | undefined;
  
              param.part?.forEach(paramPart => {
                if ('value' === paramPart.name) {
                  parameterCode = paramPart.valueCode;
                  param.part?.push({name: 'valueString', valueString: conceptDisplayMap.get(parameterCode!)});
                }
                else if ('subproperty' === paramPart.name) {
                  paramPart.part?.map(subpart => {
                    if (subpart.valueCode) {
                      if ('code' === subpart.name  || 'value' === subpart.name || 'valueCode' === subpart.name) {
                        paramPart.part?.push({name: subpart.name, valueString: conceptDisplayMap.get(subpart.valueCode)});
                      }
                    }                
                  })
                }
                else if ('code' === paramPart.name && paramPart.valueCode && !isNaN(+paramPart.valueCode)) {
                  // pick up the attribute relationships that do not reside in a role group
                  if ('609096000' !== paramPart.valueCode) { // role gorup
                    parameterCode = paramPart.valueCode;
                    param.part?.push({name: 'code', valueString: conceptDisplayMap.get(parameterCode!)});  
                  }

                }
              })  
            }
          })
          return parameters;
        })
        )
      })
    );
  }

  conceptHierarchy(code: string, system: string, version: string, acceptLanguage?: string): Observable<ConceptNode<Coding>[]> {
    const toId = FhirService.conceptNodeId;

    const expansionToNodes = (valueset: R4.IValueSet): ConceptNode<Coding>[] => {
      if (!valueset.expansion) throw 'No expansion in search result';
      const expansion = valueset.expansion;

      // build map from code system to version
      const versions: {[key:string]: string} = {};
      expansion.parameter
        ?.filter(p => 'version' == p.name)
        ?.forEach(p => {
          const parts = p.valueUri?.split('|');
          if (parts) {
            versions[parts[0]] = parts[1];
          }
        });

      return expansion.contains
        ?.filter((entry: R4.IValueSet_Contains) => entry.code && entry.system && entry.display)
        .map((entry: R4.IValueSet_Contains) => {
          const directParents: string[] = [];
          const payload: Coding = {
              code: entry.code,
              system: entry.system,
              version: entry.version ?? versions[entry.system ?? ''],
              display: entry.display,
            } as Coding;
          const node: ConceptNode<Coding> = {
            id: toId(payload.system, payload.code),
            display: entry.display ?? payload.code,
            payload,
            directParents,
          };

          entry.extension?.filter(ext => 'http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.contains.property' == ext.url)
            .forEach(ext => {
              const map: any = {}
              ext.extension?.forEach(ext2 => {
                if ('code' == ext2.url) {
                  map.code = ext2.valueCode;
                }
                if ('value' == ext2.url) {
                  map.value = ext2;
                }
                // This is a fallback case for old versions of Ontoserver
                if ('value_x_' == ext2.url && typeof map.value === 'undefined') {
                  map.value = ext2;
                }
              });
              if (map.code == 'parent') {
                directParents.push(toId(payload.system, map.value.valueCode));
              } else if (map.code == 'sufficientlyDefined') {
                node.primitive = !map.value.valueBoolean;
              }
            });

          return node as ConceptNode<Coding>;
        }) ?? [];
    };

    const url = `${this.config.fhirBaseUrl}/ValueSet/$expand`;
    const ecl = `>>(${code}) OR <!(${code})`;
    const params = {
      'url': FhirService.toValueSet(version, ecl),
      // 'includeDesignations': true,
      'property': ['parent', 'sufficientlyDefined'],
      'count': 1000,
    };
    const options = ServiceUtils.getHTTPHeaders();
    options.headers = options.headers
      .set('Accept', ['application/fhir+json', 'application/json']);
    if (acceptLanguage) {
      options.headers = options.headers.set('Accept-Language', acceptLanguage);
    }
    options.params = {...options.params, ...params};
    return this.http.get<R4.IValueSet>(url, options).pipe(
      map(expansionToNodes),
      mergeMap(nodes => {
        if (this.isOntoserver) {
          return of(nodes);
        } else {
          const nodeMap: {[key: string]: ConceptNode<Coding>} = {};
          nodes.forEach(node => {
            nodeMap[node.id] = node;
          });
          const id = toId(system, code);
          const node = nodeMap[id];
          const directParents = nodeMap[id].directParents ?? [];
          const retainedNodes: ConceptNode<Coding>[] = [node];

          return this.lookupConcept(code, system, version, [], acceptLanguage).pipe(
            map(parameters => {
              parameters.parameter?.map(param => {
                if ('property' === param.name) {
                  let code: string | undefined;
                  let value: string | undefined;
                  param.part?.forEach(part => {
                    if ('code' === part.name) {
                      code = part.valueString;
                    } else if ('value' === part.name) {
                      value = part.valueCode;
                    }
                  });
                  if ('parent' === code && value) {
                    const parentId = toId(system, value);
                    directParents.push(parentId);
                    retainedNodes.push(nodeMap[parentId]);
                  } else if ('child' === code && value) {
                    const childId = toId(system, value);
                    (nodeMap[childId].directParents ?? []).push(node.id);
                    retainedNodes.push(nodeMap[childId]);
                  }
                }
              });

              return retainedNodes;
            })
          );
        }
      })
    );
  }

  hierarchyProperties(expansion: R4.IValueSet, version: string): Observable<R4.IBundle> {
    const url = `${this.config.fhirBaseUrl}`;
    const body: R4.IBundle = {
      resourceType: 'Bundle',
      type: BundleTypeKind._batch,
      entry: expansion.expansion?.contains?.map(item => {
        if (!item.system || !item.code) throw new Error("Incomplete data in ValueSet expansion");

        const system = encodeURIComponent(item.system);
        const code = encodeURIComponent(item.code);
        const system_version = encodeURIComponent(item.version ?? version);
        return {
          request: {
            method: Bundle_RequestMethodKind._get,
            url: `CodeSystem/$lookup?system=${system}&code=${code}&version=${system_version}`
          }
        }
      }),
    };
    const options = ServiceUtils.getHTTPHeaders();
    options.headers = options.headers
      .set('Accept', ['application/fhir+json', 'application/json']);
    return this.http.post<R4.IBundle>(url, body, options);
  }

  getEnglishFsn(code: string, system: string, version: string, properties: string[] = []): Observable<string> {
    return this.lookupConcept(code, system, version, properties).pipe(
      map((parameters: R4.IParameters) => {
        return parameters.parameter?.filter(parameter => {
          return parameter.name == 'designation' && parameter.part?.some(param => param.name == 'use' && param.valueCoding?.code == '900000000000003001');
        }).map(fsns => ({
          language: fsns.part?.find(e => e.name == 'language')?.valueCode,
          fsn: fsns.part?.find(e => e.name == 'value')?.valueString
        }));
      }),
      map(fsnObjects => {
        let englishFsn = '';
        if (fsnObjects) {
          englishFsn = fsnObjects.find(fsn => fsn.language == 'en')?.fsn || '';
        }
        return englishFsn;
      })
    );
  }

  lookupConcept(code: string, system: string, version: string, properties: string[] = [], acceptLanguage?: string): Observable<R4.IParameters> {
    const url = `${this.config.fhirBaseUrl}/CodeSystem/$lookup`;
    const params: any = {
      'code': code,
      'system': system,
      'property': properties,
    };
    if (version) {
      params.version = version;
    }
    const options = ServiceUtils.getHTTPHeaders();
    options.headers = options.headers
      .set('Accept', ['application/fhir+json', 'application/json']);
    if (acceptLanguage) {
      options.headers = options.headers.set('Accept-Language', acceptLanguage);
    }
    options.params = {...options.params, ...params};
    return this.http.get<R4.IParameters>(url, options);
  }

  private static toValueSet(version: string, ecl: string): string {
    return version + '?fhir_vs=ecl/' + encodeURIComponent(ecl.replace(/\s+/g, ' '));
  }

  validateConceptInScopeAndActive(code: string, system: string, version: string, scope: string): Observable<R4.IParameters> {

    const url = `${this.config.fhirBaseUrl}/ValueSet/$validate-code`;

    const params: any = {
      'code': code,
      'system': system,
      'url': FhirService.toValueSet(version, scope)
    };

    const options = ServiceUtils.getHTTPHeaders();
    options.headers = options.headers
      .set('Accept', ['application/fhir+json', 'application/json']);
    options.params = {...options.params, ...params};
    return this.http.get<R4.IParameters>(url, options);

  }

  validateEcl(ecl: string): Observable<{ valid: boolean, detail?: any }> {
    const url = `${this.config.fhirBaseUrl}/ValueSet/$expand`;
    const params: any = {
      url: FhirService.toValueSet('http://snomed.info/sct', ecl),
      count: 1,   // work around Snowstorm bug with count=0
    };

    const options = ServiceUtils.getHTTPHeaders();
    options.headers = options.headers
      .set('Accept', ['application/fhir+json', 'application/json']);

    options.params = {...options.params, ...params};
    return this.http.get<R4.IValueSet | R4.IOperationOutcome>(url, options)
      .pipe(
        map((res) => {
          if (res.resourceType === 'ValueSet') {
            return {valid: true};
          }
          return {valid: false, detail: FhirService.ooToErrorDetail('http://snap2snomed.app/problem/invalid-ecl', res)};
        }),
        catchError(result => of({valid: false, detail: FhirService.ooToErrorDetail('http://snap2snomed.app/problem/invalid-ecl', result.error)}))
      );
  }

  private static ooToErrorDetail(id: string, oo: R4.IOperationOutcome): ErrorDetail {
    const detail: ErrorDetail = {
      type: id,
      detail: '',
      title: '',
      status: 400,
      violations: [],
    };
    oo.issue.forEach(i => {
      detail.violations?.push({field: i.code ?? '', message: i.diagnostics ?? ''});
    });
    return detail;
  }

  private static paramToMatch(param: R4.IParameters_Parameter): Match | null {
    const coding = param.part?.find(p => 'concept' === p.name)?.valueCoding;
    if (coding) {
      const semanticTag = coding?.extension?.find(ex => 'http://snomed.info/field/semanticTag' === ex.url)?.valueString;
      return {
        inactive: false,
        ...coding,
        label: coding.display,
        tag: semanticTag,
      };
    } else {
      return null;
    }
  }

}
