import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import type { CloseoutContext } from '../api/generated/src/models/CloseoutContext';
import type { ParticipantMeasurementCommand } from '../api/generated/src/models/ParticipantMeasurementCommand';
import type { ParticipantMeasurementPresetView } from '../api/generated/src/models/ParticipantMeasurementPresetView';
import { ApiFacade } from '../core/api.facade';
import { ParticipantMeasurementDialogComponent } from './participant-measurement-dialog.component';

type ActingContext = 'TRAINER' | 'PHYSIOTHERAPIST';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, ParticipantMeasurementDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.closeout { max-width: 760px; margin: 24px auto; padding: 0 16px; } .item { border-top: 1px solid #ddd; padding: 16px 0; } .error { color: #b00020; }`],
  template: `<main class="closeout" aria-labelledby="closeout-title"><p aria-live="polite">{{ announcement() }}</p>
    @if (state() === 'loading') { <p>Wczytywanie zakończenia spotkania…</p> } @else if (state() === 'error') { <section role="alert"><h1>Nie udało się otworzyć zakończenia spotkania</h1><button mat-stroked-button type="button" (click)="load()">Spróbuj ponownie</button></section> }
    @if (context(); as data) { <header><button mat-stroked-button type="button" (click)="back(data)">Wróć do uczestnika</button><p>{{ data.participantName || 'Uczestnik' }} · {{ data.status || 'Spotkanie' }}</p><h1 id="closeout-title">Zakończenie spotkania</h1>@if (data.shortPurpose) { <p>{{ data.shortPurpose }}</p> }</header>
      @if (data.status !== 'IN_PROGRESS') { <section class="item"><h2>Spotkanie jest już zakończone</h2><p>Nie można ponownie wykonać zamknięcia.</p><button mat-flat-button type="button" (click)="back(data)">Wróć do uczestnika</button></section> } @else {
        <section class="item"><h2>Realizacja sesji</h2>
          @if (data.executionRequired && !data.executionRecorded) { <p>Realizacja powiązanej sesji jest wymagana przed zakończeniem.</p><button mat-flat-button type="button" (click)="openExecution(data)">Zapisz realizację</button> } @else if (data.executionRecorded && data.execution; as execution) { <p>✓ zapisana{{ execution.outcome ? ' · ' + outcomeLabel(execution.outcome) : '' }}</p> } @else { <p>Nie wymaga realizacji sesji.</p> }
        </section>
        <section class="item"><h2>Pomiary</h2><p>{{ (data.measurements?.length ?? 0) }} zapisane</p>@if (data.measurements?.length) { <ul>@for (measurement of data.measurements; track measurement.measurementId) { <li>{{ measurement.value }} {{ measurement.unit }}</li> }</ul> }<button mat-stroked-button type="button" (click)="openMeasurement(data)">Dodaj pomiar</button><button mat-stroked-button type="button" (click)="measurementSkipped.set(true)">Pomiń</button>@if (measurementSkipped()) { <p>Możesz zakończyć bez dodawania pomiaru.</p> }</section>
        <section class="item"><h2>Podsumowanie</h2>@if (data.summaryNote) { <p>{{ data.summaryNote.title || 'Podsumowanie spotkania' }}</p><p>{{ data.summaryNote.content }}</p> } @else { <label>Krótka notatka (opcjonalnie)<textarea [formControl]="summaryControl" maxlength="2000"></textarea></label> }</section>
        @if (completionError()) { <p class="error" role="alert">{{ completionError() }}</p> }
        <section class="item"><h2>Gotowe do zakończenia</h2><button mat-flat-button type="button" [disabled]="pending() || !data.canComplete" (click)="complete(data)">{{ pending() ? 'Zapisywanie…' : 'Zakończ spotkanie' }}</button></section>
      }
      @if (measurementDialog()) { <app-participant-measurement-dialog [presets]="measurementPresets()" [appointmentId]="data.appointmentId" [loading]="measurementCatalogLoading()" [saving]="savingMeasurement()" [error]="measurementError()" (closed)="closeMeasurement()" (submitted)="recordMeasurement(data, $event)" /> }
    }
  </main>`,
})
export class SpecialistAppointmentCloseoutPage {
  private readonly api = inject(ApiFacade); private readonly route = inject(ActivatedRoute); private readonly router = inject(Router);
  protected readonly context = signal<CloseoutContext | null>(null); protected readonly state = signal<'loading' | 'loaded' | 'error'>('loading');
  protected readonly announcement = signal(''); protected readonly pending = signal(false); protected readonly completionError = signal('');
  protected readonly measurementDialog = signal(false); protected readonly measurementCatalogLoading = signal(false); protected readonly measurementPresets = signal<ParticipantMeasurementPresetView[]>([]); protected readonly savingMeasurement = signal(false); protected readonly measurementError = signal(false); protected readonly measurementSkipped = signal(false);
  readonly summaryControl = new FormControl('', { nonNullable: true, validators: Validators.maxLength(2000) });
  private actingContext: ActingContext | undefined; private noteSaved = false; private noteIdempotencyKey: string | undefined; private completeIdempotencyKey: string | undefined; private measurementIdempotencyKey: string | undefined;
  constructor() { void this.load(); }
  async load(): Promise<void> {
    const appointmentId = this.route.snapshot.paramMap.get('appointmentId'); if (!appointmentId) { this.state.set('error'); return; }
    this.state.set('loading');
    try {
      const onboarding = await this.api.onboarding.state(); const role = onboarding.profile?.specialistKind;
      if (role !== 'TRAINER' && role !== 'PHYSIOTHERAPIST') throw new Error('specialist role unavailable');
      this.actingContext = role;
      const context = await this.api.appointmentCloseout.getSpecialistAppointmentCloseoutContext({ appointmentId, actingContext: role });
      this.context.set(context); this.noteSaved = !!context.summaryNote; this.state.set('loaded');
    } catch { this.state.set('error'); }
  }
  protected outcomeLabel(outcome?: string): string { return ({ COMPLETED: 'wykonano', PARTIAL: 'częściowo wykonano', SKIPPED: 'pominięto' })[outcome ?? ''] ?? 'zapisano'; }
  protected async back(data: CloseoutContext): Promise<void> { if (data.participantId) await this.router.navigate(['/specialist/clients', data.participantId]); }
  protected async openExecution(data: CloseoutContext): Promise<void> { if (data.participantId && data.appointmentId) await this.router.navigate(['/specialist/clients', data.participantId, 'appointments', data.appointmentId, 'session'], { queryParams: { returnTo: 'closeout' } }); }
  protected async openMeasurement(data: CloseoutContext): Promise<void> { if (!data.participantId) return; this.measurementDialog.set(true); this.measurementError.set(false); this.measurementIdempotencyKey = undefined; this.measurementCatalogLoading.set(true); try { this.measurementPresets.set(await this.api.participantMeasurements.participantMeasurementCatalog({ participantId: data.participantId })); } catch { this.measurementError.set(true); } finally { this.measurementCatalogLoading.set(false); } }
  protected closeMeasurement(): void { if (!this.savingMeasurement()) this.measurementDialog.set(false); }
  protected async recordMeasurement(data: CloseoutContext, command: ParticipantMeasurementCommand): Promise<void> { if (!data.participantId || this.savingMeasurement()) return; this.savingMeasurement.set(true); this.measurementError.set(false); this.measurementIdempotencyKey ??= crypto.randomUUID(); try { await this.api.participantMeasurements.recordParticipantMeasurement({ participantId: data.participantId, idempotencyKey: this.measurementIdempotencyKey, participantMeasurementCommand: command }); this.measurementDialog.set(false); await this.load(); } catch { this.measurementError.set(true); } finally { this.savingMeasurement.set(false); } }
  protected async complete(data: CloseoutContext): Promise<void> {
    const participantId = data.participantId;
    const appointmentId = data.appointmentId;
    const version = data.version;
    const actingContext = this.actingContext;
    if (this.pending() || !data.canComplete || !participantId || !appointmentId || typeof version !== 'number' || !actingContext) return;
    this.pending.set(true); this.completionError.set('');
    try {
      const note = this.summaryControl.value.trim();
      if (note && !this.noteSaved) {
        this.noteIdempotencyKey ??= crypto.randomUUID();
        await this.api.participantDocumentation.createParticipantDocumentationNote({ participantId, actingContext, idempotencyKey: this.noteIdempotencyKey, noteRequest: { category: 'APPOINTMENT_SUMMARY', title: 'Podsumowanie spotkania', content: note, appointmentId } });
        this.noteSaved = true;
        await this.load();
      }
      const refreshed = this.context();
      const completionId = refreshed?.appointmentId;
      const completionVersion = refreshed?.version;
      if (!refreshed?.canComplete || !completionId || typeof completionVersion !== 'number') {
        this.completionError.set('Spotkanie zmieniło się. Odświeżono dane zakończenia.');
        return;
      }
      this.completeIdempotencyKey ??= crypto.randomUUID();
      await this.api.appointments.complete({ id: completionId, idempotencyKey: this.completeIdempotencyKey, appointmentVersionCommand: { version: completionVersion } });
      this.announcement.set('Spotkanie zostało zakończone.');
      await this.back(refreshed);
    } catch { this.completionError.set('Nie udało się zakończyć spotkania. Spróbuj ponownie.'); } finally { this.pending.set(false); }
  }
}
