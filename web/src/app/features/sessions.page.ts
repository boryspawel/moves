import { ChangeDetectionStrategy, Component, inject, Input, output, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { AgendaSessionView, AttemptDetailView, BarrierReportView, PrescriptionSnapshot, TodayAgendaView } from '../api/generated/src';
import { ResponseError } from '../api/generated/src/runtime';
import { ApiFacade } from '../core/api.facade';

type Stage = 'today' | 'preview' | 'checkin' | 'paused' | 'guided' | 'finish' | 'terminal' | 'problem';
type Outcome = 'PERFORMED' | 'PARTIAL' | 'SKIPPED';

@Component({
  selector: 'app-current-exercise-editor',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="exercise" aria-labelledby="exercise-title">
      <p>Ćwiczenie {{ position + 1 }} z {{ total }}</p>
      <h2 id="exercise-title">{{ prescription.exerciseName || 'Ćwiczenie ' + (position + 1) }}</h2>
      <p><strong>Plan:</strong> {{ plannedDose() }}</p>
      <p>{{ details() }}</p>
      <div class="actions">
        <button mat-flat-button type="button" (click)="submitAsPlanned()">Tak jak w planie</button>
        <button mat-stroked-button type="button" (click)="choose('PERFORMED')">Zmień wynik</button>
        <button mat-stroked-button type="button" (click)="choose('PARTIAL')">Częściowo</button>
        <button mat-stroked-button type="button" (click)="choose('SKIPPED')">Pomiń</button>
      </div>
      @if (selected) {
        <form class="editor" [formGroup]="form" (ngSubmit)="submit(selected)">
          <mat-form-field><mat-label>Powód</mat-label><input matInput formControlName="reason" maxlength="500" /></mat-form-field>
          @if (hasSets() && prescription.repetitions != null) { <mat-form-field><mat-label>Powtórzenia w seriach, np. 10, 10, 7</mat-label><input matInput formControlName="repetitions" /></mat-form-field> }
          @if (hasSets() && prescription.durationSeconds != null) { <mat-form-field><mat-label>Czas w seriach (s), np. 30, 30, 25</mat-label><input matInput formControlName="setDurations" /></mat-form-field> }
          @if (hasSets() && prescription.contacts != null) { <mat-form-field><mat-label>Kontakty w seriach, np. 12, 12, 10</mat-label><input matInput formControlName="setContacts" /></mat-form-field> }
          @if (!hasSets() && prescription.repetitions != null) { <mat-form-field><mat-label>Wykonane powtórzenia</mat-label><input matInput type="number" formControlName="repetitions" /></mat-form-field> }
          @if (!hasSets() && prescription.durationSeconds != null) { <mat-form-field><mat-label>Wykonany czas (s)</mat-label><input matInput type="number" formControlName="durationSeconds" /></mat-form-field> }
          @if (!hasSets() && prescription.contacts != null) { <mat-form-field><mat-label>Wykonane kontakty</mat-label><input matInput type="number" formControlName="contacts" /></mat-form-field> }
          @if (prescription.distanceMeters != null) { <mat-form-field><mat-label>Wykonany dystans (m)</mat-label><input matInput type="number" formControlName="distanceMeters" /></mat-form-field> }
          @if (prescription.externalLoadValue != null) { <mat-form-field><mat-label>Obciążenie {{ prescription.externalLoadUnit || '' }}</mat-label><input matInput type="number" formControlName="externalLoadValue" /></mat-form-field> }
          @if (prescription.intensityValue != null) { <mat-form-field><mat-label>Intensywność</mat-label><input matInput type="number" formControlName="intensityValue" /></mat-form-field> }
          @if (prescription.intensityZone) { <mat-form-field><mat-label>Strefa intensywności</mat-label><input matInput formControlName="intensityZone" /></mat-form-field> }
          @if (prescription.side) { <mat-form-field><mat-label>Strona</mat-label><input matInput formControlName="side" /></mat-form-field> }
          <button mat-flat-button type="submit" [disabled]="selected !== 'PERFORMED' && form.invalid">Zapisz wynik</button>
        </form>
      }
      @if (revision) { <p role="status">Zapisano wynik, wersja {{ revision }}.</p> }
    </article>
  `,
})
export class CurrentExerciseEditorComponent {
  @Input() prescription!: PrescriptionSnapshot;
  @Input() position = 0;
  @Input() total = 0;
  @Input() revision?: number;
  readonly submitted = output<{ outcome: Outcome; reason?: string; result?: Record<string, unknown>; modified?: boolean }>();
  selected: Outcome | null = null;
  readonly form = new FormGroup({
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(500)] }),
    repetitions: new FormControl('', { nonNullable: true }),
    setDurations: new FormControl('', { nonNullable: true }),
    setContacts: new FormControl('', { nonNullable: true }),
    durationSeconds: new FormControl('', { nonNullable: true }),
    contacts: new FormControl('', { nonNullable: true }),
    distanceMeters: new FormControl('', { nonNullable: true }),
    externalLoadValue: new FormControl('', { nonNullable: true }),
    intensityValue: new FormControl('', { nonNullable: true }),
    intensityZone: new FormControl('', { nonNullable: true }),
    side: new FormControl('', { nonNullable: true }),
  });

  choose(outcome: Outcome): void { this.selected = outcome; }
  hasSets(): boolean { return (this.prescription.sets ?? 0) > 0; }
  submitAsPlanned(): void { this.submitted.emit({ outcome: 'PERFORMED', result: this.plannedResult(), modified: false }); }
  submit(outcome: Outcome): void {
    if (outcome !== 'PERFORMED' && this.form.invalid) return;
    const result = this.actualResult();
    if (outcome !== 'SKIPPED' && !this.hasMeaningfulResult(result)) return;
    this.submitted.emit({ outcome, reason: outcome === 'PERFORMED' ? undefined : this.form.controls.reason.value, result, modified: outcome !== 'SKIPPED' });
  }
  private actualResult(): Record<string, unknown> {
    const values = this.form.getRawValue(); const number = (value: unknown) => String(value ?? '').trim() === '' ? undefined : Number(value);
    const ordered = (value: string) => value.split(',').map(part => Number(part.trim())).filter(part => Number.isFinite(part) && part >= 0);
    const repetitions = ordered(values.repetitions), durations = ordered(values.setDurations), contacts = ordered(values.setContacts);
    const actualSetDetails = this.hasSets() ? Array.from({ length: Math.max(repetitions.length, durations.length, contacts.length) }, (_, index) => ({ repetitions: repetitions[index], durationSeconds: durations[index], contacts: contacts[index] })).filter(set => Object.values(set).some(value => value != null)) : undefined;
    return {
      actualSets: actualSetDetails?.length ?? (this.hasSets() ? this.prescription.sets : undefined), actualSetDetails,
      actualRepetitions: this.hasSets() ? undefined : number(values.repetitions), actualDurationSeconds: number(values.durationSeconds), actualContacts: number(values.contacts),
      actualDistanceMeters: number(values.distanceMeters), actualExternalLoadValue: number(values.externalLoadValue), actualExternalLoadUnit: this.prescription.externalLoadUnit,
      actualIntensityType: this.prescription.intensityType, actualIntensityValue: number(values.intensityValue), actualIntensityZone: values.intensityZone.trim() || undefined, side: values.side.trim() || undefined,
    };
  }
  private plannedResult(): Record<string, unknown> {
    const p = this.prescription; const count = this.hasSets() ? p.sets : undefined;
    const actualSetDetails = count ? Array.from({ length: count }, () => ({ repetitions: p.repetitions, durationSeconds: p.durationSeconds, contacts: p.contacts, externalLoadValue: p.externalLoadValue, externalLoadUnit: p.externalLoadUnit })) : undefined;
    return { actualSets: count, actualSetDetails, actualRepetitions: count ? undefined : p.repetitions, actualDurationSeconds: count ? undefined : p.durationSeconds, actualContacts: count ? undefined : p.contacts, actualDistanceMeters: p.distanceMeters, actualExternalLoadValue: p.externalLoadValue, actualExternalLoadUnit: p.externalLoadUnit, actualIntensityType: p.intensityType, actualIntensityValue: p.intensityValue, actualIntensityZone: p.intensityZone, side: p.side };
  }
  private hasMeaningfulResult(result: Record<string, unknown>): boolean { return Object.entries(result).some(([key, value]) => key !== 'actualSets' && value != null && (!Array.isArray(value) || value.length > 0)); }
  plannedDose(): string { const p = this.prescription; return [p.sets && p.repetitions ? `${p.sets} × ${p.repetitions}` : '', p.durationSeconds ? `${p.durationSeconds} s` : ''].filter(Boolean).join(', ') || 'Dawka zgodna z planem'; }
  details(): string { const p = this.prescription; return [p.side ? `strona: ${p.side}` : '', p.tempo ? `tempo: ${p.tempo}` : '', p.restSeconds != null ? `odpoczynek: ${p.restSeconds} s` : '', p.notes ?? ''].filter(Boolean).join(' · '); }
}

@Component({
  selector: 'app-sessions-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, CurrentExerciseEditorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel" aria-labelledby="sessions-title">
      <h1 id="sessions-title">Twoja sesja</h1>
      <p role="status" aria-live="polite">{{ message() }}</p>
      @if (stage() === 'today') { @if (today(); as session) { <h2>{{ session.title || 'Zaplanowana sesja' }}</h2><button mat-flat-button type="button" (click)="choose(session)">Zobacz plan</button> } @else { <p>Na dziś nie ma sesji.</p> } }
      @if (stage() === 'preview') { <h2>Planowana sesja</h2><ul>@for (item of prescriptions(); track item.id) { <li>{{ item.exerciseName || 'Ćwiczenie ' + item.position }}: {{ dose(item) }}</li> }</ul><button mat-flat-button type="button" (click)="stage.set('checkin')">Kontynuuj</button> }
      @if (stage() === 'checkin') { <form [formGroup]="checkIn" (ngSubmit)="start()"><mat-form-field><mat-label>Ból teraz</mat-label><input matInput type="number" formControlName="painLevel" /></mat-form-field><mat-form-field><mat-label>Gotowość</mat-label><input matInput type="number" formControlName="readinessLevel" /></mat-form-field><mat-form-field><mat-label>Miejsce dyskomfortu (opcjonalnie)</mat-label><input matInput formControlName="painArea" maxlength="120" /></mat-form-field><button mat-flat-button [disabled]="checkIn.invalid">Rozpocznij sesję</button></form> }
      @if (stage() === 'paused') { <h2>Sesja jest wstrzymana</h2><p>Odczytanie sesji jej nie wznawia.</p><button mat-flat-button type="button" (click)="resume()">Wznów świadomie</button> }
      @if (stage() === 'guided' && current(); as item) { <app-current-exercise-editor [prescription]="item" [position]="index()" [total]="prescriptions().length" [revision]="revision(item.id)" (submitted)="saveFact(item, $event)"></app-current-exercise-editor><button mat-stroked-button type="button" (click)="previous()">Poprzednie</button><button mat-stroked-button type="button" (click)="next()">Następne</button><button mat-flat-button type="button" (click)="stage.set('finish')">Zakończ</button><button mat-stroked-button type="button" (click)="pause()">Wstrzymaj</button> }
      @if (stage() === 'finish') { <form [formGroup]="finishForm"><mat-form-field><mat-label>Ból po sesji</mat-label><input matInput type="number" formControlName="painLevel" /></mat-form-field><mat-form-field><mat-label>Trudność</mat-label><input matInput type="number" formControlName="difficultyLevel" /></mat-form-field><mat-form-field><mat-label>Pewność techniki</mat-label><input matInput type="number" formControlName="techniqueConfidenceLevel" /></mat-form-field><mat-form-field><mat-label>Odczuwany wysiłek sesji (opcjonalnie)</mat-label><input matInput type="number" formControlName="sessionRpe" /></mat-form-field><mat-form-field><mat-label>Notatka po sesji (opcjonalnie)</mat-label><input matInput formControlName="note" maxlength="500" /></mat-form-field><button mat-flat-button type="button" [disabled]="finishForm.invalid" (click)="finish('COMPLETE')">Zapisz zakończenie</button><button mat-stroked-button type="button" (click)="stopping.set(true)">Zatrzymaj sesję</button>@if (stopping()) { <mat-form-field><mat-label>Powód zatrzymania</mat-label><input matInput [formControl]="stopReason" /></mat-form-field><button mat-flat-button type="button" [disabled]="stopReason.invalid" (click)="finish('STOP')">Potwierdź zatrzymanie sesji</button> }</form> }
      @if (stage() === 'terminal') { <h2>Sesja została zapisana</h2><p>Wynik sesji: {{ terminalOutcome() }}</p><button mat-flat-button type="button" (click)="returnToday()">Wróć do dzisiejszych sesji</button> }
      @if (stage() === 'problem') { <h2 tabindex="-1">Co się dzieje?</h2>@if (!reportedProblem()) { <p>Zatrzymaj się, jeśli potrzebujesz. Wybierz powód, aby bezpiecznie wysłać zgłoszenie.</p><div role="group" class="problem-categories">@for (category of problemCategories; track category.value) { <button mat-stroked-button type="button" (click)="reportProblem(category.value)" [disabled]="reporting()">{{ category.label }}</button> }</div> } @else { <p role="status">Zgłoszenie zapisane. {{ safeNextStep(reportedProblem()) }}</p>@if (canContactSpecialist()) { <button mat-stroked-button type="button" class="contact-specialist" (click)="contactSpecialist()" [disabled]="reporting()">Skontaktuj się ze specjalistą</button> }<button type="button" (click)="closeProblem()">Wróć do sesji</button> } }
      @if (stage() !== 'problem' && stage() !== 'terminal') { <button type="button" class="problem-link" (click)="openProblem()">Mam problem</button> }
    </section>
  `,
})
export class SessionsPage {
  private readonly api = inject(ApiFacade); private readonly route = inject(ActivatedRoute); private readonly router = inject(Router);
  readonly agenda = signal<TodayAgendaView | null>(null); readonly today = signal<AgendaSessionView | null>(null); readonly attempt = signal<AttemptDetailView | null>(null); readonly stage = signal<Stage>('today'); readonly index = signal(0); readonly message = signal('Ładowanie…'); readonly stopping = signal(false); readonly terminalOutcome = signal('');
  private stageBeforeProblem: Stage = 'today';
  readonly checkIn = new FormGroup({ painLevel: new FormControl(0, { nonNullable: true, validators: [Validators.min(0), Validators.max(10)] }), readinessLevel: new FormControl(5, { nonNullable: true, validators: [Validators.min(1), Validators.max(10)] }), painArea: new FormControl('', { nonNullable: true, validators: Validators.maxLength(120) }) });
  readonly reportedProblem = signal<BarrierReportView | null>(null);
  readonly reporting = signal(false);
  readonly problemCategories = [{ value: 'PAIN_OR_SYMPTOMS', label: 'Ból lub dyskomfort' }, { value: 'TOO_DIFFICULT', label: 'Za trudno' }, { value: 'LOW_MOTIVATION', label: 'Trudno zacząć' }, { value: 'OTHER', label: 'Inny powód' }];
  readonly finishForm = new FormGroup({ painLevel: new FormControl(0, { nonNullable: true }), difficultyLevel: new FormControl(5, { nonNullable: true, validators: Validators.min(1) }), techniqueConfidenceLevel: new FormControl(5, { nonNullable: true, validators: Validators.min(1) }), sessionRpe: new FormControl<number | null>(null, { validators: [Validators.min(0), Validators.max(10)] }), note: new FormControl('', { nonNullable: true, validators: Validators.maxLength(500) }) });
  readonly stopReason = new FormControl('', { nonNullable: true, validators: Validators.required });
  constructor() { void this.load(); }
  choose(session: AgendaSessionView): void { this.today.set(session); this.stage.set('preview'); }
  openProblem(): void { this.stageBeforeProblem = this.stage(); this.reportedProblem.set(null); this.stage.set('problem'); }
  closeProblem(): void { this.stage.set(this.stageBeforeProblem === 'problem' ? 'today' : this.stageBeforeProblem); }
  async reportProblem(category: string): Promise<void> { this.reporting.set(true); try { this.reportedProblem.set(await this.api.barriers.report({ idempotencyKey: crypto.randomUUID(), barrierReportCommand: { plannedSessionId: this.today()?.sessionId, sessionAttemptId: this.attempt()?.attemptId, category } })); } finally { this.reporting.set(false); } }
  canContactSpecialist(): boolean { return this.reportedProblem()?.proposedOptions?.includes('CONTACT_SPECIALIST') ?? false; }
  safeNextStep(report: BarrierReportView | null): string { return report?.proposedOptions?.includes('CONTACT_SPECIALIST') ? 'Możesz też przekazać je specjaliście.' : 'System zaproponował bezpieczną następną opcję zgodnie z planem.'; }
  async contactSpecialist(): Promise<void> { const report = this.reportedProblem(); if (!report?.category) return; this.reporting.set(true); try { await this.api.barriers.report({ idempotencyKey: crypto.randomUUID(), barrierReportCommand: { plannedSessionId: this.today()?.sessionId, sessionAttemptId: this.attempt()?.attemptId, category: report.category, selectedAction: 'CONTACT_SPECIALIST' } }); this.message.set('Prośba o kontakt została bezpiecznie przekazana specjaliście.'); } finally { this.reporting.set(false); } }
  prescriptions(): PrescriptionSnapshot[] { return [...(this.attempt()?.session?.prescriptions ?? [])].sort((a, b) => (a.position ?? 0) - (b.position ?? 0)); }
  current(): PrescriptionSnapshot | null { return this.prescriptions()[this.index()] ?? null; }
  dose(item: PrescriptionSnapshot): string { return [item.sets && item.repetitions ? `${item.sets} × ${item.repetitions}` : '', item.durationSeconds ? `${item.durationSeconds} s` : ''].filter(Boolean).join(', ') || 'Dawka zgodna z planem'; }
  revision(id?: string): number | undefined { return this.attempt()?.facts?.filter(fact => fact.exercisePrescriptionId === id).reduce((highest, fact) => Math.max(highest, fact.revisionNumber ?? 0), 0); }
  previous(): void { this.index.update(value => Math.max(value - 1, 0)); }
  next(): void { this.index.update(value => Math.min(value + 1, this.prescriptions().length - 1)); }
  async start(): Promise<void> { const session = this.today(); const revision = session?.planRevisionId; if (!session?.sessionId || !revision) return; await this.api.safety.checkIn({ checkInRequest: this.checkIn.getRawValue() }); const attempt = await this.api.attempts.start({ idempotencyKey: crypto.randomUUID(), startAttemptCommand: { plannedSessionId: session.sessionId, planRevisionId: revision, selectedVariantType: 'STANDARD' } }); if (attempt.attemptId) await this.open(attempt.attemptId, true); }
  async resume(): Promise<void> { const id = this.attempt()?.attemptId; if (!id) return; await this.api.attempts.resume({ attemptId: id }); await this.open(id, true); }
  async pause(): Promise<void> { const id = this.attempt()?.attemptId; if (!id) return; await this.api.attempts.pause({ attemptId: id }); await this.open(id, false); }
  async saveFact(item: PrescriptionSnapshot, value: { outcome: Outcome; reason?: string; result?: Record<string, unknown>; modified?: boolean }): Promise<void> { const id = this.attempt()?.attemptId; if (!id || !item.id) return; const skipped = value.outcome === 'SKIPPED'; this.attempt.set(await this.api.attempts.fact({ attemptId: id, factCommand: { exercisePrescriptionId: item.id, outcome: value.outcome, reason: value.reason, result: { exercisePrescriptionId: item.id, ...(skipped ? {} : value.result), skipped, modified: skipped || value.modified === true, observationMode: 'DECLARED' } } })); }
  async finish(intent: 'COMPLETE' | 'STOP'): Promise<void> { const id = this.attempt()?.attemptId; if (!id || (intent === 'STOP' && this.stopReason.invalid)) return; const { sessionRpe, ...finishValues } = this.finishForm.getRawValue(); const result = await this.api.attempts.finish({ attemptId: id, idempotencyKey: crypto.randomUUID(), finishCommand: { intent, ...finishValues, sessionRpe: sessionRpe ?? undefined, stopReason: intent === 'STOP' ? this.stopReason.value : undefined } }); this.terminalOutcome.set(result.outcome ?? intent); this.stage.set('terminal'); }
  returnToday(): void { void this.router.navigate(['/sessions'], { queryParams: {} }); void this.load(); }
  private async load(): Promise<void> { const agenda = await this.api.today.today(); this.agenda.set(agenda); const id = this.route.snapshot.queryParamMap.get('sessionId'); const session = id ? agenda.sessions?.find(item => item.sessionId === id) ?? null : agenda.sessions?.find(item => !['COMPLETED', 'PARTIAL', 'SKIPPED', 'STOPPED'].includes(item.executionState ?? '')) ?? null; this.today.set(session); if (!session?.sessionId) { this.stage.set('today'); this.message.set(id ? 'Nie znaleziono wybranej sesji.' : ''); return; } try { const active = await this.api.attempts.active({ plannedSessionId: session.sessionId }); if (active.attemptId) await this.open(active.attemptId, false); } catch (error) { this.stage.set('today'); this.message.set(error instanceof ResponseError && error.response.status === 404 ? '' : 'Nie udało się odczytać aktywnej sesji. Spróbuj ponownie.'); } }
  private async open(id: string, resumed: boolean): Promise<void> { const detail = await this.api.attempts.get3({ attemptId: id }); this.attempt.set(detail); this.stage.set(detail.state === 'PAUSED' && !resumed ? 'paused' : 'guided'); this.message.set(detail.state === 'PAUSED' && !resumed ? 'Sesja czeka na świadome wznowienie.' : ''); }
}
