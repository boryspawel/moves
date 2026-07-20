import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { ExecutionView } from '../api/generated/src';
import { ApiFacade } from '../core/api.facade';

@Component({
  selector: 'app-specialist-alerts-page',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <section class="panel">
      <h1>Wykonania i alerty uczestnika</h1>
      <form (ngSubmit)="load()">
        <mat-form-field><mat-label>ID konta uczestnika</mat-label><input matInput [formControl]="participantId"></mat-form-field>
        <button mat-flat-button type="submit" [disabled]="participantId.invalid">Pobierz</button>
      </form>
      <p class="status" aria-live="polite" [class.error]="failed()">{{ message() }}</p>
      <ul class="card-list">
        @for (execution of executions(); track execution.id) {
          <li>
            <h2>Wykonanie {{ execution.recordedAt | date:'medium' }}</h2>
            <p>Ból: {{ execution.painLevel }} · trudność: {{ execution.difficultyLevel }}</p>
            @if (execution.alerts?.length) { <p class="error"><strong>Alert:</strong> {{ execution.alerts?.join(', ') }}</p> }
          </li>
        } @empty { <li>Brak wykonania lub alertu do wyświetlenia.</li> }
      </ul>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SpecialistAlertsPage {
  private readonly api = inject(ApiFacade).execution;
  protected readonly participantId = new FormControl('', { nonNullable: true, validators: Validators.required });
  protected readonly executions = signal<ExecutionView[]>([]);
  protected readonly message = signal('');
  protected readonly failed = signal(false);
  protected async load(): Promise<void> {
    this.failed.set(false);
    try { this.executions.set(await this.api.specialistExecutions({ participantAccountId: this.participantId.value })); this.message.set(`${this.executions().length} wykonań.`); }
    catch { this.failed.set(true); this.message.set('Brak aktywnej relacji lub błąd pobierania.'); }
  }
}
