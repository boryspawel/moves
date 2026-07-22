import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import type { EditorView } from '../api/generated/src';
import { ApiFacade } from '../core/api.facade';

@Component({
  selector: 'app-plan-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <section class="panel">
      <h1>Nowy plan</h1>
      <p class="muted">Plan zostanie sprawdzony przed aktywacją. Identyfikatory techniczne nie są wyświetlane.</p>
      <form class="form-grid" [formGroup]="form" (ngSubmit)="build()">
        <mat-form-field><mat-label>Uczestnik</mat-label><mat-select formControlName="participant"><mat-option value="">Wybierz uczestnika</mat-option>@for (participant of participants(); track participant.participantAccountId) { <mat-option [value]="participant.participantAccountId">{{ participant.label }}</mat-option> }</mat-select></mat-form-field>
        <mat-form-field><mat-label>Cel</mat-label><input matInput formControlName="goal"></mat-form-field>
        <mat-form-field><mat-label>Ćwiczenie</mat-label><mat-select formControlName="exercise"><mat-option value="">Wybierz ćwiczenie</mat-option>@for (exercise of exercises(); track exercise.versionId) { <mat-option [value]="exercise.versionId">{{ exercise.canonicalName }}</mat-option> }</mat-select></mat-form-field>
        <mat-form-field><mat-label>Sesja</mat-label><input matInput formControlName="session"></mat-form-field>
        <mat-form-field><mat-label>Dzień sesji</mat-label><input matInput type="date" formControlName="date"></mat-form-field>
        <mat-form-field><mat-label>Dostępna od</mat-label><input matInput type="datetime-local" formControlName="availableFrom"></mat-form-field>
        <mat-form-field><mat-label>Dostępna do</mat-label><input matInput type="datetime-local" formControlName="availableTo"></mat-form-field>
        <mat-form-field><mat-label>Serie</mat-label><input matInput type="number" min="1" formControlName="sets"></mat-form-field>
        <mat-form-field><mat-label>Powtórzenia</mat-label><input matInput type="number" min="1" formControlName="repetitions"></mat-form-field>
        <mat-checkbox formControlName="short">Dodaj wariant skrócony</mat-checkbox>
        <mat-checkbox formControlName="minimum">Dodaj wariant minimum</mat-checkbox>
        <div class="full"><button mat-flat-button type="submit" [disabled]="form.invalid || busy()">Utwórz i sprawdź plan</button></div>
      </form>
      @if (safety().length) { <section class="safety"><h2>Ocena bezpieczeństwa</h2><ul>@for (factor of safety(); track factor) { <li>{{ factor }}</li> }</ul></section> }
      @if (revisionId()) { <mat-checkbox [formControl]="acknowledged">Potwierdzam ostrzeżenia wymagające decyzji</mat-checkbox><button mat-flat-button (click)="activate()" [disabled]="busy() || (!acknowledged.value && safety().length > 0)">Aktywuj bezpiecznie sprawdzony plan</button> }
      <p class="status" aria-live="polite" [class.error]="failed()">{{ message() }}</p>
    </section>`,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PlanPage {
  private readonly facade = inject(ApiFacade);
  protected readonly participants = signal<Array<{ participantAccountId?: string; label?: string }>>([]);
  protected readonly exercises = signal<Array<{ versionId?: string; canonicalName?: string }>>([]);
  protected readonly safety = signal<string[]>([]); protected readonly revisionId = signal('');
  protected readonly message = signal(''); protected readonly failed = signal(false); protected readonly busy = signal(false);
  protected readonly acknowledged = new FormControl(false, { nonNullable: true });
  protected readonly form = new FormGroup({
    participant: new FormControl('', { nonNullable: true, validators: Validators.required }), goal: new FormControl('Sprawniejszy ruch', { nonNullable: true, validators: Validators.required }),
    exercise: new FormControl('', { nonNullable: true, validators: Validators.required }), session: new FormControl('Sesja podstawowa', { nonNullable: true, validators: Validators.required }),
    date: new FormControl(new Date().toISOString().slice(0, 10), { nonNullable: true, validators: Validators.required }), availableFrom: new FormControl('', { nonNullable: true }), availableTo: new FormControl('', { nonNullable: true }),
    sets: new FormControl(3, { nonNullable: true, validators: Validators.min(1) }), repetitions: new FormControl(8, { nonNullable: true, validators: Validators.min(1) }), short: new FormControl(true, { nonNullable: true }), minimum: new FormControl(true, { nonNullable: true })
  });
  constructor() { void this.loadChoices(); }
  private async loadChoices(): Promise<void> { try { const [participants, catalog] = await Promise.all([this.facade.specialistParticipants.activeParticipants(), this.facade.catalog.list({ page: 0, size: 100 })]); this.participants.set(participants); this.exercises.set(catalog.content ?? []); } catch { this.failed.set(true); this.message.set('Nie udało się pobrać uczestników lub katalogu ćwiczeń.'); } }
  protected async build(): Promise<void> {
    if (this.form.invalid) return; this.busy.set(true); this.failed.set(false); this.safety.set([]);
    try {
      const v = this.form.getRawValue(); const context = { role: 'TRAINER' as const };
      let editor = await this.facade.planningV2.createDraft({ createDraftCommand: { participantAccountId: v.participant, name: `Plan: ${v.goal}`, purpose: v.goal, mode: 'SPECIALIST', phaseIntent: v.goal, validFrom: new Date(v.date), validTo: new Date(v.date), actingContext: context } });
      const revision = editor.currentRevisionId!;
      editor = await this.facade.planningV2.addGoal({ revisionId: revision, addGoalCommand: { expectedVersion: this.version(editor), perspective: 'GENERAL_FITNESS', category: 'FUNCTIONAL', title: v.goal, priority: 1, status: 'ACTIVE' } });
      editor = await this.facade.planningV2.addCycle({ revisionId: revision, addCycleCommand: { expectedVersion: this.version(editor), sequenceNumber: 1, name: 'Cykl podstawowy', startDate: new Date(v.date), endDate: new Date(v.date), phaseIntent: v.goal, phaseGoal: v.goal } });
      const cycleId = editor.revision?.cycles?.[0]?.id!;
      editor = await this.facade.planningV2.addMicrocycle({ revisionId: revision, addMicrocycleCommand: { expectedVersion: this.version(editor), cycleId, sequenceNumber: 1, name: 'Tydzień 1', startDate: new Date(v.date), endDate: new Date(v.date), phaseIntent: v.goal, phaseGoal: v.goal } });
      const microcycleId = editor.revision?.cycles?.[0]?.microcycles?.[0]?.id!;
      editor = await this.facade.planningV2.addSession({ revisionId: revision, addSessionCommand: { expectedVersion: this.version(editor), microcycleId, title: v.session, scheduledDate: new Date(v.date), availableFrom: this.instant(v.availableFrom), availableTo: this.instant(v.availableTo), expectedDurationMinutes: 30 } });
      const sessionId = editor.revision?.cycles?.[0]?.microcycles?.[0]?.sessions?.[0]?.id!;
      editor = await this.facade.planningV2.addPrescription({ revisionId: revision, addPrescriptionCommand: { expectedVersion: this.version(editor), sessionId, exerciseVersionId: v.exercise, position: 1, side: 'BILATERAL', doseType: 'DYNAMIC_RESISTANCE', sets: v.sets, repetitions: v.repetitions, intensityType: 'RPE', intensityValue: 5 } });
      const prescriptionId = editor.revision?.cycles?.[0]?.microcycles?.[0]?.sessions?.[0]?.prescriptions?.[0]?.id!;
      editor = await this.variant(revision, editor, sessionId, prescriptionId, 'STANDARD', 30, v.sets, v.repetitions);
      if (v.short) editor = await this.variant(revision, editor, sessionId, prescriptionId, 'SHORT', 15, Math.max(1, v.sets - 1), v.repetitions);
      if (v.minimum) editor = await this.variant(revision, editor, sessionId, prescriptionId, 'MINIMUM', 10, 1, Math.max(1, Math.floor(v.repetitions / 2)));
      await this.facade.planningV2.validateStructurally({ revisionId: revision, validateCommand: { expectedVersion: this.version(editor) } });
      const validation = await this.facade.planWorkflow.validate({ revisionId: revision, validateWorkflowCommand: { expectedVersion: this.version(editor), actingContext: context } });
      this.revisionId.set(revision); this.safety.set((validation.assessment?.factors ?? []).map(factor => factor.explanationCode ?? factor.ruleCode ?? 'Wymaga sprawdzenia'));
      this.message.set('Plan utworzony i sprawdzony. Potwierdź ostrzeżenia przed aktywacją.');
    } catch (error) { this.showError(error); } finally { this.busy.set(false); }
  }
  protected async activate(): Promise<void> { const revision = this.revisionId(); if (!revision) return; this.busy.set(true); this.failed.set(false); try { const context = { role: 'TRAINER' as const }; const status = await this.facade.planWorkflow.status({ revisionId: revision, activateWorkflowCommand: { actingContext: context } }); const warningIds = (status.assessment?.factors ?? []).filter(f => f.result === 'WARNING' && f.overridable).map(f => f.id!).filter(Boolean); if (warningIds.length) await this.facade.planWorkflow.acknowledge({ revisionId: revision, acknowledgeWarningCommand: { factorIds: new Set(warningIds), rationale: 'Potwierdzone po przeglądzie specjalisty.', actingContext: context } }); await this.facade.planWorkflow.activate({ revisionId: revision, idempotencyKey: crypto.randomUUID(), activateWorkflowCommand: { actingContext: context } }); this.message.set('Plan aktywny.'); } catch (error) { this.showError(error); } finally { this.busy.set(false); } }
  private async variant(revisionId: string, editor: EditorView, sessionId: string, prescriptionId: string, type: 'STANDARD' | 'SHORT' | 'MINIMUM', minutes: number, sets: number, repetitions: number): Promise<EditorView> { return this.facade.planningV2.defineSessionVariant({ revisionId, defineSessionVariantCommand: { expectedVersion: this.version(editor), sessionId, type, expectedDurationMinutes: minutes, items: [{ basePrescriptionId: prescriptionId, position: 1, overrideSets: sets, overrideRepetitions: repetitions }] } }); }
  private version(editor: EditorView): number { return editor.revision?.revisionVersion ?? 0; }
  private instant(value: string): Date | undefined { return value ? new Date(value) : undefined; }
  private showError(error: unknown): void { const detail = error instanceof Error ? error.message : ''; this.failed.set(true); this.message.set(detail.includes('409') || detail.includes('conflict') ? 'Plan został zmieniony w innym miejscu. Odśwież dane i spróbuj ponownie.' : detail.includes('safety') ? 'Ocena bezpieczeństwa blokuje aktywację. Przejrzyj problemy.' : 'Nie udało się zapisać planu. Sprawdź dane i aktywną relację.'); }
}
