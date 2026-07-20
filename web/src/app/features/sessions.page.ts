import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { SessionView } from '../api/generated/src';
import { ApiFacade } from '../core/api.facade';

@Component({
  selector: 'app-sessions-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <section class="panel">
      <h1>Moje sesje</h1>
      <p class="muted">Wykonanie jest deklaracją uczestnika. Zgłoszenie bólu nie stanowi diagnozy.</p>
      <p class="status" aria-live="polite" [class.error]="failed()">{{ message() }}</p>
      <ul class="card-list">
        @for (session of sessions(); track session.id) {
          <li>
            <h2>{{ session.title }}</h2>
            <p>{{ session.kind }} · {{ session.status }}</p>
            <ul>
              @for (item of session.prescriptions ?? []; track item.id) {
                <li>Wersja ćwiczenia {{ item.exerciseVersionId }} — {{ item.targetSets ?? '—' }} × {{ item.targetRepetitions ?? '—' }}</li>
              }
            </ul>
            @if (session.status === 'ASSIGNED') {
              <form class="form-grid" [formGroup]="executionForm" (ngSubmit)="declare(session)">
                <mat-form-field><mat-label>Ból 0–10</mat-label><input matInput type="number" min="0" max="10" formControlName="painLevel"></mat-form-field>
                <mat-form-field><mat-label>Trudność 1–10</mat-label><input matInput type="number" min="1" max="10" formControlName="difficultyLevel"></mat-form-field>
                <mat-form-field class="full"><mat-label>Opcjonalna notatka</mat-label><textarea matInput formControlName="note"></textarea></mat-form-field>
                <div class="full"><button mat-flat-button type="submit" [disabled]="executionForm.invalid">Deklaruję wykonanie</button></div>
              </form>
            }
          </li>
        } @empty { <li>Brak przypisanych sesji.</li> }
      </ul>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SessionsPage {
  private readonly api = inject(ApiFacade);
  protected readonly sessions = signal<SessionView[]>([]);
  protected readonly message = signal('Ładowanie…');
  protected readonly failed = signal(false);
  protected readonly executionForm = new FormGroup({
    painLevel: new FormControl(0, { nonNullable: true, validators: [Validators.min(0), Validators.max(10)] }),
    difficultyLevel: new FormControl(5, { nonNullable: true, validators: [Validators.min(1), Validators.max(10)] }),
    note: new FormControl('', { nonNullable: true, validators: Validators.maxLength(500) })
  });
  constructor() { void this.load(); }
  protected async declare(session: SessionView): Promise<void> {
    if (!session.id) return;
    const form = this.executionForm.getRawValue();
    this.failed.set(false);
    try {
      const execution = await this.api.execution.declare({
        sessionId: session.id,
        idempotencyKey: crypto.randomUUID(),
        declareExecutionCommand: {
          declaredCompletion: true,
          painLevel: form.painLevel,
          difficultyLevel: form.difficultyLevel,
          note: form.note || undefined,
          results: (session.prescriptions ?? []).map(item => ({
            exercisePrescriptionId: item.id,
            actualRepetitions: item.targetRepetitions,
            actualDurationSeconds: item.targetDurationSeconds,
            actualLoadKg: item.targetLoadKg
          }))
        }
      });
      let points = '';
      if (execution.id) {
        try {
          const qualification = await this.api.gamification.qualify({ executionId: execution.id, idempotencyKey: crypto.randomUUID() });
          points = ` Punkty: ${qualification.points ?? 0} (${qualification.outcome ?? 'brak'}).`;
        } catch { points = ' Wykonanie zapisano; gamifikacja nie została naliczona.'; }
      }
      this.message.set(`Wykonanie zapisane.${points}`);
      await this.load();
    } catch { this.failed.set(true); this.message.set('Nie udało się zapisać wykonania. Możliwa jest twarda blokada bezpieczeństwa.'); }
  }
  private async load(): Promise<void> {
    try { this.sessions.set(await this.api.planning.sessions()); this.message.set(''); }
    catch { this.failed.set(true); this.message.set('Nie udało się pobrać sesji.'); }
  }
}
