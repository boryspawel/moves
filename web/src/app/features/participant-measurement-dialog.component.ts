import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import type { ParticipantMeasurementCommand, ParticipantMeasurementCommandPresetIdEnum } from '../api/generated/src/models/ParticipantMeasurementCommand';
import type { ParticipantMeasurementPresetView } from '../api/generated/src/models/ParticipantMeasurementPresetView';

type MeasurementPreset = ParticipantMeasurementPresetView & { id: ParticipantMeasurementCommandPresetIdEnum };
function isMeasurementPresetId(value?: string): value is ParticipantMeasurementCommandPresetIdEnum {
  return value === 'BODY_WEIGHT' || value === 'BODY_CIRCUMFERENCE' || value === 'MAX_LOAD' || value === 'DISTANCE'
    || value === 'COMPLETION_TIME' || value === 'HOLD_DURATION' || value === 'CUSTOM';
}

@Component({
  selector: 'app-participant-measurement-dialog', standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule], changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './schedule-appointment-dialog.component.scss',
  template: `<section class="appointment-dialog" role="dialog" aria-modal="true" aria-labelledby="measurement-dialog-title" (keydown.escape)="closed.emit()"><form [formGroup]="form" (ngSubmit)="submit()">
    <button class="dialog-close" type="button" aria-label="Zamknij dodawanie pomiaru" [disabled]="saving" (click)="closed.emit()">×</button><h2 id="measurement-dialog-title">Dodaj pomiar</h2>
    @if (loading) { <p role="status">Wczytywanie rodzajów pomiarów…</p> } @if (error) { <p role="alert">Nie udało się przygotować lub zapisać pomiaru. Spróbuj ponownie.</p> }
    @if (!loading) { <label>Rodzaj pomiaru<select formControlName="presetId" (change)="choose()" required><option value="">Wybierz rodzaj</option>@for (preset of presets; track preset.id) { <option [value]="preset.id">{{ preset.label }}</option> }</select></label>
      @if (requires('BODY_AREA')) { <label>Obszar ciała<input formControlName="bodyArea" maxlength="80" required /></label> } @if (requires('EXERCISE')) { <label>Ćwiczenie<input formControlName="exercise" maxlength="160" required /></label> } @if (requires('ACTIVITY')) { <label>Aktywność<input formControlName="activity" maxlength="160" required /></label> } @if (requires('CUSTOM_LABEL')) { <label>Własna nazwa<input formControlName="customLabel" maxlength="160" required /></label> }
      <label>Wartość<input type="number" min="0" step="any" formControlName="value" required /></label>
      @if (unitChoices().length > 1) { <label>Jednostka<select formControlName="unit" required>@for (unit of unitChoices(); track unit) { <option [value]="unit">{{ unit }}</option> }</select></label> } @else { <label>Jednostka<input formControlName="unit" [readonly]="!!selected()?.defaultUnit" required /></label> }
      <label>Data i czas<input type="datetime-local" formControlName="measuredAt" required /></label><label>Notatka (opcjonalnie)<input formControlName="note" maxlength="500" /></label><div><button mat-stroked-button type="button" [disabled]="saving" (click)="closed.emit()">Anuluj</button><button mat-flat-button type="submit" [disabled]="saving || form.invalid">{{ saving ? 'Zapisywanie…' : 'Zapisz pomiar' }}</button></div> }
  </form></section>`,
})
export class ParticipantMeasurementDialogComponent implements OnChanges {
  @Input() presets: ParticipantMeasurementPresetView[] = [];
  @Input() requestedPresetId?: string;
  @Input() requestedBodyArea?: string;
  @Input() appointmentId?: string;
  @Input() loading = false;
  @Input() saving = false;
  @Input() error = false;
  @Output() closed = new EventEmitter<void>();
  @Output() submitted = new EventEmitter<ParticipantMeasurementCommand>();
  protected readonly selected = signal<MeasurementPreset | null>(null);
  private initialized = false;
  readonly form = new FormGroup({ presetId: new FormControl('', { nonNullable: true, validators: Validators.required }), value: new FormControl<number | null>(null, [Validators.required, Validators.min(0)]), unit: new FormControl('', { nonNullable: true, validators: Validators.required }), measuredAt: new FormControl(this.localNow(), { nonNullable: true, validators: Validators.required }), note: new FormControl('', { nonNullable: true, validators: Validators.maxLength(500) }), bodyArea: new FormControl('', { nonNullable: true }), exercise: new FormControl('', { nonNullable: true }), activity: new FormControl('', { nonNullable: true }), customLabel: new FormControl('', { nonNullable: true }) });
  protected choose(): void { const candidate = this.presets.find(item => item.id === this.form.controls.presetId.value); const preset = candidate && isMeasurementPresetId(candidate.id) ? { ...candidate, id: candidate.id } : null; this.selected.set(preset); this.form.controls.unit.setValue(preset?.defaultUnit ?? ''); for (const [field, key] of [['bodyArea', 'BODY_AREA'], ['exercise', 'EXERCISE'], ['activity', 'ACTIVITY'], ['customLabel', 'CUSTOM_LABEL']] as const) { const control = this.form.controls[field]; control.setValidators(this.requires(key, preset) ? [Validators.required, Validators.maxLength(160)] : []); control.updateValueAndValidity(); } }
  ngOnChanges(changes: SimpleChanges): void {
    const requestChanged = !!changes['requestedPresetId'] || !!changes['requestedBodyArea'];
    const initialCatalog = !this.initialized && !!this.presets.length;
    if (!requestChanged && !initialCatalog) return;
    this.initialized = this.initialized || initialCatalog;
    this.selected.set(null);
    this.form.reset({ presetId: '', value: null, unit: '', measuredAt: this.localNow(), note: '', bodyArea: '', exercise: '', activity: '', customLabel: '' });
    const preset = this.presets.find((item) => item.id === this.requestedPresetId);
    if (!preset || !isMeasurementPresetId(preset.id)) return;
    this.form.controls.presetId.setValue(preset.id);
    this.choose();
    if (this.requires('BODY_AREA') && this.requestedBodyArea) this.form.controls.bodyArea.setValue(this.requestedBodyArea);
  }
  protected requires(field: string, preset: ParticipantMeasurementPresetView | null = this.selected()): boolean { return !!preset?.requiredContextFields?.includes(field); }
  protected unitChoices(): string[] { const preset = this.selected(); return preset?.allowedUnits?.length && preset.allowedUnits.length > 1 ? preset.allowedUnits : []; }
  protected submit(): void { const preset = this.selected(); if (this.form.invalid || !preset) { this.form.markAllAsTouched(); return; } const raw = this.form.getRawValue(); const measuredAt = new Date(raw.measuredAt); if (Number.isNaN(measuredAt.getTime())) return; this.submitted.emit({ presetId: preset.id, value: raw.value ?? undefined, unit: raw.unit.trim() || undefined, measuredAt, note: raw.note.trim() || undefined, bodyArea: raw.bodyArea.trim() || undefined, exercise: raw.exercise.trim() || undefined, activity: raw.activity.trim() || undefined, customLabel: raw.customLabel.trim() || undefined, appointmentId: this.appointmentId }); }
  private localNow(): string { const now = new Date(); now.setMinutes(now.getMinutes() - now.getTimezoneOffset()); return now.toISOString().slice(0, 16); }
}
