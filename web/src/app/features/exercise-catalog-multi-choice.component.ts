import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';

export type ExerciseCatalogChoice = { value: string; label: string };

@Component({
  selector: 'app-exercise-catalog-multi-choice',
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatSelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `:host { display:block; } mat-form-field { width:100%; } .custom-equipment { display:flex; gap:var(--space-2); margin-top:var(--space-2); }`,
  template: `<mat-form-field><mat-label>{{label}}</mat-label><mat-select [name]="name" multiple [ngModel]="selection" (ngModelChange)="selectionChange.emit($event)"><mat-select-trigger>@for (value of selection; track value; let last = $last) { {{labelFor(value)}}@if (!last) {, } }</mat-select-trigger>@for (option of options; track option.value) {<mat-option [value]="option.value">{{option.label}}</mat-option>}</mat-select></mat-form-field>@if (allowCustom) {<div class="custom-equipment"><input class="app-native-control" [name]="name + '-custom'" [(ngModel)]="customValue" placeholder="Inny sprzęt" (keyup.enter)="addCustom()" /><button type="button" mat-button (click)="addCustom()">Dodaj</button></div>}`,
})
export class ExerciseCatalogMultiChoiceComponent {
  @Input({required: true}) label = '';
  @Input({required: true}) name = '';
  @Input() options: ExerciseCatalogChoice[] = [];
  @Input() selection: string[] = [];
  @Input() allowCustom = false;
  @Output() selectionChange = new EventEmitter<string[]>();
  customValue = '';
  labelFor(value: string) { return this.options.find(option => option.value === value)?.label ?? value; }
  addCustom() { const value = this.customValue.trim(); if (value && !this.selection.includes(value)) this.selectionChange.emit([...this.selection, value]); this.customValue = ''; }
}
