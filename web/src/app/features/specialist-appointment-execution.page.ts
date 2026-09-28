import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { DeclareExecutionCommand, ResultCommand, SpecialistAppointmentExecutionContext, SpecialistExecutionPrescriptionView } from '../api/generated/src';
import { ApiFacade } from '../core/api.facade';

type Choice = 'AS_PLANNED' | 'CHANGED' | 'SKIPPED';
type PrescriptionForm = FormGroup<{
  choice: FormControl<Choice | null>;
  sets: FormControl<number | null>;
  repetitions: FormControl<number | null>;
  durationSeconds: FormControl<number | null>;
  distanceMeters: FormControl<number | null>;
  contacts: FormControl<number | null>;
  externalLoadValue: FormControl<number | null>;
}>;

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`
    .session { max-width: 760px; margin: 24px auto; padding: 0 16px; }
    .prescription { border-top: 1px solid #ddd; padding: 20px 0; }
    .choices, .fields { display: flex; flex-wrap: wrap; gap: 8px; }
    .fields mat-form-field { width: min(220px, 100%); }
    .status { min-height: 1.5em; } .error { color: #b00020; }
  `],
  template: `
    <main class="session" aria-labelledby="session-title">
      <p class="status" aria-live="polite">{{ announcement() }}</p>
      @if (state() === 'loading') { <p>Ładowanie sesji…</p> }
      @if (state() === 'error') { <section #error tabindex="-1" role="alert" class="error"><h1>Nie udało się otworzyć sesji</h1><p>Spróbuj ponownie lub wróć do kartoteki uczestnika.</p><button mat-stroked-button type="button" (click)="load()">Spróbuj ponownie</button></section> }
      @if (context(); as data) {
        <header>
          <button mat-stroked-button type="button" (click)="back(data)">Wróć do uczestnika</button>
          <p>{{ data.participantName || 'Uczestnik' }} · Spotkanie w toku</p>
          <h1 id="session-title">{{ data.sessionTitle || 'Realizacja sesji' }}</h1>
        </header>
        @if (data.recordedExecution) {
          <section aria-labelledby="recorded-title"><h2 id="recorded-title">Realizacja została już zapisana</h2><p>Wynik: {{ outcomeLabel(data.recordedExecution.outcome) }}.</p><button mat-flat-button type="button" (click)="back(data)">Wróć do uczestnika</button></section>
        } @else if (data.recordingAllowed) {
          <form [formGroup]="sessionForm" (ngSubmit)="save(data)">
            @for (prescription of prescriptions(); track prescription.id; let index = $index) {
              <article class="prescription" [formGroup]="prescriptionForms()[index]" [attr.aria-labelledby]="'exercise-' + index">
                <h2 [id]="'exercise-' + index">{{ prescription.exerciseName || 'Ćwiczenie ' + (index + 1) }}</h2>
                <p>Plan: {{ plannedDose(prescription) }}</p>
                <div class="choices" role="radiogroup" [attr.aria-label]="'Wynik: ' + (prescription.exerciseName || 'ćwiczenie')">
                  <button mat-stroked-button type="button" role="radio" [attr.aria-checked]="choice(index) === 'AS_PLANNED'" (click)="select(index, 'AS_PLANNED')">Zgodnie z planem</button>
                  <button mat-stroked-button type="button" role="radio" [attr.aria-checked]="choice(index) === 'CHANGED'" (click)="select(index, 'CHANGED')">Zmień</button>
                  <button mat-stroked-button type="button" role="radio" [attr.aria-checked]="choice(index) === 'SKIPPED'" (click)="select(index, 'SKIPPED')">Pominięto</button>
                </div>
                @if (choice(index) === 'CHANGED') {
                  <div class="fields">
                    @if (showsSets(prescription)) { <mat-form-field><mat-label>Serie</mat-label><input matInput type="number" formControlName="sets" min="0" /></mat-form-field> }
                    @if (showsRepetitions(prescription)) { <mat-form-field><mat-label>Powtórzenia</mat-label><input matInput type="number" formControlName="repetitions" min="0" /></mat-form-field> }
                    @if (showsLoad(prescription)) { <mat-form-field><mat-label>Obciążenie {{ prescription.externalLoadUnit || '' }}</mat-label><input matInput type="number" formControlName="externalLoadValue" min="0" /></mat-form-field> }
                    @if (showsDuration(prescription)) { <mat-form-field><mat-label>Czas (s)</mat-label><input matInput type="number" formControlName="durationSeconds" min="0" /></mat-form-field> }
                    @if (showsDistance(prescription)) { <mat-form-field><mat-label>Dystans (m)</mat-label><input matInput type="number" formControlName="distanceMeters" min="0" /></mat-form-field> }
                    @if (showsContacts(prescription)) { <mat-form-field><mat-label>Kontakty</mat-label><input matInput type="number" formControlName="contacts" min="0" /></mat-form-field> }
                  </div>
                }
              </article>
            }
            <section class="prescription" aria-labelledby="response-title"><h2 id="response-title">Odpowiedź po sesji</h2>
              <div class="fields"><mat-form-field><mat-label>Ból po sesji (0–10)</mat-label><input matInput type="number" formControlName="painLevel" min="0" max="10" /></mat-form-field><mat-form-field><mat-label>Trudność (1–10)</mat-label><input matInput type="number" formControlName="difficultyLevel" min="1" max="10" /></mat-form-field><mat-form-field><mat-label>Odczuwany wysiłek (1–10, opcjonalnie)</mat-label><input matInput type="number" formControlName="sessionRpe" min="1" max="10" /></mat-form-field><mat-form-field><mat-label>Pewność techniki (1–10, opcjonalnie)</mat-label><input matInput type="number" formControlName="techniqueConfidenceLevel" min="1" max="10" /></mat-form-field></div>
              <mat-form-field><mat-label>Krótka notatka (opcjonalnie)</mat-label><input matInput formControlName="note" maxlength="500" /></mat-form-field>
            </section>
            @if (submitError()) { <p #submissionError tabindex="-1" class="error" role="alert">{{ submitError() }}</p> }
            <button mat-flat-button type="submit" [disabled]="saving() || !ready()">Zapisz realizację sesji</button>
          </form>
        } @else {
          <section aria-labelledby="unavailable-title"><h2 id="unavailable-title">Nie można teraz zapisać realizacji</h2><p>Stan spotkania lub sesji zmienił się. Wróć do kartoteki uczestnika, aby zobaczyć aktualne informacje.</p><button mat-flat-button type="button" (click)="back(data)">Wróć do uczestnika</button></section>
        }
      }
    </main>
  `,
})
export class SpecialistAppointmentExecutionPage {
  private readonly api = inject(ApiFacade);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);
  protected readonly context = signal<SpecialistAppointmentExecutionContext | null>(null);
  protected readonly state = signal<'loading' | 'loaded' | 'error'>('loading');
  protected readonly saving = signal(false);
  protected readonly announcement = signal('');
  protected readonly submitError = signal('');
  protected readonly prescriptions = signal<SpecialistExecutionPrescriptionView[]>([]);
  protected readonly prescriptionForms = signal<PrescriptionForm[]>([]);
  private idempotencyKey: string | undefined;
  readonly sessionForm = new FormGroup({
    painLevel: new FormControl<number | null>(null, [Validators.required, Validators.min(0), Validators.max(10)]),
    difficultyLevel: new FormControl<number | null>(null, [Validators.required, Validators.min(1), Validators.max(10)]),
    sessionRpe: new FormControl<number | null>(null, [Validators.min(1), Validators.max(10)]),
    techniqueConfidenceLevel: new FormControl<number | null>(null, [Validators.min(1), Validators.max(10)]),
    note: new FormControl('', { nonNullable: true, validators: Validators.maxLength(500) }),
  });

  constructor() { void this.load(); }

  async load(): Promise<void> {
    const appointmentId = this.route.snapshot.paramMap.get('appointmentId');
    if (!appointmentId) { this.state.set('error'); return; }
    this.state.set('loading'); this.submitError.set('');
    try {
      const context = await this.api.specialistAppointmentExecution.context({ appointmentId });
      this.context.set(context);
      const prescriptions = [...(context.prescriptions ?? [])].sort((a, b) => (a.position ?? 0) - (b.position ?? 0));
      this.prescriptions.set(prescriptions);
      this.prescriptionForms.set(prescriptions.map(() => this.newPrescriptionForm()));
      this.state.set('loaded');
    } catch {
      this.state.set('error'); this.focusError();
    }
  }

  protected choice(index: number): Choice | null { return this.prescriptionForms()[index]?.controls.choice.value ?? null; }
  protected select(index: number, choice: Choice): void {
    const form = this.prescriptionForms()[index]; if (!form) return;
    form.controls.choice.setValue(choice);
    this.applyChangedValidators(form, this.prescriptions()[index], choice === 'CHANGED');
  }
  protected ready(): boolean { return this.sessionForm.valid && this.prescriptionForms().every(form => form.controls.choice.value !== null && form.valid); }
  protected async save(context: SpecialistAppointmentExecutionContext): Promise<void> {
    if (this.saving() || !this.ready() || !context.appointmentId) return;
    this.saving.set(true); this.submitError.set('');
    this.idempotencyKey ??= crypto.randomUUID();
    try {
      await this.api.specialistAppointmentExecution.recordAppointmentExecution({ appointmentId: context.appointmentId, idempotencyKey: this.idempotencyKey, declareExecutionCommand: this.command() });
      this.announcement.set('Realizacja sesji została zapisana.');
      await this.back(context);
    } catch {
      this.submitError.set('Nie udało się zapisać realizacji sesji. Spróbuj ponownie.'); this.focusError();
    } finally { this.saving.set(false); }
  }
  protected async back(context: SpecialistAppointmentExecutionContext): Promise<void> {
    if (!context.participantId) return;
    if (this.route.snapshot.queryParamMap.get('returnTo') === 'closeout') {
      await this.router.navigate(['/specialist/clients', context.participantId, 'appointments', context.appointmentId, 'closeout']);
      return;
    }
    await this.router.navigate(['/specialist/clients', context.participantId], { queryParams: { sessionRecorded: '1' } });
  }
  protected resultFor(index: number): ResultCommand {
    const prescription = this.prescriptions()[index]; const form = this.prescriptionForms()[index]; const choice = form.controls.choice.value;
    const base = { exercisePrescriptionId: prescription.id, observationMode: 'DECLARED' };
    if (choice === 'SKIPPED') return { ...base, modified: false, skipped: true };
    if (choice === 'AS_PLANNED') return { ...base, modified: false, skipped: false, ...this.plannedActuals(prescription) };
    const values = form.getRawValue();
    return { ...base, modified: true, skipped: false,
      actualSets: this.showsSets(prescription) ? values.sets ?? undefined : undefined,
      actualRepetitions: this.showsRepetitions(prescription) ? values.repetitions ?? undefined : undefined,
      actualDurationSeconds: this.showsDuration(prescription) ? values.durationSeconds ?? undefined : undefined,
      actualDistanceMeters: this.showsDistance(prescription) ? values.distanceMeters ?? undefined : undefined,
      actualContacts: this.showsContacts(prescription) ? values.contacts ?? undefined : undefined,
      actualExternalLoadValue: this.showsLoad(prescription) ? values.externalLoadValue ?? undefined : undefined,
      actualExternalLoadUnit: this.showsLoad(prescription) ? prescription.externalLoadUnit : undefined,
    };
  }
  protected plannedDose(p: SpecialistExecutionPrescriptionView): string {
    return [p.sets != null && p.repetitions != null ? `${p.sets} × ${p.repetitions}` : '', p.durationSeconds != null ? `${p.durationSeconds} s` : '', p.distanceMeters != null ? `${p.distanceMeters} m` : '', p.contacts != null ? `${p.contacts} kontaktów` : '', p.externalLoadValue != null ? `${p.externalLoadValue} ${p.externalLoadUnit ?? ''}`.trim() : ''].filter(Boolean).join(', ') || 'Dawka zgodna z planem';
  }
  protected outcomeLabel(outcome?: string): string { return ({ COMPLETED: 'wykonano', PARTIAL: 'częściowo wykonano', SKIPPED: 'pominięto' })[outcome ?? ''] ?? 'zapisano'; }
  protected showsSets(p: SpecialistExecutionPrescriptionView): boolean { return (p.canonicalDoseType === 'STRENGTH' || p.canonicalDoseType === 'IMPACT' || p.doseType === 'DYNAMIC_RESISTANCE' || p.doseType === 'IMPACT') && p.sets != null; }
  protected showsRepetitions(p: SpecialistExecutionPrescriptionView): boolean { return (p.canonicalDoseType === 'STRENGTH' || p.doseType === 'DYNAMIC_RESISTANCE') && p.repetitions != null; }
  protected showsLoad(p: SpecialistExecutionPrescriptionView): boolean { return (p.canonicalDoseType === 'STRENGTH' || p.doseType === 'DYNAMIC_RESISTANCE') && p.externalLoadValue != null; }
  protected showsDuration(p: SpecialistExecutionPrescriptionView): boolean { return p.durationSeconds != null && !this.showsRepetitions(p); }
  protected showsDistance(p: SpecialistExecutionPrescriptionView): boolean { return p.distanceMeters != null; }
  protected showsContacts(p: SpecialistExecutionPrescriptionView): boolean { return (p.canonicalDoseType === 'IMPACT' || p.doseType === 'IMPACT') && p.contacts != null; }
  private command(): DeclareExecutionCommand {
    const value = this.sessionForm.getRawValue();
    return { declaredCompletion: true, observationMode: 'DECLARED', painLevel: value.painLevel ?? undefined, difficultyLevel: value.difficultyLevel ?? undefined, sessionRpe: value.sessionRpe ?? undefined, techniqueConfidenceLevel: value.techniqueConfidenceLevel ?? undefined, note: value.note.trim() || undefined, results: this.prescriptions().map((_, index) => this.resultFor(index)) };
  }
  private newPrescriptionForm(): PrescriptionForm { return new FormGroup({ choice: new FormControl<Choice | null>(null), sets: new FormControl<number | null>(null), repetitions: new FormControl<number | null>(null), durationSeconds: new FormControl<number | null>(null), distanceMeters: new FormControl<number | null>(null), contacts: new FormControl<number | null>(null), externalLoadValue: new FormControl<number | null>(null) }); }
  private plannedActuals(p: SpecialistExecutionPrescriptionView): ResultCommand { return { actualSets: p.sets, actualRepetitions: p.repetitions, actualDurationSeconds: p.durationSeconds, actualDistanceMeters: p.distanceMeters, actualContacts: p.contacts, actualExternalLoadValue: p.externalLoadValue, actualExternalLoadUnit: p.externalLoadValue != null ? p.externalLoadUnit : undefined, actualIntensityType: p.intensityType, actualIntensityValue: p.intensityValue, actualIntensityZone: p.intensityZone, side: p.side }; }
  private applyChangedValidators(form: PrescriptionForm, p: SpecialistExecutionPrescriptionView, active: boolean): void { const required = (control: FormControl<number | null>, show: boolean) => { control.setValidators(active && show ? [Validators.required, Validators.min(0)] : []); control.updateValueAndValidity(); }; required(form.controls.sets, this.showsSets(p)); required(form.controls.repetitions, this.showsRepetitions(p)); required(form.controls.durationSeconds, this.showsDuration(p)); required(form.controls.distanceMeters, this.showsDistance(p)); required(form.controls.contacts, this.showsContacts(p)); required(form.controls.externalLoadValue, this.showsLoad(p)); }
  private focusError(): void { queueMicrotask(() => this.host.nativeElement.querySelector('[role="alert"]') instanceof HTMLElement && (this.host.nativeElement.querySelector('[role="alert"]') as HTMLElement).focus()); }
}
