import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { AgendaSessionView, AttemptDetailView, BarrierReportView, PrescriptionView, SessionView, TodayAgendaView } from '../api/generated/src';
import { ApiFacade } from '../core/api.facade';

type Stage = 'today' | 'variant' | 'check-in' | 'guided' | 'result' | 'problem';
type Variant = 'STANDARD' | 'SHORT' | 'MINIMUM';

@Component({
  selector: 'app-sessions-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <section class="panel session-flow" aria-labelledby="sessions-title">
      <h1 id="sessions-title">Twoja sesja na dziś</h1>
      <p class="muted">Wybierz tyle, ile jest dziś dla Ciebie bezpieczne. Możesz przerwać i wrócić później.</p>
      <p class="status" role="status" aria-live="polite" [class.error]="failed()">{{ message() }}</p>

      @if (stage() === 'today') {
        @if (todaySession(); as session) {
          <section class="session-summary" aria-labelledby="today-session-title">
            <h2 id="today-session-title">{{ session.title || 'Zaplanowana sesja' }}</h2>
            <p>{{ durationLabel(session) }}{{ session.doseSummary ? ' · ' + session.doseSummary : '' }}</p>
            <button mat-flat-button type="button" (click)="chooseSession(session)">Przejdź do wyboru</button>
          </section>
        } @else {
          <h2>Na dziś nie ma sesji do rozpoczęcia</h2>
          <p>Wróć do tego widoku, gdy plan udostępni kolejną sesję.</p>
        }
      }

      @if (stage() === 'variant') {
        <section aria-labelledby="variant-title">
          <h2 id="variant-title">Ile chcesz dziś zrobić?</h2>
          <p class="muted">Zakres wybierasz przed rozpoczęciem. Gdy plan nie pokazuje tu treści wariantu, wyświetlamy liczbę kolejnych pozycji do oznaczenia.</p>
          <div class="variant-list" role="list">
            @for (variant of variants; track variant.type) {
              <button type="button" class="variant" [class.selected]="selectedVariant() === variant.type" (click)="selectVariant(variant.type)" role="listitem" [attr.aria-pressed]="selectedVariant() === variant.type">
                <span>{{ variant.name }}</span><small>{{ variant.description }} {{ variantItemCount(variant.type) }}</small>
              </button>
            }
          </div>
          <button mat-flat-button type="button" (click)="showCheckIn()">Kontynuuj</button>
        </section>
      }

      @if (stage() === 'check-in') {
        <section aria-labelledby="check-in-title">
          <h2 id="check-in-title">Krótki check-in</h2>
          <p class="muted">To nie jest diagnoza. Jeśli coś Cię niepokoi, wybierz „Mam problem”.</p>
          <form class="form-grid" [formGroup]="checkInForm" (ngSubmit)="start()">
            <mat-form-field><mat-label>Ból teraz, od 0 do 10</mat-label><input matInput type="number" min="0" max="10" formControlName="painLevel"></mat-form-field>
            <mat-form-field><mat-label>Gotowość, od 1 do 10</mat-label><input matInput type="number" min="1" max="10" formControlName="readinessLevel"></mat-form-field>
            <mat-form-field class="full"><mat-label>Miejsce dyskomfortu (opcjonalnie)</mat-label><input matInput formControlName="painArea" maxlength="120"></mat-form-field>
            <div class="full"><button mat-flat-button type="submit" [disabled]="checkInForm.invalid">Rozpocznij sesję</button></div>
          </form>
        </section>
      }

      @if (stage() === 'guided') {
        <section aria-labelledby="guided-title">
          <h2 id="guided-title">W trakcie sesji</h2>
          <p>Wybrany wariant: {{ variantName(attempt()?.selectedVariantType) }}. Postęp zapisuje się po każdym oznaczeniu.</p>
          @if (guidedItems().length) {
            <ul class="progress-list" aria-label="Postęp sesji">
              @for (item of guidedItems(); track item.id; let index = $index) {
                <li>
                  <span>Krok {{ index + 1 }}{{ doseLabel(item) }}</span>
                  <button mat-stroked-button type="button" (click)="updateProgress(item.id, !item.completed)">{{ item.completed ? 'Cofnij oznaczenie' : 'Oznacz jako zrobione' }}</button>
                </li>
              }
            </ul>
          } @else {
            <p class="muted">Wykonuj wskazówki z zatwierdzonego planu w swoim tempie. Szczegóły kliniczne nie są wyświetlane w tym widoku.</p>
          }
          <div class="session-actions">
            <button mat-flat-button type="button" [disabled]="!hasCompletedProgress()" (click)="finish()">Przejdź do podsumowania</button>
            <button mat-stroked-button type="button" (click)="pause()">Zapisz i wróć później</button>
          </div>
        </section>
      }

      @if (stage() === 'result') {
        <section aria-labelledby="result-title">
          <h2 id="result-title">Sesja zapisana</h2>
          <p>Możesz wrócić do codziennych aktywności. Jeśli coś budzi niepokój, użyj bezpiecznego zgłoszenia poniżej.</p>
          <button mat-flat-button type="button" (click)="returnToToday()">Wróć do dzisiaj</button>
        </section>
      }

      @if (stage() === 'problem') {
        <section aria-labelledby="problem-title">
          <h2 id="problem-title" tabindex="-1">Co się dzieje?</h2>
          @if (!reportedProblem()) {
            <p class="muted">Zatrzymaj się, jeśli potrzebujesz. Wybierz powód — to od razu wyśle bezpieczne zgłoszenie.</p>
            <div class="problem-categories" role="group" aria-label="Wybierz powód zgłoszenia">
              @for (category of problemCategories; track category.value) {
                <button mat-stroked-button type="button" (click)="reportProblem(category.value)" [disabled]="reporting()">{{ category.label }}</button>
              }
            </div>
          } @else {
            <p class="report-confirmation">Zgłoszenie zapisane. {{ safeNextStep(reportedProblem()) }}</p>
            @if (canContactSpecialist()) {
              <button mat-stroked-button type="button" class="contact-specialist" (click)="contactSpecialist()" [disabled]="reporting()">Skontaktuj się ze specjalistą</button>
            }
            <p class="specialist-note">Kontakt jest przekazywany wyłącznie przez bezpieczne zgłoszenie; nie udostępniamy tu prywatnych danych kontaktowych.</p>
            <button type="button" class="problem-link" (click)="closeProblem()">Wróć do sesji</button>
          }
        </section>
      }

      @if (stage() !== 'problem' && stage() !== 'result') {
        <button type="button" class="problem-link" (click)="openProblem()">Mam problem</button>
      }
    </section>
  `,
  styles: [`
    .session-flow { max-width: 720px; margin: 0 auto; font-size: clamp(18px, 1rem + .2vw, 20px); line-height: 1.5; } .session-summary, .variant, .progress-list li { border: 1px solid var(--line); border-radius: 1rem; padding: 1rem; background: #ffffff08; }
    .variant-list, .progress-list { display: grid; gap: .75rem; margin: 1rem 0; padding: 0; } .variant { color: var(--ink); text-align: left; cursor: pointer; min-height: 72px; } .variant.selected { border-color: var(--accent); background: #76d7c422; } .variant span, .variant small { display: block; } .variant small { color: var(--muted); margin-top: .25rem; }
    .progress-list { list-style: none; } .progress-list li { display: flex; justify-content: space-between; gap: 1rem; align-items: center; } .session-actions, .problem-categories { display: flex; flex-wrap: wrap; gap: .75rem; margin-top: 1rem; } .session-actions button { margin: 0; } :host button, :host input, :host [role="button"] { min-width: 44px; min-height: 44px; } :host :is(button, input, [role="button"]):focus-visible { outline: 3px solid var(--accent); outline-offset: 3px; } .problem-link { display: block; margin: 2rem auto 0; color: var(--ink); background: transparent; border: 0; text-decoration: underline; cursor: pointer; } .specialist-note { border-left: 3px solid var(--accent); padding-left: 1rem; } .report-confirmation { font-weight: 600; } .contact-specialist { margin-top: 1rem; }
    @media (prefers-reduced-motion: reduce) { *, *::before, *::after { scroll-behavior: auto !important; transition-duration: .01ms !important; animation-duration: .01ms !important; } } @media (max-width: 480px), (min-width: 320px) and (max-height: 480px) { .progress-list li, .session-actions, .problem-categories { align-items: stretch; flex-direction: column; } :host button { width: 100%; } } @media (min-width: 721px) { .session-flow { padding-inline: 1rem; } }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SessionsPage {
  private readonly api = inject(ApiFacade);
  protected readonly agenda = signal<TodayAgendaView | null>(null);
  protected readonly todaySession = signal<AgendaSessionView | null>(null);
  protected readonly attempt = signal<AttemptDetailView | null>(null);
  protected readonly plannedSessions = signal<SessionView[]>([]);
  protected readonly stage = signal<Stage>('today');
  protected readonly selectedVariant = signal<Variant>('STANDARD');
  protected readonly message = signal('Ładowanie…');
  protected readonly failed = signal(false);
  protected readonly variants = [
    { type: 'STANDARD' as const, name: 'Pełna sesja', description: 'Standardowy wariant z Twojego planu.' },
    { type: 'SHORT' as const, name: 'Krótsza sesja', description: 'Krótszy wariant zatwierdzony w planie, jeśli jest dostępny.' },
    { type: 'MINIMUM' as const, name: 'Minimum na dziś', description: 'Najmniejszy zatwierdzony wariant, jeśli jest dostępny.' }
  ];
  protected readonly checkInForm = new FormGroup({
    painLevel: new FormControl(0, { nonNullable: true, validators: [Validators.min(0), Validators.max(10)] }),
    readinessLevel: new FormControl(5, { nonNullable: true, validators: [Validators.min(1), Validators.max(10)] }),
    painArea: new FormControl('', { nonNullable: true, validators: Validators.maxLength(120) })
  });
  protected readonly reportedProblem = signal<BarrierReportView | null>(null);
  protected readonly reporting = signal(false);
  protected readonly problemCategories = [
    { value: 'PAIN_OR_SYMPTOMS', label: 'Ból lub dyskomfort' },
    { value: 'TOO_DIFFICULT', label: 'Za trudno' },
    { value: 'LOW_MOTIVATION', label: 'Trudno zacząć' },
    { value: 'OTHER', label: 'Inny powód' }
  ];
  private readonly attemptStorageKey = 'moves.participant.active-attempt';

  constructor() { void this.load(); }

  protected durationLabel(session: AgendaSessionView): string { return session.expectedDurationMinutes ? `Około ${session.expectedDurationMinutes} min` : 'Czas zgodny z planem'; }
  protected variantName(type?: string): string { return this.variants.find(item => item.type === type)?.name ?? 'wybrany wariant'; }
  protected variantItemCount(variant: Variant): string { return `(${this.prescriptionsForVariant(variant).length} ${this.prescriptionsForVariant(variant).length === 1 ? 'pozycja' : 'pozycje'})`; }
  protected guidedItems(): Array<PrescriptionView & { completed: boolean }> {
    const detail = this.attempt();
    if (!detail) return [];
    const completed = new Map((detail.progress ?? []).map(item => [item.exercisePrescriptionId, item.completed]));
    return this.prescriptionsForVariant(this.variantFromAttempt(detail)).map(item => ({ ...item, completed: completed.get(item.id) ?? false }));
  }
  protected doseLabel(item: PrescriptionView): string {
    const parts = [item.targetSets && item.targetRepetitions ? `${item.targetSets} × ${item.targetRepetitions}` : '', item.targetDurationSeconds ? `${item.targetDurationSeconds} s` : '', item.targetLoadKg ? `${item.targetLoadKg} kg` : ''].filter(Boolean);
    return parts.length ? ` · ${parts.join(', ')}` : '';
  }
  protected hasCompletedProgress(): boolean { return this.guidedItems().some(item => item.completed); }
  protected chooseSession(session: AgendaSessionView): void {
    this.todaySession.set(session); this.selectedVariant.set('STANDARD');
    if (!this.matchingSession(session)?.prescriptions?.length) { this.missingPrescriptions(); return; }
    this.stage.set('variant'); this.message.set('');
  }
  protected selectVariant(variant: Variant): void { this.selectedVariant.set(variant); }
  protected showCheckIn(): void { this.stage.set('check-in'); }
  protected openProblem(): void { this.reportedProblem.set(null); this.stage.set('problem'); this.message.set(''); }
  protected closeProblem(): void { this.reportedProblem.set(null); this.stage.set(this.attempt() ? 'guided' : 'today'); this.message.set(''); }

  protected async start(): Promise<void> {
    const session = this.todaySession(); const revisionId = this.agenda()?.activePlan?.revisionId;
    if (!session?.sessionId || !revisionId) return this.error('Brakuje danych aktywnego planu. Odśwież widok i spróbuj ponownie.');
    if (!this.matchingSession(session)?.prescriptions?.length) return this.missingPrescriptions();
    try {
      this.failed.set(false);
      await this.api.safety.checkIn({ checkInRequest: this.checkInForm.getRawValue() });
      const started = await this.api.attempts.start({ idempotencyKey: crypto.randomUUID(), startAttemptCommand: { plannedSessionId: session.sessionId, planRevisionId: revisionId, selectedVariantType: this.selectedVariant() } });
      if (!started.attemptId) throw new Error('missing attempt');
      this.persistAttempt(started.attemptId);
      await this.openAttempt(started.attemptId);
      this.message.set('Sesja rozpoczęta. Możesz ją przerwać i wrócić później.');
    } catch { this.error('Nie udało się rozpocząć sesji. Sprawdź check-in albo wybierz dostępny wariant.'); }
  }

  protected async updateProgress(prescriptionId?: string, completed = false): Promise<void> {
    const attemptId = this.attempt()?.attemptId;
    if (!attemptId || !prescriptionId) return;
    try { await this.api.attempts.progress({ attemptId, progressCommand: { exercisePrescriptionId: prescriptionId, completed } }); await this.openAttempt(attemptId); }
    catch { this.error('Nie udało się zapisać postępu. Spróbuj ponownie.'); }
  }

  protected async pause(): Promise<void> {
    const attemptId = this.attempt()?.attemptId; if (!attemptId) return;
    try { await this.api.attempts.pause({ attemptId }); await this.openAttempt(attemptId); this.message.set('Sesja jest zapisana. Możesz wrócić do niej później.'); }
    catch { this.error('Nie udało się wstrzymać sesji. Spróbuj ponownie.'); }
  }

  protected async finish(): Promise<void> {
    const detail = this.attempt(); if (!detail?.attemptId) return;
    try {
      await this.api.attempts.complete({ attemptId: detail.attemptId, idempotencyKey: crypto.randomUUID(), declareExecutionCommand: { declaredCompletion: true, painLevel: this.checkInForm.controls.painLevel.value, difficultyLevel: 5, results: (detail.progress ?? []).filter(item => item.completed && item.exercisePrescriptionId).map(item => ({ exercisePrescriptionId: item.exercisePrescriptionId })) } });
      this.clearAttempt(); this.stage.set('result'); this.message.set('');
    } catch { this.error('Nie udało się zapisać podsumowania. Sprawdź postęp i spróbuj ponownie.'); }
  }

  protected async reportProblem(category: string): Promise<void> {
    this.reporting.set(true);
    try {
      const report = await this.api.barriers.report({ idempotencyKey: crypto.randomUUID(), barrierReportCommand: { plannedSessionId: this.todaySession()?.sessionId, sessionAttemptId: this.attempt()?.attemptId, category } });
      this.reportedProblem.set(report); this.message.set('Zgłoszenie zapisane.'); this.failed.set(false);
    } catch { this.error('Nie udało się wysłać zgłoszenia. Zatrzymaj sesję i spróbuj ponownie później.'); }
    finally { this.reporting.set(false); }
  }

  protected canContactSpecialist(): boolean { return this.reportedProblem()?.proposedOptions?.includes('CONTACT_SPECIALIST') ?? false; }
  protected safeNextStep(report: BarrierReportView | null): string { return report?.proposedOptions?.includes('CONTACT_SPECIALIST') ? 'Możesz też przekazać je specjaliście.' : 'System zaproponował bezpieczną następną opcję zgodnie z planem.'; }
  protected async contactSpecialist(): Promise<void> {
    const report = this.reportedProblem();
    if (!report?.category) return;
    this.reporting.set(true);
    try {
      await this.api.barriers.report({ idempotencyKey: crypto.randomUUID(), barrierReportCommand: { plannedSessionId: this.todaySession()?.sessionId, sessionAttemptId: this.attempt()?.attemptId, category: report.category, selectedAction: 'CONTACT_SPECIALIST' } });
      this.message.set('Prośba o kontakt została bezpiecznie przekazana specjaliście.'); this.failed.set(false);
    } catch { this.error('Nie udało się przekazać prośby o kontakt. Zgłoszenie problemu pozostaje zapisane.'); }
    finally { this.reporting.set(false); }
  }

  protected returnToToday(): void { this.stage.set('today'); void this.load(); }

  private async load(): Promise<void> {
    try {
      this.failed.set(false);
      const [agenda, plannedSessions] = await Promise.all([this.api.today.today(), this.api.planning.sessions()]);
      this.agenda.set(agenda); this.plannedSessions.set(plannedSessions);
      const saved = this.readAttempt();
      if (saved) { await this.openAttempt(saved); this.message.set('Wznowiono zapisaną sesję.'); return; }
      this.todaySession.set(this.agenda()?.sessions?.find(session => session.executionState !== 'COMPLETED') ?? null); this.message.set('');
    } catch { this.error('Nie udało się pobrać dzisiejszego planu. Odśwież stronę i spróbuj ponownie.'); }
  }

  private async openAttempt(attemptId: string): Promise<void> {
    const detail = await this.api.attempts.get({ attemptId });
    const session = this.agenda()?.sessions?.find(item => item.sessionId === detail.plannedSessionId);
    if (!session || !this.matchingSession(session)?.prescriptions?.length) { this.missingPrescriptions(); return; }
    this.todaySession.set(session);
    if (detail.state === 'PAUSED') await this.api.attempts.resume({ attemptId });
    this.attempt.set(await this.api.attempts.get({ attemptId })); this.stage.set('guided'); this.persistAttempt(attemptId);
  }
  private matchingSession(session: AgendaSessionView): SessionView | undefined { return this.plannedSessions().find(item => item.id === session.sessionId); }
  private prescriptionsForVariant(variant: Variant): PrescriptionView[] {
    const prescriptions = [...(this.matchingSession(this.todaySession() ?? {})?.prescriptions ?? [])].sort((left, right) => (left.position ?? 0) - (right.position ?? 0));
    if (variant === 'SHORT') return prescriptions.slice(0, Math.ceil(prescriptions.length / 2));
    return variant === 'MINIMUM' ? prescriptions.slice(0, 1) : prescriptions;
  }
  private variantFromAttempt(detail: AttemptDetailView): Variant { return detail.selectedVariantType === 'SHORT' || detail.selectedVariantType === 'MINIMUM' ? detail.selectedVariantType : 'STANDARD'; }
  private missingPrescriptions(): void {
    this.attempt.set(null); this.clearAttempt(); this.stage.set('problem');
    this.error('Nie możemy bezpiecznie wyświetlić pozycji tej sesji. Nie rozpoczynaj jej — zgłoś problem lub skontaktuj się ze specjalistą.');
  }
  private persistAttempt(attemptId: string): void { sessionStorage.setItem(this.attemptStorageKey, attemptId); }
  private readAttempt(): string | null { return sessionStorage.getItem(this.attemptStorageKey); }
  private clearAttempt(): void { sessionStorage.removeItem(this.attemptStorageKey); }
  private error(message: string): void { this.failed.set(true); this.message.set(message); }
}
