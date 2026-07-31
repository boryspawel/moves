import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import type { Dose } from '../../api/generated/src';

type DoseType = Dose['type'];

@Component({
  selector: 'app-dose-editor', imports: [ReactiveFormsModule], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<fieldset class="dose-fields" [disabled]="readonly()"><legend>Dawkowanie</legend><label>Typ<select [formControl]="type"><option value="">Wybierz typ</option>@for (item of types; track item) {<option [value]="item">{{ item }}</option>}</select></label>
  @if (type.value === 'STRENGTH') {<label>Serie<input type="number" min="1" [formControl]="first"></label><label>Powtórzenia<input type="number" min="1" [formControl]="second"></label><label>Odpoczynek (s)<input type="number" min="0" [formControl]="third"></label>}
  @if (type.value === 'AEROBIC') {<label>Czas (s)<input type="number" min="1" [formControl]="first"></label><label>Dystans (m)<input type="number" min="0" [formControl]="second"></label><label>RPE<input type="number" min="0" [formControl]="third"></label>}
  @if (type.value === 'ISOMETRIC') {<label>Serie<input type="number" min="1" [formControl]="first"></label><label>Utrzymanie (s)<input type="number" min="1" [formControl]="second"></label><label>Odpoczynek (s)<input type="number" min="0" [formControl]="third"></label>}
  @if (type.value === 'MOBILITY') {<label>Powtórzenia<input type="number" min="1" [formControl]="first"></label><label>Czas (s)<input type="number" min="1" [formControl]="second"></label><label>Tempo<input [formControl]="text"></label>}
  @if (type.value === 'STRETCH') {<label>Utrzymanie (s)<input type="number" min="1" [formControl]="first"></label><label>Powtórzenia<input type="number" min="1" [formControl]="second"></label><label>Intensywność<input [formControl]="text"></label>}
  @if (type.value === 'BREATHING') {<label>Czas (s)<input type="number" min="1" [formControl]="first"></label><label>Cykle<input type="number" min="1" [formControl]="second"></label><label>Rytm<input [formControl]="text"></label>}</fieldset>`,
})
export class DoseEditorComponent {
  readonly readonly = input(false); readonly changed = output<Dose | undefined>();
  readonly types: DoseType[] = ['STRENGTH', 'AEROBIC', 'ISOMETRIC', 'MOBILITY', 'STRETCH', 'BREATHING'];
  readonly type = new FormControl<DoseType | ''>('', { nonNullable: true, validators: Validators.required });
  readonly first = new FormControl<number | null>(null, [Validators.required, Validators.min(0)]); readonly second = new FormControl<number | null>(null, [Validators.required, Validators.min(0)]); readonly third = new FormControl<number | null>(null, [Validators.required, Validators.min(0)]); readonly text = new FormControl('', { nonNullable: true, validators: Validators.required });
  constructor() { this.type.valueChanges.subscribe(() => this.emit()); this.first.valueChanges.subscribe(() => this.emit()); this.second.valueChanges.subscribe(() => this.emit()); this.third.valueChanges.subscribe(() => this.emit()); this.text.valueChanges.subscribe(() => this.emit()); }
  private emit() { this.changed.emit(this.value()); }
  value(): Dose | undefined { if (!this.type.valid) return undefined; const first = this.first.value; const second = this.second.value; const third = this.third.value; const text = this.text.value.trim(); switch (this.type.value) {
    case 'STRENGTH': return !this.positive(first) || !this.positive(second) || !this.nonNegative(third) ? undefined : {type: 'STRENGTH', sets: first, reps: second, restSeconds: third};
    case 'AEROBIC': return !this.positive(first) || !this.nonNegative(second) || !this.nonNegative(third) ? undefined : {type: 'AEROBIC', durationSeconds: first, distanceMeters: second, rpe: third};
    case 'ISOMETRIC': return !this.positive(first) || !this.positive(second) || !this.nonNegative(third) ? undefined : {type: 'ISOMETRIC', sets: first, holdSeconds: second, restSeconds: third};
    case 'MOBILITY': return !this.positive(first) || !this.positive(second) || !text ? undefined : {type: 'MOBILITY', reps: first, durationSeconds: second, tempo: text};
    case 'STRETCH': return !this.positive(first) || !this.positive(second) || !text ? undefined : {type: 'STRETCH', holdSeconds: first, repetitions: second, intensity: text};
    case 'BREATHING': return !this.positive(first) || !this.positive(second) || !text ? undefined : {type: 'BREATHING', durationSeconds: first, cycles: second, rhythm: text};
    default: return undefined;
  } }
  private positive(value: number | null): value is number { return value != null && value >= 1; } private nonNegative(value: number | null): value is number { return value != null && value >= 0; }
}
