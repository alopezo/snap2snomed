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

/**
 * Main Map class (note naming as Mapping is to avoid conflicts with native JS function, map)
 */

import {Project} from './project';
import {Source} from './source';

/** Which side of a map is SNOMED CT; see MapDirection in the API. */
export type MapDirection = 'TO_SNOMED' | 'FROM_SNOMED';

export class Mapping {

  constructor() {
    this.id = '';
    this.project = new Project();
    this.mapVersion = '';
    this.source = new Source();
    this.toVersion = null;
    this.toScope = '*'; // ECL for "all SNOMED concepts" - cannot use 'ANY' because SnowStorm
    this.displayLanguage = null;
    this.created = new Date();
    this.modified = new Date();
    this.blindMapFlag = false;
    this.direction = 'TO_SNOMED';
    this.targetCodeSet = null;
    this.inheritance = false;
  }

  id: string | null;
  project: Project;
  mapVersion: string;
  source: Source;
  toSystem = 'http://snomed.info/sct';
  /**
   * toVersion, toScope and displayLanguage always describe the SNOMED CT side: the target for
   * TO_SNOMED maps, the source (an ECL frozen into the source code set) for FROM_SNOMED maps.
   */
  toVersion: string | null;
  toScope: string;
  /**
   * Ordered language dialects (Snowstorm format, e.g. "es-X-450828004,en-X-900000000000509007") used
   * for target display terms; null means the English FSN.
   */
  displayLanguage: string | null;
  created: Date;
  modified: Date;
  blindMapFlag: Boolean;
  direction: MapDirection;
  /** FROM_SNOMED maps: the imported code set the targets are chosen from. */
  targetCodeSet: Source | null;
  /** FROM_SNOMED single author maps: unmapped concepts take the targets of their nearest mapped ancestor. */
  inheritance: boolean;

  static replacer(key: string, value: any): any {
    if (value !== null && key === 'project') {
      return '/projects/' + value.id;
    } else if (value !== null && (key === 'source' || key === 'targetCodeSet')) {
      return '/importedCodeSets/' + value.id;
    }
    return value;
  }

  static isFromSnomed(mapping: Mapping | null | undefined): boolean {
    return mapping?.direction === 'FROM_SNOMED';
  }

  static hasInheritance(mapping: Mapping | null | undefined): boolean {
    return Mapping.isFromSnomed(mapping) && !!mapping?.inheritance && !mapping?.project?.dualMapMode;
  }

  /** The code system of the map's targets: SNOMED CT, or the target code set of a map from SNOMED CT. */
  static targetSystem(mapping: Mapping | null | undefined): string {
    if (Mapping.isFromSnomed(mapping)) {
      const codeSet: any = mapping?.targetCodeSet;
      return codeSet?.systemUri || codeSet?.codesystemUri || `urn:snap2snomed:code-set:${codeSet?.id ?? ''}`;
    }
    return mapping?.toSystem ?? 'http://snomed.info/sct';
  }

  static getStatus(mapping: Mapping): string {
    // TODO - Calculate rows mapped
    return 'MAP.STATUS_NOT_STARTED';
  }
}


