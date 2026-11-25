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

import { Component, ElementRef, EventEmitter, Input, OnInit, Output, SimpleChanges, ViewChild } from '@angular/core';
import { Store } from '@ngrx/store';
import { IAppState } from '../../store/app.state';
import {
  AddMapping,
  ClearErrors,
  CopyMapping,
  DeleteMapping,
  UpdateMapping
} from '../../store/mapping-feature/mapping.actions';
import { TranslateService } from '@ngx-translate/core';
import {
  selectAddEditMappingSuccess,
  selectMappingError,
  selectMappingLoading
} from '../../store/mapping-feature/mapping.selectors';
import { selectCurrentUser } from '../../store/auth-feature/auth.selectors';
import { Mapping } from '../../_models/mapping';
import { SourceImportComponent } from '../../source/source-import/source-import.component';
import { MatDialog } from '@angular/material/dialog';
import { selectMappingFile, selectSourceList, selectSourceState } from '../../store/source-feature/source.selectors';
import { ImportMappingFileParams, InitSelectedSource, LoadSources } from 'src/app/store/source-feature/source.actions';
import { Source } from 'src/app/_models/source';
import { FhirService, Release } from 'src/app/_services/fhir.service';
import { LoadReleases } from 'src/app/store/fhir-feature/fhir.actions';
import { selectReleaseList, selectReleasesError } from 'src/app/store/fhir-feature/fhir.selectors';
import { UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { cloneDeep } from 'lodash';
import { ErrorInfo } from 'src/app/errormessage/errormessage.component';
import { FormUtils } from '../../_utils/form_utils';
import { MappingImportComponent } from '../mapping-import/mapping-import.component';
import { MappingImportSource } from 'src/app/_models/mapping_import_source';
import { selectAuthorizedProjects } from '../../store/app.selectors';
import { Project } from '../../_models/project';
import { MatSelectChange } from '@angular/material/select';
import { LanguageContext, LanguageMetadataService } from '../../_services/language-metadata.service';
import { LanguageDialects } from '../../_utils/language-dialects';
import { MAX_FROM_SNOMED_CONCEPTS, MapService } from '../../_services/map.service';

@Component({
  selector: 'app-mapping-add',
  templateUrl: './mapping-add.component.html',
  styleUrls: ['./mapping-add.component.css'],
  standalone: false
})
export class MappingAddComponent implements OnInit {
  private width = '1000px';
  error: ErrorInfo = {};
  sources: Source[] = [];
  editionToVersionsMap: Map<string, Release[]> | undefined; // versions (often dates) indexed by edition (often a country)
  editionToVersionsMapLoaded = false;
  editionVersions: Release[] = []; // versions of the selected edition (often a country)
  existingMapVersions: string[] | null = null;
  loading = false;
  selectedEdition: string = "";
  mappingFile: ImportMappingFileParams | undefined | null;
  // Display language options for the selected target version; '' is the English FSN (no explicit language)
  languageContexts: LanguageContext[] = [];

  MAX_TITLE = FormUtils.MAX_TITLE;
  MAX_VERSION = FormUtils.MAX_VERSION;
  MAX_DESCRIPTION = FormUtils.MAX_DESCRIPTION;
  MAX_TARGETSCOPE = FormUtils.MAX_TARGETSCOPE;
  VALID_STRING_PATTERN = FormUtils.VALID_STRING_PATTERN;

  mappingModel!: Mapping;
  previousVersionSource: Source | undefined;
  warnDelete = false;

  formGroup: UntypedFormGroup = new UntypedFormGroup({
    title: new UntypedFormControl(''),
    mapVersion: new UntypedFormControl(''),
    description: new UntypedFormControl(''),
    sourceId: new UntypedFormControl(''),
    toEdition: new UntypedFormControl(''),
    toVersion: new UntypedFormControl(''),
    toScopeSelect: new UntypedFormControl(''),
    toScope: new UntypedFormControl('', [Validators.minLength(1)]),
    displayLanguage: new UntypedFormControl(''),
    dualMapMode: new UntypedFormControl(''),
    direction: new UntypedFormControl('TO_SNOMED'),
    inheritance: new UntypedFormControl(false),
    // maps from SNOMED CT: source from an ECL or from an imported list of SNOMED CT codes
    sourceKind: new UntypedFormControl('ECL'),
    sourceCodeSetId: new UntypedFormControl(''),
    inheritanceMode: new UntypedFormControl('WITHIN_SOURCE')
  });

  /** A map from SNOMED CT whose source is an imported list of codes (chosen on creation, fixed after). */
  get fromSnomedCodes(): boolean {
    if (!this.fromSnomed) {
      return false;
    }
    return this.mode === 'FORM.CREATE' ? this.formGroup.controls.sourceKind.value === 'CODES' : !this.mappingModel.sourceEcl;
  }

  /** The ECL the form saves: the scope, or the source ECL (every concept for a list of codes). */
  private eclToValidate(): string {
    return this.fromSnomedCodes && this.mode === 'FORM.CREATE' ? '*' : this.mappingModel.toScope;
  }

  readonly MAX_FROM_SNOMED_CONCEPTS = MAX_FROM_SNOMED_CONCEPTS;
  /** Result of "count concepts" for a map from SNOMED CT; null until counted. */
  eclCount: number | null = null;
  eclCounting = false;
  eclCountFailed = false;

  /** A map from SNOMED CT: the ECL fields describe the source and the imported code set is the target. */
  get fromSnomed(): boolean {
    return Mapping.isFromSnomed(this.mappingModel);
  }

  @Input() set mapping(value: Mapping | undefined) {
    if (value) {
      this.mappingModel = cloneDeep(value);
      // if target version no longer available - need to clear model
      if (!this.hasAvailableTargetVersion(this.mappingModel.toVersion) && this.editionToVersionsMapLoaded) {
        this.formGroup.controls.toVersion.setValue('');
        this.formGroup.controls.toScope.setValue('');
      }
      else if (this.editionToVersionsMapLoaded && this.editionToVersionsMap) {
        // initialize to version (country and date)
        for (let [key, value] of this.editionToVersionsMap) {
          var result = value.filter(obj => {
            return obj.uri === this.mappingModel.toVersion
          })
          if (result.length > 0) {
            this.selectedEdition = key;
            this.editionVersions = value;
            break;
          }
        }
      }
      this.previousVersionSource = this.sources.find((source) => source.id === this.mappingModel.source.id);
      // get other map versions
      this.store.select(selectAuthorizedProjects).subscribe((projects) => {
        if (this.mappingModel?.project && this.mappingModel.project.id !== '') {
          const mapProject: Project[] = projects.filter((proj) => proj.id === this.mappingModel.project.id);
          this.existingMapVersions = mapProject[0] ? mapProject[0].maps.map((m) => m.mapVersion) : null;
        }
      }).unsubscribe();
    } else {
      this.newMapping();
    }
  }

  @Input() mode = 'FORM.CREATE';
  @Input() drawerOpen = false;

  @Output() closed = new EventEmitter();

  constructor(
    private elRef: ElementRef,
    private store: Store<IAppState>,
    private translate: TranslateService,
    private fhirService: FhirService,
    private mapService: MapService,
    private languageMetadataService: LanguageMetadataService,
    public dialog: MatDialog) {
  }

  /** Loads the named contexts of the target version's edition (for the read-only label). */
  loadLanguageContexts(versionUri: string | null): void {
    this.languageMetadataService.contextsForVersion(versionUri).subscribe(contexts => this.languageContexts = contexts);
  }

  /** Name of the map's display language context (or its raw sequence); null for the English FSN. */
  get displayLanguageLabel(): string | null {
    const current = LanguageDialects.normalize(this.mappingModel?.displayLanguage);
    if (!current) {
      return null;
    }
    return this.languageContexts.find(context => context.languageDialects === current)?.name ?? current;
  }

  ngOnInit(): void {
    const self = this;
    self.error = {};
    self.load();

    self.formGroup.controls.title.setValue(this.mappingModel.project.title);
    if (self.mode === 'FORM.COPY') {
      self.formGroup.controls.title.disable()
    }
    self.formGroup.controls.mapVersion.setValue(this.mappingModel.mapVersion);
    self.formGroup.controls.description.setValue(this.mappingModel.project.description);
    if (self.mode === 'FORM.VIEW' || self.mode === 'FORM.COPY') {
      self.formGroup.controls.description.disable()
    }
    self.formGroup.controls.sourceId.setValue(this.mappingModel.source.id);
    self.formGroup.controls.toEdition.setValue(this.selectedEdition);
    self.formGroup.controls.toVersion.setValue(this.mappingModel.toVersion);
    self.formGroup.controls.toScopeSelect.setValue(this.mappingModel.toScope);
    self.formGroup.controls.toScope.setValue(this.mappingModel.toScope);
    self.formGroup.controls.displayLanguage.setValue(this.mappingModel.displayLanguage ?? null);
    self.loadLanguageContexts(this.mappingModel.toVersion);
    self.formGroup.controls.dualMapMode.setValue(this.mappingModel.project.dualMapMode);
    self.formGroup.controls.direction.setValue(this.mappingModel.direction);
    self.formGroup.controls.inheritance.setValue(!!this.mappingModel.inheritance);
    self.formGroup.controls.sourceKind.setValue(this.fromSnomed && this.mode !== 'FORM.CREATE' && !this.mappingModel.sourceEcl ? 'CODES' : 'ECL');
    self.formGroup.controls.inheritanceMode.setValue(this.mappingModel.inheritanceMode ?? 'WITHIN_SOURCE');
    // the API drops the cached descendants when an existing map's mode changes
    self.formGroup.controls.inheritanceMode.valueChanges.subscribe((value) => self.mappingModel.inheritanceMode = value);
    self.formGroup.controls.inheritance.valueChanges.subscribe((value) => self.mappingModel.inheritance = !!value);
    if (this.fromSnomed && this.mappingModel.targetCodeSet) {
      self.formGroup.controls.sourceId.setValue(this.mappingModel.targetCodeSet.id);
    }

    self.formGroup.controls.title.valueChanges.subscribe((value) => {
      if (self.mappingModel.project.title !== value) {
        self.mappingModel.project.title = value;
      }
    });
    self.formGroup.controls.mapVersion.valueChanges.subscribe((value) => {
      if (self.mappingModel.mapVersion !== value) {
        self.mappingModel.mapVersion = value;
      }
    });
    self.formGroup.controls.description.valueChanges.subscribe((value) => {
      self.mappingModel.project.description = value;
    });
    // The imported code set field is the source of a map to SNOMED CT and the target of a map from it
    self.formGroup.controls.sourceId.valueChanges.subscribe((value) => self.applyCodeSet(value));
    self.formGroup.controls.direction.valueChanges.subscribe((value) => {
      self.mappingModel.direction = value;
      self.eclCount = null;
      if (self.fromSnomed) {
        // importing an existing map is not available for maps from SNOMED CT
        self.mappingFile = undefined;
      }
      self.applyCodeSet(self.formGroup.controls.sourceId.value);
    });
    self.formGroup.controls.toEdition.valueChanges.subscribe((value) => {
      self.selectedEdition = value;
      self.changeEdition(value);
    });
    self.formGroup.controls.toVersion.valueChanges.subscribe((value) => {
      self.mappingModel.toVersion = value;
      self.eclCount = null;
      self.loadLanguageContexts(value);
    });
    self.formGroup.controls.displayLanguage.valueChanges.subscribe((value) => {
      self.mappingModel.displayLanguage = LanguageDialects.normalize(value);
    });
    self.formGroup.controls.toScopeSelect.valueChanges.subscribe((value) => {
      self.mappingModel.toScope = value;
      if (value !== self.formGroup.controls.toScope.value) {
        self.formGroup.controls.toScope.setValue(value);
      }
    });
    self.formGroup.controls.toScope.valueChanges.subscribe((value) => {
      self.mappingModel.toScope = value;
      self.eclCount = null;
      if (value !== self.formGroup.controls.toScopeSelect.value) {
        self.formGroup.controls.toScopeSelect.setValue(value);
      }
    });
    self.formGroup.controls.dualMapMode.valueChanges.subscribe((value) => {
      if (self.mappingModel.project.dualMapMode !== value) {
        self.mappingModel.project.dualMapMode = value;
      }
      if (self.mappingModel.project.dualMapMode) {
        // import is not allowed in dual map mode
        this.mappingFile = undefined;
      }
    });

    this.applyModeToDualMapMode();
    this.applyModeToFromSnomedCopy();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.mode) {
      this.applyModeToDualMapMode();
      this.applyModeToFromSnomedCopy();
    }
    // clear all errors when drawer is closed
    if (changes.drawerOpen && changes.drawerOpen.currentValue === false && changes.drawerOpen.previousValue === true) {
      this.warnDelete = false;
      this.error = {};
      this.store.dispatch(new ClearErrors());
    }
  }

  /** A new version of a map from SNOMED CT keeps its ECL and display language; only the version changes. */
  private applyModeToFromSnomedCopy(): void {
    const locked = this.mode === 'FORM.COPY' && this.fromSnomed;
    for (const control of [this.formGroup.controls.toScopeSelect, this.formGroup.controls.toScope,
      this.formGroup.controls.displayLanguage]) {
      if (locked) {
        control.disable({emitEvent: false});
      } else if (control.disabled) {
        control.enable({emitEvent: false});
      }
    }
  }

  private applyModeToDualMapMode(): void {
    // The author mode and the direction are chosen when the map is created
    for (const control of [this.formGroup.controls.dualMapMode, this.formGroup.controls.direction]) {
      if (this.mode !== 'FORM.CREATE') {
        control.disable();
      } else {
        control.enable();
      }
    }
  }

  private applyCodeSet(id: string | null): void {
    // The target code set of a map from SNOMED CT is fixed once created, so only creation picks it
    if (this.mode !== 'FORM.CREATE' && (this.mode !== 'FORM.COPY' || this.fromSnomed)) {
      return;
    }
    if (this.fromSnomed) {
      this.mappingModel.targetCodeSet = id ? (this.sources.find(s => s.id === id) ?? {...new Source(), id}) : null;
      this.mappingModel.source = new Source();
    } else {
      this.mappingModel.targetCodeSet = null;
      this.mappingModel.source.id = id;
    }
  }

  /** Inheritance is offered for single author maps from SNOMED CT; it can be changed when editing. */
  get inheritanceAvailable(): boolean {
    return this.fromSnomed && !this.formGroup.controls.dualMapMode.value;
  }

  /** Counts the active concepts the ECL returns, so the user sees whether it fits a map from SNOMED CT. */
  countConcepts(): void {
    if (!this.mappingModel.toVersion || !this.mappingModel.toScope) {
      return;
    }
    this.eclCounting = true;
    this.eclCountFailed = false;
    this.fhirService.countEcl(this.mappingModel.toScope, this.mappingModel.toVersion).subscribe({
      next: count => {
        this.eclCount = count;
        this.eclCounting = false;
      },
      error: () => {
        this.eclCount = null;
        this.eclCountFailed = true;
        this.eclCounting = false;
      }
    });
  }

  get eclTooLarge(): boolean {
    return this.eclCount !== null && this.eclCount > MAX_FROM_SNOMED_CONCEPTS;
  }

  private newMapping(): void {
    this.mappingModel = new Mapping();
    this.error = {};
    this.store.select(selectCurrentUser).subscribe((owner) => {
      if (owner) {
        if (this.mode === 'FORM.CREATE') {
          this.mappingModel.project.owners = [owner];
        }
      }
    });
  }

  private load(): void {
    const self = this;

    self.loadReleases();
    self.loadSources();

    self.store.select(selectMappingLoading).subscribe((res) => this.loading = res);
    self.store.select(selectMappingFile).subscribe((res) => this.mappingFile = res);
    self.store.select(selectMappingError).subscribe((error) => {
      if (error != null) {
        if (error.type && error.type.includes("mapping-delete/last-map")) {
          self.translate.get('ERROR.DELETE_MAPPING').subscribe((res: string) => self.createOrAppendError(res));
        }
        else {
          self.translate.get('ERROR.ADD_MAPPING').subscribe((res: string) => self.createOrAppendError(res));
        }
        self.error.detail = error;
      } else {
        self.error = {};
      }
    });
    self.store.select(selectSourceList).subscribe(
      data => this.sources = data,
      error => this.translate.get('ERROR.LOAD_SOURCES').subscribe((res) => self.createOrAppendError(res))
    );
    self.store.select(selectReleaseList).subscribe(
      data => {
        this.editionToVersionsMap = data;
        this.editionToVersionsMapLoaded = true;
      },
      error => this.translate.get('ERROR.LOAD_VERSIONS').subscribe((res) => self.createOrAppendError(res))
    );
    // Only a failed target version load is relevant here (the message suggests reloading the list);
    // search or lookup errors from other screens are shared in the FHIR state and must not show up
    self.store.select(selectReleasesError).subscribe((error) => {
      if (error != null) {
        self.translate.get('ERROR.BACKEND_ISSUES', { url: error.url }).subscribe((res: string) => self.createOrAppendError(res));
        self.error.detail = error;
      }
    });
    self.store.select(selectAddEditMappingSuccess).subscribe(res => {
      if (res && !self.error.detail) {
        //this.closed.emit();
      }
    });
  }

  onSubmit(): void {
    this.error = {};
    this.fhirService.validateEcl(this.eclToValidate()).subscribe(
      (result) => {
        if (result.valid) {
          if (this.mode === 'FORM.CREATE' && this.fromSnomed && this.fromSnomedCodes) {
            this.createFromSnomedCodes();
          } else if (this.mode === 'FORM.CREATE' && this.fromSnomed) {
            this.createFromSnomed();
          } else if (this.mode === 'FORM.CREATE') {
            this.store.dispatch(new AddMapping({
              mapping: this.mappingModel,
              importFile: this.mappingFile,
              dualMapMode: this.mappingModel.project.dualMapMode
            }));
          } else if (this.mode === 'FORM.EDIT') {
            this.store.dispatch(new UpdateMapping(this.mappingModel));
          } else if (this.mode === 'FORM.COPY') {
            if (this.existingMapVersions) {
              if (this.existingMapVersions.indexOf(this.mappingModel.mapVersion) < 0) {
                if (this.fromSnomed) {
                  this.copyFromSnomed();
                } else {
                  this.store.dispatch(new CopyMapping(this.mappingModel));
                }
              } else {
                this.translate.get('ERROR.EXISTING_VERSION').subscribe((res) => {
                  this.error.message = `${res}: ${this.existingMapVersions?.join(',')}`;
                });
              }
            } else {
              this.translate.get('ERROR.PROJECT_LOAD').subscribe((res) => {
                this.error.message = res;
              });
            }
          }

        } else {
          this.translate.get('MAP.TARGET_SCOPE_INVALID_ERROR').subscribe((res) => {
            this.error.message = res;
          });
        }
      }
    );
  }

  /**
   * A map from SNOMED CT: the ECL is first expanded into the source code set (the API refuses more than
   * MAX_FROM_SNOMED_CONCEPTS concepts), then the map is created with it.
   */
  private createFromSnomed(): void {
    if (!this.mappingModel.targetCodeSet?.id) {
      this.translate.get('MAP.TARGET_CODE_SET_REQUIRED').subscribe(res => this.error.message = res);
      return;
    }
    const name = `${this.mappingModel.project.title} (SNOMED CT)`.substring(0, 100);
    const sourceEcl = this.mappingModel.toScope;
    this.loading = true;
    this.mapService.createSourceFromEcl(name, this.mappingModel.toVersion ?? '', sourceEcl,
      this.mappingModel.displayLanguage).subscribe({
      next: source => {
        this.loading = false;
        this.mappingModel.source = source;
        this.mappingModel.sourceEcl = sourceEcl;
        this.mappingModel.toScope = sourceEcl;
        this.store.dispatch(new AddMapping({
          mapping: this.mappingModel,
          importFile: undefined,
          dualMapMode: this.mappingModel.project.dualMapMode
        }));
      },
      error: err => {
        this.loading = false;
        this.translate.get('MAP.FROM_SNOMED_SOURCE_ERROR').subscribe(res => {
          this.error.message = res;
          this.error.detail = err?.error;
        });
      }
    });
  }

  /**
   * A map from SNOMED CT whose source is an imported list of SNOMED CT codes (checked by the API against the
   * chosen version); its scope is every concept.
   */
  private createFromSnomedCodes(): void {
    if (!this.mappingModel.targetCodeSet?.id) {
      this.translate.get('MAP.TARGET_CODE_SET_REQUIRED').subscribe(res => this.error.message = res);
      return;
    }
    const sourceId = this.formGroup.controls.sourceCodeSetId.value;
    if (!sourceId) {
      this.translate.get('MAP.SOURCE_CODES_REQUIRED').subscribe(res => this.error.message = res);
      return;
    }
    this.mappingModel.source = this.sources.find(s => s.id === sourceId) ?? {...new Source(), id: sourceId};
    this.mappingModel.sourceEcl = null;
    this.mappingModel.toScope = '*';
    this.store.dispatch(new AddMapping({
      mapping: this.mappingModel,
      importFile: undefined,
      dualMapMode: this.mappingModel.project.dualMapMode
    }));
  }

  /**
   * New version of a map from SNOMED CT: the same ECL and display language are expanded again in the
   * chosen SNOMED CT version into a new source code set; the copy keeps the rows of the concepts still in it.
   */
  private copyFromSnomed(): void {
    if (!this.mappingModel.sourceEcl) {
      // an imported list of codes is kept as the source of the new version
      this.store.dispatch(new CopyMapping(this.mappingModel));
      return;
    }
    const name = `${this.mappingModel.project.title} (SNOMED CT)`.substring(0, 100);
    this.loading = true;
    this.mapService.createSourceFromEcl(name, this.mappingModel.toVersion ?? '', this.mappingModel.sourceEcl,
      this.mappingModel.displayLanguage).subscribe({
      next: source => {
        this.loading = false;
        this.mappingModel.source = source;
        this.store.dispatch(new CopyMapping(this.mappingModel));
      },
      error: err => {
        this.loading = false;
        this.translate.get('MAP.FROM_SNOMED_SOURCE_ERROR').subscribe(res => {
          this.error.message = res;
          this.error.detail = err?.error;
        });
      }
    });
  }

  onCancel($event: Event): void {
    $event.preventDefault();
    this.warnDelete = false;
    this.closed.emit();
    this.error = {};
  }


  /** Imports a code set; for the source list of a map from SNOMED CT it goes to that field. */
  addSource($event: MouseEvent, field: 'sourceId' | 'sourceCodeSetId' = 'sourceId'): void {
    $event.preventDefault();
    this.store.dispatch(new InitSelectedSource());
    let data = new Source();

    // pre-fill values SNOMED-453
    if (this.previousVersionSource) {
      data.name = this.previousVersionSource.name;
      data.version = FormUtils.calculateNextVersion(this.previousVersionSource.version);
    }

    const dialogRef = this.dialog.open(SourceImportComponent, {
      width: this.width, maxWidth: this.width, data
    });

    dialogRef.afterClosed().subscribe(
      (result: boolean) => {
        if (result) {
          // don't do this if the user selected cancel, it clears the existing source out
          this.store.select(selectSourceState).subscribe((state) => {
            this.sources = state.sources;
            if (field === 'sourceCodeSetId') {
              this.formGroup.controls.sourceCodeSetId.setValue(state.selectedSource?.id ?? '');
            } else {
              this.mappingModel.source = state.selectedSource ?? new Source();
              this.formGroup.controls.sourceId.setValue(this.mappingModel.source.id);
            }
          });
        }
      });
  }

  loadReleases(): void {
    this.store.dispatch(new LoadReleases());
  }

  loadSources(): void {
    this.store.dispatch(new LoadSources());
  }

  getFormModeTextForTranslation(): string {
    if (this.mode === 'FORM.VIEW') {
      return 'MAP.MAP_VIEW_DETAILS';
    } else if (this.mode === 'FORM.CREATE') {
      return 'MAP.MAP_ADD';
    } else if (this.mode === 'FORM.EDIT') {
      return 'MAP.MAP_EDIT';
    } else if (this.mode === 'FORM.COPY') {
      return 'MAP.MAP_COPY';
    } else {
      throw Error('Unknown mode ' + this.mode);
    }
  }

  onImportMapping($event: Event): void {
    $event.preventDefault();
    const dialogRef = this.dialog.open(MappingImportComponent, {
      width: this.width, maxWidth: this.width, data: {
        source: new MappingImportSource(),
        createMode: true
      }
    });

    dialogRef.afterClosed().subscribe(
      (result: any) => {
        if (result instanceof ImportMappingFileParams) {
          this.mappingFile = result;
        }
      });
  }

  onClearSelectedMappingFile($event: Event): void {
    this.mappingFile = undefined;
  }

  errorUpdate(event: any): void {
    if (event === null) {
      this.error = {};
    } else {
      this.error.message = event.msg ?? null;
      this.error.messages = undefined;
      this.error.detail = event.detail ?? null;
    }
  }

  changeEdition(value: string) {
    this.formGroup.controls.toVersion.setValue(''); // reset
    const versions = this.editionToVersionsMap?.get(value);
    if (versions) {
      this.editionVersions = versions;
      // select the most recent (or only, if just 1) version
      this.formGroup.controls.toVersion.setValue(versions[0].uri);
    }
  }

  deleteMap(): void {
    if (!this.warnDelete) {
      this.warnDelete = true;
    }
    else {
      this.warnDelete = false;
      this.store.dispatch(new DeleteMapping(this.mappingModel));
    }
  }

  private createOrAppendError(err: string): void {
    const self = this;
    self.elRef.nativeElement.parentElement.scrollTop = 0;
    if (!self.error.messages) {
      self.error.messages = [];
    }
    if (!self.error.messages.includes(err)) {
      self.error.messages.push(err);
    }
  }

  private hasAvailableTargetVersion(toVersion: string | null): boolean {
    if (toVersion && this.editionToVersionsMap && this.editionToVersionsMap.size > 0) {
      // returns array of uris
      for (let [key, value] of this.editionToVersionsMap) {
        var result = value.filter(obj => {
          return obj.uri === toVersion
        })
        if (result.length > 0) {
          return true;
        }
      }
    }
    return false;
  }
}
