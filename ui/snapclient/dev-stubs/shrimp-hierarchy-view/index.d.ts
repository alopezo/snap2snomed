import { EventEmitter } from '@angular/core';
import * as i0 from '@angular/core';

export interface ConceptNode<T> {
  id: string;
  display: string;
  payload?: T;
  directParents?: string[];
  primitive?: boolean;
}

export declare class ShrimpHierarchyComponent<T = any> {
  rows: ConceptNode<T>[];
  selected: EventEmitter<T>;
  static ɵfac: i0.ɵɵFactoryDeclaration<ShrimpHierarchyComponent<any>, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<ShrimpHierarchyComponent<any>, "lib-shrimp-hierarchy", never, { "rows": { "alias": "rows"; "required": false; }; }, { "selected": "selected"; }, never, never, true, never>;
}

export declare class ShrimpHierarchyViewModule {
  static ɵfac: i0.ɵɵFactoryDeclaration<ShrimpHierarchyViewModule, never>;
  static ɵmod: i0.ɵɵNgModuleDeclaration<ShrimpHierarchyViewModule, never, [typeof ShrimpHierarchyComponent], [typeof ShrimpHierarchyComponent]>;
  static ɵinj: i0.ɵɵInjectorDeclaration<ShrimpHierarchyViewModule>;
}
