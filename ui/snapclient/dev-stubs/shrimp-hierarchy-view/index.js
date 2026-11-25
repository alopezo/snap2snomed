// LOCAL DEV STUB - hand-written Ivy definitions standing in for the private package.
import * as i0 from '@angular/core';

export class ShrimpHierarchyComponent {
  constructor() {
    this.rows = [];
    this.selected = new i0.EventEmitter();
  }
}
ShrimpHierarchyComponent.ɵfac = function ShrimpHierarchyComponent_Factory(t) {
  return new (t || ShrimpHierarchyComponent)();
};
ShrimpHierarchyComponent.ɵcmp = i0.ɵɵdefineComponent({
  type: ShrimpHierarchyComponent,
  selectors: [['lib-shrimp-hierarchy']],
  inputs: { rows: 'rows' },
  outputs: { selected: 'selected' },
  decls: 2,
  vars: 0,
  consts: [['style', 'padding:12px;font-style:italic;opacity:.7']],
  template: function ShrimpHierarchyComponent_Template(rf) {
    if (rf & 1) {
      i0.ɵɵelementStart(0, 'p', 0);
      i0.ɵɵtext(1, 'Shrimp hierarchy view is not available in this local build (dev stub). Use the tree view.');
      i0.ɵɵelementEnd();
    }
  },
  encapsulation: 2,
});

export class ShrimpHierarchyViewModule {}
ShrimpHierarchyViewModule.ɵfac = function ShrimpHierarchyViewModule_Factory(t) {
  return new (t || ShrimpHierarchyViewModule)();
};
ShrimpHierarchyViewModule.ɵmod = i0.ɵɵdefineNgModule({
  type: ShrimpHierarchyViewModule,
  imports: [ShrimpHierarchyComponent],
  exports: [ShrimpHierarchyComponent],
});
ShrimpHierarchyViewModule.ɵinj = i0.ɵɵdefineInjector({});
