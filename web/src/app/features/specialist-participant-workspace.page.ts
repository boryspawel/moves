import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatInputModule } from '@angular/material/input';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiFacade } from '../core/api.facade';
import { ResponseError } from '../api/generated/src/runtime';
import type { SpecialistParticipantWorkspaceView } from '../api/generated/src/models/SpecialistParticipantWorkspaceView';
import type { SituationalSignalView } from '../api/generated/src/models/SituationalSignalView';
import type { OperationalFocusView } from '../api/generated/src/models/OperationalFocusView';
import type { ParticipantTimelineEvent } from '../api/generated/src/models/ParticipantTimelineEvent';
import type { AppointmentView } from '../api/generated/src/models/AppointmentView';
import type { ParticipantWorkspaceAppointmentView } from '../api/generated/src/models/ParticipantWorkspaceAppointmentView';
import type { ParticipantGoalView } from '../api/generated/src/models/ParticipantGoalView';
import type { ObservationView } from '../api/generated/src/models/ObservationView';
import type { PresetView } from '../api/generated/src/models/PresetView';
import type { ParticipantMeasurementPresetView } from '../api/generated/src/models/ParticipantMeasurementPresetView';
import type { CreateFromPresetRequestPresetIdEnum, CreateFromPresetRequestTargetComparatorEnum } from '../api/generated/src/models/CreateFromPresetRequest';
import { ParticipantDocumentationComponent, type RecordPanelType } from './participant-documentation.component';
import { ParticipantAccessPanelComponent } from './participant-access-panel.component';
import { ParticipantMeasurementDialogComponent } from './participant-measurement-dialog.component';
import {
  groupEvents,
  rangeDates,
  sortedEvents,
  timelineCategories,
  type TimelineCategory,
  type WorkspaceRange,
  type WorkspaceView,
} from './specialist-participant-workspace.geometry';
import {
  appointmentLocation,
  appointmentPurpose,
  appointmentTypeLabel,
  attentionSummary,
  categoryLabel,
  eventDescription,
  eventTimeLabel,
  goalsSummary,
  humanEventTitle,
  isPastScheduled,
  outcomeMetricLabel,
  realizationSummary,
  statusLabel,
} from './specialist-participant-workspace.presentation';

const actionLabels: Record<string, string> = {
  SCHEDULE_APPOINTMENT: 'Spotkanie',
  ADD_MEASUREMENT: 'Pomiar',
  ADD_NOTE: 'Notatkę',
  ADD_GOAL: 'Cel',
};
const label = (value: string | undefined, labels: Record<string, string>) =>
  value ? (labels[value] ?? value.replace(/_/g, ' ').toLocaleLowerCase('pl-PL')) : 'Brak danych';
type WorkspaceSection = 'overview' | 'plan' | 'documentation' | 'history';
type SituationalSignal = SituationalSignalView;

@Component({
  selector: 'app-participant-workspace-header',
  standalone: true,
  imports: [MatButtonModule, MatMenuModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './participant-workspace-header.component.scss',
  template: `<header class="workspace-header">
    <div>
      <h1 id="workspace-title">{{ workspace.participant?.displayName || 'Klient' }}</h1>
      <div class="header-badges">
        <span>{{ relationshipLabel }}</span
        ><span>{{ accountLabel }}</span>
      </div>
      @if (workspace.nextAppointment?.startsAt) {
        <p>
          Następne spotkanie: <time>{{ nextAppointment }}</time>
        </p>
      } @else {
        <p>Brak nadchodzącego spotkania.</p>
      }
    </div>
    <div class="header-actions">
      <button mat-stroked-button type="button" (click)="accessRequested.emit()">Dostęp</button>
      @if (actions.length) {
        <button mat-flat-button type="button" [matMenuTriggerFor]="addMenu">+ Dodaj</button>
        <mat-menu #addMenu="matMenu">
          @for (action of actions; track action) {
            <button mat-menu-item type="button" (click)="requested.emit(action)">{{ actionLabel(action) }}</button>
          }
        </mat-menu>
      }
    </div>
  </header>`,
})
export class ParticipantWorkspaceHeaderComponent {
  @Input({ required: true }) workspace!: SpecialistParticipantWorkspaceView;
  @Input() accessStatus?: string;
  @Input() accessStatusAvailable = true;
  @Input() actions: string[] = [];
  @Output() requested = new EventEmitter<string>();
  @Output() accessRequested = new EventEmitter<void>();
  protected actionLabel = (action: string) => actionLabels[action] ?? action;
  protected get nextAppointment() {
    return eventTimeLabel({ effectiveFrom: this.workspace.nextAppointment?.startsAt });
  }
  protected get relationshipLabel() {
    return label(this.workspace.relationship?.status, {
      ACTIVE: 'Aktywna współpraca',
      INACTIVE: 'Nieaktywna współpraca',
    });
  }
  protected get accountLabel() {
    return this.accessStatusAvailable
      ? `Konto: ${label(this.accessStatus, { ACTIVE: 'aktywne', INVITED: 'zaproszone', NO_ACCOUNT: 'bez konta', NONE: 'bez konta' })}`
      : 'Status konta niedostępny';
  }
}

@Component({
  selector: 'app-participant-summary-strip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './participant-summary-strip.component.scss',
  template: `<section class="summary-strip" aria-label="Podsumowanie klienta">
    @if (workspace.activePlan) { <div>
      <strong>Aktywny plan</strong
      ><span>{{ workspace.activePlan.name || 'Brak aktywnego planu' }}</span>
    </div> }
    @if (workspace.goals?.length) { <div>
      <strong>Cele</strong><span>{{ goals(workspace.goals.length) }}</span>
    </div> }
    @if (workspace.recentProgress?.latestActivityAt) { <div>
      <strong>Ostatnia aktywność</strong
      ><span>{{
        workspace.recentProgress.latestActivityAt ? 'Zarejestrowana' : 'Brak danych'
      }}</span>
    </div> }
    @if (workspace.activeProblems?.length) { <div>
      <strong>Wymaga reakcji</strong><span>{{ attention(workspace.activeProblems.length) }}</span>
    </div> }
  </section>`,
})
export class ParticipantSummaryStripComponent {
  @Input({ required: true }) workspace!: SpecialistParticipantWorkspaceView;
  protected goals = goalsSummary;
  protected realization = realizationSummary;
  protected attention = attentionSummary;
}

@Component({
  selector: 'app-participant-operational-focus',
  standalone: true,
  imports: [MatButtonModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './specialist-participant-workspace.page.scss',
  template: `<section class="operational-focus" aria-labelledby="operational-focus-title">
    <p class="focus-kicker">Bieżący priorytet</p>
    <h2 id="operational-focus-title">{{ focus?.title || 'Brak pilnych działań' }}</h2>
    <p>{{ focus?.explanation || 'Nie ma obecnie spraw wymagających reakcji.' }}</p>
    @if (focus?.relevantAt) { <p class="focus-time"><time>{{ focus.relevantAt | date: 'medium' : '' : 'pl' }}</time></p> }
    @if (focus?.primaryAction) { <button mat-flat-button type="button" [disabled]="busy" (click)="requested.emit(focus!)">{{ actionLabel(focus!.primaryAction) }}</button> }
  </section>`,
})
export class ParticipantOperationalFocusComponent {
  @Input() focus?: OperationalFocusView;
  @Input() busy = false;
  @Output() requested = new EventEmitter<OperationalFocusView>();
  protected actionLabel(action?: string): string {
    return ({ OPEN_ATTENTION_ITEMS: 'Otwórz sprawę', OPEN_HISTORY: 'Otwórz historię', OPEN_PLAN: 'Otwórz plan', SCHEDULE_APPOINTMENT: 'Zaplanuj spotkanie', START_APPOINTMENT: 'Rozpocznij spotkanie', RECORD_SESSION_EXECUTION: 'Zapisz realizację sesji', CONTINUE_CLOSEOUT: 'Zakończ spotkanie', CONTINUE_INTERVIEW: 'Kontynuuj wywiad' })[action ?? ''] ?? 'Otwórz';
  }
}

@Component({
  selector: 'app-participant-situation',
  standalone: true,
  imports: [MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './specialist-participant-workspace.page.scss',
  template: `<section class="workspace-situation" aria-labelledby="workspace-situation-title">
    <h2 id="workspace-situation-title">Sytuacja</h2>
    @if (signals.length) {
      <ul>
        @for (signal of signals; track $index) {
          <li>
            <strong>{{ signal.title }}</strong>
            @if (signal.description) { <span>{{ signal.description }}</span> }
            @if (signal.action) { <button mat-stroked-button type="button" (click)="requested.emit(signal)">{{ actionLabel(signal.action) }}</button> }
          </li>
        }
      </ul>
    } @else {
      <p>Brak dodatkowych informacji wymagających uwagi.</p>
    }
  </section>`,
})
export class ParticipantSituationComponent {
  @Input() signals: SituationalSignal[] = [];
  @Output() requested = new EventEmitter<SituationalSignal>();
  protected actionLabel(action: string): string {
    return ({ OPEN_ATTENTION_ITEMS: 'Otwórz sprawę', SCHEDULE_APPOINTMENT: 'Zaplanuj spotkanie', OPEN_NEXT_APPOINTMENT: 'Otwórz spotkanie', OPEN_ACTIVE_PLAN: 'Otwórz plan' })[action] ?? 'Otwórz';
  }
}

@Component({
  selector: 'app-patient-timeline-filters',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './patient-timeline-filters.component.scss',
  template: `<section class="timeline-controls" aria-label="Filtry osi czasu">
    <div class="ranges" role="group" aria-label="Zakres czasu">
      @for (item of ranges; track item.key) {
        <button
          type="button"
          [attr.aria-pressed]="range === item.key"
          [class.active]="range === item.key"
          (click)="rangeChange.emit(item.key)"
        >
          {{ item.label }}
        </button>
      }
    </div>
    <button
      class="filter-toggle"
      type="button"
      [attr.aria-expanded]="filtersOpen"
      aria-controls="timeline-filter-chips"
      (click)="filtersOpen = !filtersOpen"
    >
      Filtry{{ selected.length ? ' (' + selected.length + ')' : '' }}
    </button>
    @if (filtersOpen) {
      <div id="timeline-filter-chips" class="filter-chips">
        @for (type of categories; track type) {
          <button
            type="button"
            [attr.aria-pressed]="selected.includes(type)"
            [class.active]="selected.includes(type)"
            (click)="toggle(type)"
          >
            {{ categoryLabel(type) }}
          </button>
        }
        @if (selected.length) {
          <button type="button" (click)="clear.emit()">Wyczyść</button>
        }
      </div>
    }
    <button type="button" (click)="viewChange.emit(view === 'timeline' ? 'list' : 'timeline')">
      {{ view === 'timeline' ? 'Widok listy' : 'Widok osi czasu' }}
    </button>
  </section>`,
})
export class PatientTimelineFiltersComponent {
  @Input() range: WorkspaceRange = '2w';
  @Input() selected: TimelineCategory[] = [];
  @Input() view: WorkspaceView = 'timeline';
  @Output() rangeChange = new EventEmitter<WorkspaceRange>();
  @Output() selectedChange = new EventEmitter<TimelineCategory[]>();
  @Output() clear = new EventEmitter<void>();
  @Output() viewChange = new EventEmitter<WorkspaceView>();
  protected filtersOpen = false;
  protected readonly categories = timelineCategories;
  protected readonly ranges = [
    { key: '2w' as const, label: '2 tyg.' },
    { key: '3m' as const, label: '3 mies.' },
    { key: '12m' as const, label: '12 mies.' },
  ];
  protected categoryLabel = (type: string) =>
    ({ APPOINTMENT: 'Spotkania', SESSION: 'Planowane sesje', EXECUTION: 'Wykonania', MEASUREMENT: 'Pomiary', INTERVIEW: 'Wywiady', NOTE: 'Notatki' })[type] ??
    type;
  protected toggle(type: TimelineCategory) {
    this.selectedChange.emit(
      this.selected.includes(type)
        ? this.selected.filter((item) => item !== type)
        : [...this.selected, type],
    );
  }
}

@Component({
  selector: 'app-timeline-event',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './timeline-event.component.scss',
  template: `<article class="timeline-event">
    <button type="button" [attr.data-event-id]="event.eventId" (click)="opened.emit(event)">
      <span class="event-category">{{ category(event.category) }}</span>
      @if (time(event)) {
        <time>{{ time(event) }}</time>
      }
      <strong>{{ title(event) }}</strong>
      @if (description(event); as description) {
        <span class="event-description">{{ description }}</span>
      }
      @if (status(event); as state) {
        <span class="event-status" [class.stale]="pastScheduled(event)">Status: {{ state }}</span>
      }
    </button>
  </article>`,
})
export class TimelineEventComponent {
  @Input({ required: true }) event!: ParticipantTimelineEvent;
  @Output() opened = new EventEmitter<ParticipantTimelineEvent>();
  protected category = categoryLabel;
  protected description = eventDescription;
  protected pastScheduled = isPastScheduled;
  protected status = statusLabel;
  protected time = eventTimeLabel;
  protected title = humanEventTitle;
}

@Component({
  selector: 'app-timeline-period-group',
  standalone: true,
  imports: [TimelineEventComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './timeline-period-group.component.scss',
  template: `<section class="period-group">
    <h3>{{ group.label }}</h3>
    <ol>
      @for (event of group.items; track event.eventId) {
        <li><app-timeline-event [event]="event" (opened)="opened.emit($event)" /></li>
      }
    </ol>
  </section>`,
})
export class TimelinePeriodGroupComponent {
  @Input({ required: true }) group!: { label: string; items: ParticipantTimelineEvent[] };
  @Output() opened = new EventEmitter<ParticipantTimelineEvent>();
}
@Component({
  selector: 'app-patient-timeline',
  standalone: true,
  imports: [TimelinePeriodGroupComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [':host { display: block; box-sizing: border-box; min-width: 0; width: 100%; }'],
  template: `<section aria-label="Chronologiczna oś czasu">
    @for (group of groups; track group.label) {
      <app-timeline-period-group [group]="group" (opened)="opened.emit($event)" />
    }
    @if (!groups.length) {
      <p>Brak zdarzeń w wybranym zakresie.</p>
    }
  </section>`,
})
export class PatientTimelineComponent {
  @Input() groups: Array<{ label: string; items: ParticipantTimelineEvent[] }> = [];
  @Output() opened = new EventEmitter<ParticipantTimelineEvent>();
}
@Component({
  selector: 'app-patient-timeline-list-view',
  standalone: true,
  imports: [TimelineEventComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './patient-timeline-list-view.component.scss',
  template: `<section aria-label="Widok listy">
    <ol class="timeline-list">
      @for (event of events; track event.eventId) {
        <li><app-timeline-event [event]="event" (opened)="opened.emit($event)" /></li>
      }
    </ol>
  </section>`,
})
export class PatientTimelineListViewComponent {
  @Input() events: ParticipantTimelineEvent[] = [];
  @Output() opened = new EventEmitter<ParticipantTimelineEvent>();
}
@Component({
  selector: 'app-patient-timeline-event-panel',
  standalone: true,
  imports: [MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './patient-timeline-event-panel.component.scss',
  template: `<aside
    class="event-panel"
    role="dialog"
    aria-modal="false"
    aria-labelledby="event-panel-title"
    tabindex="-1"
    (keydown.escape)="closed.emit()"
  >
    <button type="button" aria-label="Zamknij szczegóły zdarzenia" (click)="closed.emit()">
      ×
    </button>
    <p class="event-category">{{ category(event.category) }}</p>
    <h2 id="event-panel-title">{{ title(event) }}</h2>
    @if (outsideRange) {
      <p role="status">Zdarzenie znajduje się poza aktualnie wybranym zakresem historii.</p>
    }
    @if (description(event); as description) {
      <p>{{ description }}</p>
    }
    @if (event.category === 'MEASUREMENT' && event.measurement; as measurement) {
      <section aria-labelledby="measurement-detail-title">
        <h3 id="measurement-detail-title">{{ measurementTitle(event) }}</h3>
        <p><strong>{{ measurement.value }} {{ measurement.unit }}</strong></p>
        <dl><dt>Zmierzono</dt><dd>{{ time(event) }}</dd><dt>Zapisano</dt><dd>{{ recordedTime(event.recordedAt) }}</dd><dt>Zapisano przez</dt><dd>specjalistę</dd>@if (measurement.note) { <dt>Notatka</dt><dd>{{ measurement.note }}</dd> }</dl>
      </section>
    }
    <dl>
      @if (appointmentType(event); as type) {
        <dt>Rodzaj spotkania</dt>
        <dd>{{ type }}</dd>
      }
      @if (time(event)) {
        <dt>Czas zdarzenia</dt>
        <dd>{{ time(event) }}</dd>
      }
      @if (status(event); as state) {
        <dt>Status</dt>
        <dd [class.stale]="pastScheduled(event)">{{ state }}</dd>
      }
      @if (location(event); as location) {
        <dt>Miejsce</dt>
        <dd>{{ location }}</dd>
      }
      @if (purpose(event); as purpose) {
        <dt>Cel spotkania</dt>
        <dd>{{ purpose }}</dd>
      }
    </dl>
    @if (event.category === 'GOAL') {
      <section class="goal-current-snapshot" aria-labelledby="current-goal-title">
        <h3 id="current-goal-title">Aktualne dane celu</h3>
        @if (goalUnavailable) {
          <p role="status">Aktualne dane celu nie są dostępne.</p>
        } @else if (goal) {
          <p>
            <strong>{{ goal.title }}</strong>
          </p>
          <p>{{ goalStatus(goal.status) }} · {{ goalPerspective(goal.category) }}</p>
        } @else {
          <p role="status">Wczytywanie aktualnych danych celu…</p>
        }
      </section>
    }
    @if (event.category === 'APPOINTMENT' && appointment?.availableActions?.includes('COMPLETE')) {
      <button mat-flat-button type="button" [disabled]="saving" (click)="outcome.emit('COMPLETE')">
        Odbyło się
      </button>
    }
    @if (
      event.category === 'APPOINTMENT' && appointment?.availableActions?.includes('MARK_NO_SHOW')
    ) {
      <button
        mat-stroked-button
        type="button"
        [disabled]="saving"
        (click)="outcome.emit('MARK_NO_SHOW')"
      >
        Nieobecność
      </button>
    }
  </aside>`,
})
export class PatientTimelineEventPanelComponent {
  @Input({ required: true }) event!: ParticipantTimelineEvent;
  @Input() appointment: AppointmentView | null = null;
  @Input() goal: ParticipantGoalView | null = null;
  @Input() goalUnavailable = false;
  @Input() saving = false;
  @Input() outsideRange = false;
  @Input() participantDisplayName?: string;
  @Output() closed = new EventEmitter<void>();
  @Output() outcome = new EventEmitter<'COMPLETE' | 'MARK_NO_SHOW'>();
  protected appointmentType = appointmentTypeLabel;
  protected category = categoryLabel;
  protected description = eventDescription;
  protected goalPerspective = (value?: string) => goalPerspective[value ?? ''] ?? 'Brak danych';
  protected goalStatus = (value?: string) => goalStatus[value ?? ''] ?? 'Brak danych';
  protected location = appointmentLocation;
  protected measurementTitle = humanEventTitle;
  protected pastScheduled = isPastScheduled;
  protected purpose = appointmentPurpose;
  protected recordedTime = (value?: Date) => value ? new Intl.DateTimeFormat('pl-PL', { dateStyle: 'medium', timeStyle: 'short' }).format(value) : 'Brak danych';
  protected status = statusLabel;
  protected time = eventTimeLabel;
  protected title = humanEventTitle;
}

@Component({
  selector: 'app-schedule-appointment-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './schedule-appointment-dialog.component.scss',
  template: `<section
    class="appointment-dialog"
    role="dialog"
    aria-modal="true"
    aria-labelledby="appointment-dialog-title"
  >
    <form [formGroup]="form" (ngSubmit)="submit()">
      <button
        class="dialog-close"
        type="button"
        aria-label="Zamknij planowanie spotkania"
        (click)="closed.emit()"
      >
        ×
      </button>
      <h2 id="appointment-dialog-title">Zaplanuj spotkanie</h2>
      <label
        >Termin rozpoczęcia<input
          type="datetime-local"
          formControlName="startsAt"
          required /></label
      ><label
        >Długość spotkania (min)<input type="number" min="1" formControlName="durationMinutes" required /></label
      >@if (form.controls.durationMinutes.invalid && form.controls.durationMinutes.touched) {
        <p role="alert">Podaj dodatnią liczbę całkowitą minut.</p>
      }<p class="readonly-summary">Koniec: {{ endLabel }}</p><label
        >Rodzaj spotkania<select formControlName="type">
          <option value="TRAINING">Trening</option>
          <option value="PHYSIOTHERAPY">Fizjoterapia</option>
          <option value="ASSESSMENT">Ocena</option>
          <option value="CONSULTATION">Konsultacja</option>
        </select></label
      ><label
        >Forma<select formControlName="locationMode">
          <option value="IN_PERSON">Na miejscu</option>
          <option value="REMOTE">Zdalnie</option>
          <option value="PHONE">Telefonicznie</option>
        </select></label
      ><label
        >Miejsce lub link (opcjonalnie)<input formControlName="location" maxlength="200" /></label
      ><label
        >Krótki cel spotkania (opcjonalnie)<input formControlName="shortPurpose" maxlength="300"
      /></label>
      @if (error) {
        <p role="alert">Nie udało się zaplanować spotkania. Sprawdź dane i spróbuj ponownie.</p>
      }
      <div>
        <button mat-stroked-button type="button" (click)="closed.emit()">Anuluj</button
        ><button mat-flat-button type="submit" [disabled]="form.invalid || saving">
          {{ saving ? 'Zapisywanie…' : 'Zaplanuj' }}
        </button>
      </div>
    </form>
  </section>`,
})
export class ScheduleAppointmentDialogComponent {
  @Input() saving = false;
  @Input() error = false;
  @Output() closed = new EventEmitter<void>();
  @Output() submitted = new EventEmitter<{
    startsAt: string;
    endsAt: string;
    type: string;
    locationMode: string;
    location: string;
    shortPurpose: string;
  }>();
  protected readonly form = new FormGroup({
    startsAt: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    durationMinutes: new FormControl(50, { nonNullable: true, validators: [Validators.required, Validators.min(1), control => Number.isFinite(control.value) && Number.isInteger(control.value) ? null : { positiveInteger: true }] }),
    type: new FormControl('TRAINING', { nonNullable: true }),
    locationMode: new FormControl('IN_PERSON', { nonNullable: true }),
    location: new FormControl('', { nonNullable: true }),
    shortPurpose: new FormControl('', { nonNullable: true }),
  });
  protected get endLabel(): string {
    const end = this.endInstant();
    return end ? new Intl.DateTimeFormat('pl-PL', { dateStyle: 'short', timeStyle: 'short' }).format(end) : '—';
  }
  protected submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const value = this.form.getRawValue();
    const end = this.endInstant();
    if (!end) { this.form.markAllAsTouched(); return; }
    this.submitted.emit({ ...value, endsAt: end.toISOString() });
  }
  private endInstant(): Date | null {
    const value = this.form.getRawValue();
    const startsAt = new Date(value.startsAt);
    const endsAt = new Date(startsAt.getTime() + value.durationMinutes * 60_000);
    return Number.isFinite(endsAt.getTime()) && endsAt > startsAt ? endsAt : null;
  }
}

const goalStatus: Record<string, string> = {
  ACTIVE: 'Aktywny',
  ACHIEVED: 'Cel osiągnięty',
  CANCELLED: 'Anulowany',
};
const goalPerspective: Record<string, string> = {
  PERFORMANCE: 'Wynik sportowy',
  FUNCTIONAL: 'Powrót do funkcji',
  FUNCTIONAL_RECOVERY: 'Powrót do funkcji',
};
const progress: Record<string, string> = {
  NO_DATA: 'Brak pomiaru',
  IN_PROGRESS: 'W trakcie',
  TARGET_REACHED: 'Wartość docelowa osiągnięta',
  NOT_COMPARABLE: 'Brak porównania',
};
const comparator: Record<string, string> = { AT_LEAST: 'co najmniej', AT_MOST: 'nie więcej niż' };
@Component({
  selector: 'app-participant-goals',
  standalone: true,
  imports: [MatButtonModule, MatInputModule, ReactiveFormsModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './participant-goals.component.scss',
  styles: [`.goal-card.mat-mdc-outlined-button{display:flex;flex-direction:column;align-items:stretch;min-width:0;gap:var(--space-2);text-align:left}.goal-card.mat-mdc-outlined-button .goal-card-title,.goal-card.mat-mdc-outlined-button .goal-card-target,.goal-card.mat-mdc-outlined-button .goal-card-observation{display:block;min-width:0;text-align:left}`],
  template: `<section class="goals-workspace" aria-labelledby="goals-title">
    <div class="goals-heading">
      <div>
        <h2 id="goals-title">CELE</h2>
      </div>
      @if (role) {
        <button mat-flat-button type="button" (click)="openCreate()">Dodaj cel</button>
      }
    </div>
    @if (!role) {
      <p role="status">Cele są obecnie niedostępne dla tego kontekstu pracy.</p>
    } @else if (state() === 'loading') {
      <p role="status">Wczytywanie celów…</p>
    } @else if (state() === 'error') {
      <p role="alert">Nie udało się wczytać celów. Spróbuj ponownie za chwilę.</p>
    } @else {
      @if (!active().length) {
        <p>Brak aktywnych celów.</p>
      } @else {
        <div class="goal-list">
          @for (goal of active(); track goal.id) {
            <article class="goal-row">
              <button type="button" class="goal-row-main" (click)="open(goal)">
                <strong>{{ goal.title }}</strong>
                @if (primaryOutcome(goal); as outcome) {
                  <span>{{ outcomeSummary(outcome) }}</span>
                  <span>{{ outcomeState(outcome) }}</span>
                }
              </button>
              @if (missingMeasurementAction(goal); as outcome) {
                <button mat-stroked-button type="button" (click)="requestMeasurementFor(outcome)">Dodaj pomiar</button>
              }
            </article>
          }
        </div>
      }
      @if (completed().length) {
        <details class="completed-goals">
          <summary>Zakończone cele ({{ completed().length }}) <span>Pokaż</span></summary>
          <div class="goal-list">
            @for (goal of completed(); track goal.id) {
              <button type="button" class="goal-row-main completed-goal" (click)="open(goal)"><strong>{{ goal.title }}</strong></button>
            }
          </div>
        </details>
      }
    }
    @if (creating()) {
      <section
        class="goal-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="new-goal-title"
        (keydown.escape)="closeCreate()"
      >
        <form [formGroup]="createForm" (ngSubmit)="create()">
          <button
            mat-icon-button
            class="dialog-close"
            type="button"
            aria-label="Zamknij dodawanie celu"
            [disabled]="mutating()"
            (click)="closeCreate()"
          >
            ×
          </button>
          <h2 id="new-goal-title">Dodaj cel</h2>
          <p aria-live="polite">Krok {{ createStep() }} z 2 · {{ perspectiveLabel() }}</p>
          @if (createStep() === 1) {
            <div class="preset-tiles" role="list" aria-label="Typ celu">
              @for (preset of presets(); track preset.id) {
                <button mat-stroked-button type="button" role="listitem" [attr.aria-pressed]="selectedPreset()?.id === preset.id" (click)="choosePreset(preset)">{{ preset.label }}</button>
              }
            </div>
          } @else if (selectedPreset(); as preset) {
            <p><strong>{{ preset.label }}</strong> · {{ perspectiveLabel() }}</p>
            @if (requires(preset, 'BODY_AREA')) { <label>Obszar ciała<select formControlName="bodyArea"><option value="waist">Talia</option><option value="hips">Biodra</option><option value="chest">Klatka piersiowa</option><option value="arm">Ramię</option><option value="thigh">Udo</option><option value="calf">Łydka</option><option value="neck">Szyja</option><option value="other">Inny</option></select></label> }
            @if (requires(preset, 'CUSTOM_LABEL')) { <label>Nazwa miary<input formControlName="customLabel" maxlength="120" /></label> }
            @if (requires(preset, 'EXERCISE')) { <label>Ćwiczenie<input formControlName="exercise" maxlength="120" /></label> }
            @if (requires(preset, 'ACTIVITY')) { <label>Aktywność<input formControlName="activity" maxlength="120" /></label> }
            @if (preset.baselineSupported) { <label>Wartość początkowa<input type="text" formControlName="baselineInput" required [attr.placeholder]="isTime(preset) ? 'mm:ss' : ''" /></label> }
            <label>Wartość docelowa<input type="text" formControlName="targetInput" required [attr.placeholder]="isTime(preset) ? 'mm:ss' : ''" /></label>
            @if (preset.allowedUnits?.length) { <label>Jednostka<select formControlName="unit">@for (unit of preset.allowedUnits; track unit) { <option [value]="unit">{{ unit }}</option> }</select></label> } @else { <label>Jednostka<input formControlName="unit" required /></label> }
            @if (preset.comparatorSelectable) { <label>Porównanie<select formControlName="targetComparator"><option value="AT_LEAST">co najmniej</option><option value="AT_MOST">nie więcej niż</option></select></label> }
          }
          @if (formError()) {
            <p role="alert">Nie udało się zapisać celu. Spróbuj ponownie.</p>
          }
          <div>
            @if (createStep() === 2) { <button mat-stroked-button type="button" [disabled]="mutating()" (click)="createStep.set(1)">Wstecz</button> }
            <button
              mat-stroked-button
              type="button"
              [disabled]="mutating()"
              (click)="closeCreate()"
            >
              Anuluj</button
            ><button mat-flat-button type="submit" [disabled]="!selectedPreset() || createStep() === 1 || mutating()">
              Zapisz cel
            </button>
          </div>
        </form>
      </section>
    }
    @if (selected(); as goal) {
      <aside
        class="goal-panel"
        role="dialog"
        aria-modal="false"
        tabindex="-1"
        aria-labelledby="goal-panel-title"
        (keydown.escape)="close()"
      >
        <header class="goal-panel-header">
          <div>
            <h2 id="goal-panel-title">{{ panelMode() === 'view' ? goal.title : panelMode() === 'edit' ? 'Edytuj cel' : 'Dodaj pomiar' }}</h2>
            <p>{{ status(goal.status) }} · {{ perspective(goal.category) }}</p>
          </div>
          <button mat-icon-button class="goal-panel-close" type="button" aria-label="Zamknij szczegóły celu" (click)="close()">
            <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m6 6 12 12M18 6 6 18" /></svg>
          </button>
        </header>
        @if (panelMode() === 'view') {
          <div class="goal-panel-body">
            @if (eventContext) {
              <p class="goal-event-context">Zdarzenie na osi czasu: {{ eventContext }}</p>
            }
            <section class="goal-panel-section goal-current-state" aria-labelledby="goal-current-title">
              <h3 id="goal-current-title">Aktualny stan celu</h3>
              @for (outcome of orderedOutcomes(goal); track outcome.id) {
                <article class="goal-outcome-state">
                  @if (orderedOutcomes(goal).length > 1) { <h4>{{ outcomeTitle(goal, outcome) }}</h4> }
                  @if (outcome.latestObservation) {
                    <p class="goal-current-value"><strong>{{ outcome.latestObservation.value }} {{ outcome.unit }}</strong><span>aktualnie</span></p>
                    <p class="goal-value-story">{{ outcome.baseline != null ? outcome.baseline + ' ' + outcome.unit + ' → ' : '' }}{{ outcome.latestObservation.value }} {{ outcome.unit }} → cel {{ targetText(outcome) }}</p>
                  } @else {
                    <p class="goal-value-story">Cel {{ targetText(outcome) }}</p>
                    @if (outcome.baseline != null) { <p>Wartość początkowa: {{ outcome.baseline }} {{ outcome.unit }}</p> }
                    <p class="goal-no-current">Brak aktualnego pomiaru</p>
                  }
                  <p class="goal-progress-copy">{{ progressStory(outcome.progress?.state ?? outcome.progressState) }}</p>
                  @if (outcome.latestObservation?.measuredAt) { <p class="goal-last-measurement">Ostatni pomiar: {{ outcome.latestObservation?.measuredAt | date: 'd MMM, HH:mm' : '' : 'pl' }}</p> }
                </article>
              }
            </section>
            @if (isMutable(goal) && has('RECORD_OBSERVATION')) {
              @if (measurementOutcomes(goal).length) {
                <section class="goal-primary-action" aria-label="Dodawanie pomiaru">
                  @if (measurementOutcomes(goal).length > 1) { <label>Wynik<select [value]="measurementOutcomeId()" (change)="chooseMeasurementOutcome($any($event.target).value)">@for (outcome of measurementOutcomes(goal); track outcome.id) { <option [value]="outcome.id">{{ outcomeTitle(goal, outcome) }}</option> }</select></label> }
                  <button mat-flat-button type="button" (click)="requestMeasurement()">Dodaj pomiar</button>
                </section>
              } @else {
                <details class="goal-legacy-observation"><summary>Dodaj pomiar dla tego celu</summary><button mat-stroked-button type="button" (click)="panelMode.set('observation')">Dodaj pomiar</button></details>
              }
            }
            <section class="goal-panel-section" aria-labelledby="goal-history-title">
              <h3 id="goal-history-title">Historia pomiarów</h3>
              @if (historyLoading()) {
                <p role="status">Wczytywanie historii pomiarów…</p>
              } @else if (historyError()) {
                <p role="alert">Nie udało się wczytać historii pomiarów.</p>
                <button mat-stroked-button type="button" (click)="loadHistory()">Spróbuj ponownie</button>
              } @else if (history().length) {
                <ol class="goal-observation-history">
                  @for (observation of history(); track observation.id) {
                    <li><strong>{{ observation.value }} {{ observation.unit }}</strong><span>{{ historyOutcomeLabel(goal, observation) }}</span><span>{{ observation.measuredAt | date: 'd MMM, HH:mm' : '' : 'pl' }}</span>@if (observation.note) { <span>{{ observation.note }}</span> }</li>
                  }
                </ol>
              } @else {
                <p>Brak zapisanych pomiarów.</p>
              }
            </section>
            <details class="goal-secondary-details"><summary>Szczegóły celu</summary>@if (goal.description) { <p>{{ goal.description }}</p> } @if (goal.targetDate) { <p>Termin: {{ goal.targetDate | date: 'longDate' : '' : 'pl' }}</p> }</details>
            @if (isMutable(goal) && (has('UPDATE') || has('ACHIEVE') || has('CANCEL'))) {
              <details class="goal-panel-lifecycle-actions"><summary>Działania dotyczące celu</summary><div>@if (has('UPDATE')) { <button mat-stroked-button type="button" (click)="panelMode.set('edit')">Edytuj cel</button> } @if (has('ACHIEVE')) { <button mat-stroked-button type="button" [disabled]="mutating()" (click)="confirm.set('ACHIEVE')">Oznacz jako osiągnięty</button> } @if (has('CANCEL')) { <button mat-stroked-button type="button" [disabled]="mutating()" (click)="confirm.set('CANCEL')">Anuluj cel</button> }</div></details>
            }
          </div>
        } @else if (panelMode() === 'edit' && isMutable(goal) && has('UPDATE')) {
          <form class="goal-panel-form" [formGroup]="updateForm" (ngSubmit)="update()">
            <label><span>Kategoria</span><input matInput formControlName="category" readonly /></label>
            <label><span>Tytuł</span><input matInput formControlName="title" maxlength="160" required /></label>
            <label><span>Opis</span><textarea matInput formControlName="description"></textarea></label>
            <label><span>Priorytet</span><input matInput
                type="number"
                formControlName="priority"
                min="1"
                max="100"
                required
                aria-describedby="update-priority-error" /></label>
            >@if (updateForm.controls.priority.invalid && updateForm.controls.priority.touched) {
              <p id="update-priority-error" role="alert">Priorytet musi mieścić się w zakresie od 1 do 100.</p>
            }
            <label><span>Termin docelowy</span><input matInput type="date" formControlName="targetDate" /></label>
            <div class="goal-form-actions">
              <button mat-stroked-button type="button" [disabled]="mutating()" (click)="showView()">Anuluj</button>
              <button mat-flat-button type="submit" [disabled]="updateForm.invalid || mutating()">Zapisz metadane</button>
            </div>
          </form>
        } @else if (panelMode() === 'observation' && isMutable(goal) && has('RECORD_OBSERVATION')) {
          <form class="goal-panel-form" [formGroup]="observationForm" (ngSubmit)="record()">
            <label><span>Wynik</span><select formControlName="outcomeId">
                @for (outcome of goal.outcomes ?? []; track outcome.id) {
                  <option [value]="outcome.id">
                    {{ outcomeLabel(outcome.metricCode) }} ({{ outcome.unit }})
                  </option>
                }
              </select></label>
            <label><span>Wartość</span><input matInput type="number" formControlName="value" /></label>
            <label><span>Data i czas</span><input matInput
                type="datetime-local"
                formControlName="measuredAt"
                [max]="latestMeasurementAt" /></label>
            <label><span>Notatka</span><input matInput formControlName="note" /></label>
            <label><span>Źródło dowodu</span><input matInput formControlName="evidenceSource" /></label>
            <div class="goal-form-actions">
              <button mat-stroked-button type="button" [disabled]="mutating()" (click)="showView()">Anuluj</button>
              <button mat-flat-button type="submit" [disabled]="observationForm.invalid || mutating()">Zapisz pomiar</button>
            </div>
          </form>
        }
        @if (confirm(); as action) {
          <section role="alertdialog" aria-label="Potwierdzenie">
            <p>{{ action === 'ACHIEVE' ? 'Oznaczyć cel jako osiągnięty?' : 'Anulować cel?' }}</p>
            <button mat-flat-button type="button" [disabled]="mutating()" (click)="finish(action)">Potwierdź</button
            ><button mat-stroked-button type="button" [disabled]="mutating()" (click)="confirm.set(null)">Wróć</button>
          </section>
        }
      </aside>
    }
  </section>`,
})
export class ParticipantGoalsComponent {
  private readonly api = inject(ApiFacade);
  private refreshRequest = 0;
  private selectionRequest = 0;
  private historyRequest = 0;
  @Input({ required: true }) participantId!: string;
  @Input() role?: 'TRAINER' | 'PHYSIOTHERAPIST';
  @Input() selectedGoalId?: string;
  @Input() measurementRefresh = 0;
  @Input() set createGoalRequested(value: number) { if (value && this.participantId && this.role) this.openCreate(); }
  @Input() eventContext?: string;
  @Output() changed = new EventEmitter<void>();
  @Output() measurementRequested = new EventEmitter<{ presetId?: string; bodyArea?: string }>();
  protected readonly goals = signal<ParticipantGoalView[]>([]);
  protected readonly state = signal<'loading' | 'loaded' | 'error'>('loading');
  protected readonly selected = signal<ParticipantGoalView | null>(null);
  protected readonly panelMode = signal<'view' | 'edit' | 'observation'>('view');
  protected readonly creating = signal(false);
  protected readonly mutating = signal(false);
  protected readonly formError = signal(false);
  protected readonly confirm = signal<'ACHIEVE' | 'CANCEL' | null>(null);
  protected readonly history = signal<ObservationView[]>([]);
  protected readonly historyLoading = signal(false);
  protected readonly historyError = signal(false);
  protected readonly measurementOutcomeId = signal('');
  protected readonly createStep = signal<1 | 2>(1);
  protected readonly presets = signal<PresetView[]>([]);
  protected readonly selectedPreset = signal<PresetView | null>(null);
  protected readonly createForm = new FormGroup({
    bodyArea: new FormControl('waist', { nonNullable: true }),
    customLabel: new FormControl('', { nonNullable: true }),
    exercise: new FormControl('', { nonNullable: true }),
    activity: new FormControl('', { nonNullable: true }),
    baselineInput: new FormControl('', { nonNullable: true, validators: Validators.required }),
    targetInput: new FormControl('', { nonNullable: true, validators: Validators.required }),
    unit: new FormControl('', { nonNullable: true }),
    targetComparator: new FormControl<CreateFromPresetRequestTargetComparatorEnum>('AT_LEAST', { nonNullable: true }),
  });
  protected readonly observationForm = new FormGroup({
    outcomeId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    value: new FormControl<number | null>(null, Validators.required),
    measuredAt: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    note: new FormControl('', { nonNullable: true }),
    evidenceSource: new FormControl('', { nonNullable: true }),
  });
  protected readonly updateForm = new FormGroup({
    category: new FormControl('', { nonNullable: true }),
    title: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    description: new FormControl('', { nonNullable: true }),
    priority: new FormControl(1, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(1), Validators.max(100)],
    }),
    targetDate: new FormControl('', { nonNullable: true }),
  });
  protected readonly latestMeasurementAt = new Date().toISOString().slice(0, 16);
  protected readonly active = computed(() =>
    this.goals().filter((goal) => goal.status === 'ACTIVE'),
  );
  protected readonly completed = computed(() =>
    this.goals().filter((goal) => goal.status !== 'ACTIVE'),
  );
  protected status = (value?: string) => goalStatus[value ?? ''] ?? 'Brak danych';
  protected perspective = (value?: string) => goalPerspective[value ?? ''] ?? 'Brak danych';
  protected progressLabel = (value?: string) => progress[value ?? ''] ?? 'Brak danych';
  protected hasMeaningfulProgress = (value?: string) => value === 'IN_PROGRESS' || value === 'TARGET_REACHED';
  protected outcomeLabel = outcomeMetricLabel;
  protected comparatorLabel = (value?: string) => comparator[value ?? ''] ?? 'Brak';
  protected primaryOutcome(goal: ParticipantGoalView) { return goal.outcomes?.[0]; }
  protected outcomeSummary(outcome: NonNullable<ParticipantGoalView['outcomes']>[number]): string {
    const target = `cel ${outcome.targetComparator === 'AT_MOST' ? '≤' : '≥'} ${outcome.targetValue ?? '—'} ${outcome.unit ?? ''}`.trim();
    return outcome.latestObservation && !this.isMissingCurrentMeasurement(outcome)
      ? `${outcome.latestObservation.value} ${outcome.unit ?? ''} → ${target}`.trim()
      : target;
  }
  protected outcomeState(outcome: NonNullable<ParticipantGoalView['outcomes']>[number]): string {
    const state = outcome.progress?.state ?? outcome.progressState;
    if (this.isMissingCurrentMeasurement(outcome)) return 'Brak aktualnego pomiaru';
    return ({ PROGRESSING: 'Postęp zgodny z celem', MOVING_AWAY: 'Wynik oddala się od celu', UNCHANGED: 'Wynik bez zmiany', TARGET_REACHED: 'Cel osiągnięty', IN_PROGRESS: 'W trakcie realizacji', NOT_COMPARABLE: 'Brak porównania' } as Record<string, string>)[state ?? ''] ?? 'Ostatni pomiar';
  }
  private isMissingCurrentMeasurement(outcome: NonNullable<ParticipantGoalView['outcomes']>[number]) {
    return outcome.progress?.state === 'BASELINE_ONLY' || !outcome.latestObservation;
  }
  protected missingMeasurementAction(goal: ParticipantGoalView) {
    const outcome = this.primaryOutcome(goal);
    return outcome && this.isMissingCurrentMeasurement(outcome) && this.measurementContext(outcome) && goal.availableActions?.includes('RECORD_OBSERVATION') ? outcome : undefined;
  }
  protected measurementContext(outcome: NonNullable<ParticipantGoalView['outcomes']>[number]) {
    const metric = outcome.metricCode?.trim();
    if (metric === 'body-weight' && outcome.unit === 'kg') return { presetId: 'BODY_WEIGHT' };
    const circumference = /^body-circumference:(WAIST|HIPS|CHEST|ARM|THIGH|CALF|NECK|OTHER)$/.exec(metric ?? '');
    return circumference && outcome.unit === 'cm' ? { presetId: 'BODY_CIRCUMFERENCE', bodyArea: circumference[1].toLocaleLowerCase('en-US') } : undefined;
  }
  ngOnChanges() {
    void this.refresh();
    if (this.measurementRefresh && this.selected()) void this.refreshSelectedGoal();
  }
  protected perspectiveLabel() { return this.role === 'TRAINER' ? 'Wynik sportowy' : 'Powrót do funkcji'; }
  private context() {
    return this.role! as never;
  }
  protected async refresh() {
    if (!this.role || !this.participantId) return;
    const request = ++this.refreshRequest;
    const participantId = this.participantId;
    const role = this.role;
    this.state.set('loading');
    try {
      const goals = await this.api.participantGoals.listParticipantGoals({
        participantId,
        actingContext: role as never,
      });
      if (request !== this.refreshRequest || participantId !== this.participantId || role !== this.role) return;
      this.goals.set(goals);
      this.state.set('loaded');
      const selected = this.goals().find((goal) => goal.id === this.selectedGoalId);
      if (selected && this.selected()?.id !== selected.id) void this.open(selected);
    } catch {
      if (request === this.refreshRequest && participantId === this.participantId && role === this.role) {
        this.state.set('error');
      }
    }
  }
  protected openCreate() {
    this.formError.set(false);
    this.createStep.set(1);
    this.selectedPreset.set(null);
    void this.loadPresets();
    this.creating.set(true);
  }
  protected closeCreate() {
    if (!this.mutating()) this.creating.set(false);
  }
  protected choosePreset(preset: PresetView) {
    this.selectedPreset.set(preset);
    this.createForm.controls.unit.setValue(preset.allowedUnits?.[0] ?? '');
    this.createForm.controls.targetComparator.setValue(preset.defaultComparator === 'AT_MOST' ? 'AT_MOST' : 'AT_LEAST');
    this.createStep.set(2);
  }
  protected requires(preset: PresetView, field: string) { return preset.requiredContextFields?.includes(field) ?? false; }
  protected isTime(preset: PresetView) { return preset.allowedUnits?.includes('s') ?? false; }
  private async loadPresets() {
    if (!this.role) return;
    try { this.presets.set(await this.api.participantGoals.listParticipantGoalMetricPresets({ participantId: this.participantId, actingContext: this.context() })); }
    catch { this.formError.set(true); }
  }
  protected async create() {
    const preset = this.selectedPreset();
    if (!this.role || !preset || this.createForm.invalid) return;
    this.mutating.set(true);
    this.formError.set(false);
    const value = this.createForm.getRawValue();
    try {
      const targetValue = this.numericValue(value.targetInput, preset);
      const baselineValue = this.numericValue(value.baselineInput, preset);
      if (!Number.isFinite(targetValue) || targetValue <= 0 || !Number.isFinite(baselineValue)) throw new Error('invalid values');
      const presetId = this.presetId(preset);
      await this.api.participantGoals.createParticipantGoalFromPreset({
        participantId: this.participantId,
        actingContext: this.context(),
        idempotencyKey: crypto.randomUUID(),
        createFromPresetRequest: {
          presetId,
          bodyArea: value.bodyArea || undefined,
          customLabel: value.customLabel || undefined,
          exercise: value.exercise || undefined,
          activity: value.activity || undefined,
          baselineValue,
          targetValue,
          unit: value.unit || undefined,
          targetComparator: value.targetComparator,
        },
      });
      this.creating.set(false);
      await this.refresh();
      this.changed.emit();
    } catch {
      this.formError.set(true);
    } finally {
      this.mutating.set(false);
    }
  }
  private presetId(preset: PresetView): CreateFromPresetRequestPresetIdEnum {
    const id = preset.id;
    if (!id || !['BODY_WEIGHT', 'BODY_CIRCUMFERENCE', 'MAX_LOAD', 'DISTANCE', 'COMPLETION_TIME', 'HOLD_DURATION', 'CUSTOM'].includes(id)) throw new Error('invalid preset');
    return id as CreateFromPresetRequestPresetIdEnum;
  }
  private numericValue(value: string, preset: PresetView): number { return this.isTime(preset) ? this.seconds(value) : Number(value); }
  private seconds(value: string): number {
    const text = value.trim();
    if (/^\d+$/.test(text)) return Number(text);
    const match = /^(?:(\d+)\s*min\s*)?(\d{1,2}):(\d{2})$/.exec(text);
    if (!match || Number(match[3]) > 59) return NaN;
    return (Number(match[1] ?? 0) + Number(match[2])) * 60 + Number(match[3]);
  }
  protected async open(goal: ParticipantGoalView) {
    if (!this.role || !goal.id) return;
    const request = ++this.selectionRequest;
    this.historyRequest++;
    const participantId = this.participantId;
    const role = this.role;
    try {
      const detail = await this.api.participantGoals.getParticipantGoal({
        participantId,
        goalId: goal.id,
        actingContext: role as never,
      });
      if (request !== this.selectionRequest || participantId !== this.participantId || role !== this.role) return;
      this.selected.set(detail);
      this.measurementOutcomeId.set(this.measurementOutcomes(detail)[0]?.id ?? '');
      this.panelMode.set('view');
      this.confirm.set(null);
      this.updateForm.setValue({
        category: this.perspective(detail.category),
        title: detail.title ?? '',
        description: detail.description ?? '',
        priority: detail.priority ?? 1,
        targetDate: detail.targetDate ? detail.targetDate.toISOString().slice(0, 10) : '',
      });
      this.observationForm.controls.outcomeId.setValue(detail.outcomes?.[0]?.id ?? '');
      this.history.set([]);
      this.historyError.set(false);
      void this.loadHistory();
    } catch {
      if (request === this.selectionRequest && participantId === this.participantId && role === this.role) {
        this.selected.set(null);
      }
    }
  }
  protected close() {
    this.selectionRequest++;
    this.historyRequest++;
    this.selected.set(null);
    this.panelMode.set('view');
    this.confirm.set(null);
  }
  private async refreshSelectedGoal() {
    const selected = this.selected();
    if (!selected?.id || !this.role) return;
    const request = ++this.selectionRequest;
    this.historyRequest++;
    const participantId = this.participantId;
    const role = this.role;
    const goalId = selected.id;
    try {
      const detail = await this.api.participantGoals.getParticipantGoal({ participantId, goalId, actingContext: role as never });
      if (request !== this.selectionRequest || participantId !== this.participantId || role !== this.role || goalId !== this.selected()?.id) return;
      this.selected.set(detail);
      this.goals.update((goals) => goals.map((goal) => goal.id === detail.id ? detail : goal));
      void this.loadHistory();
    } catch {
      // Keep the current detail visible when a background refresh fails.
    }
  }
  protected showView() {
    if (!this.mutating()) this.panelMode.set('view');
  }
  protected isMutable(goal: ParticipantGoalView) {
    return goal.status === 'ACTIVE';
  }
  protected has(action: string) {
    return this.selected()?.availableActions?.includes(action) ?? false;
  }
  protected async loadHistory() {
    const goal = this.selected();
    if (!goal?.id || !this.role) return;
    const request = ++this.historyRequest;
    const participantId = this.participantId;
    const goalId = goal.id;
    const role = this.role;
    this.historyLoading.set(true);
    this.historyError.set(false);
    try {
      const page = await this.api.participantGoals.listParticipantGoalObservations({
        participantId,
        goalId,
        actingContext: role as never,
        limit: 20,
      });
      if (request !== this.historyRequest || participantId !== this.participantId || goalId !== this.selected()?.id) return;
      this.history.set(page.items?.slice().sort((left, right) =>
        (right.measuredAt?.getTime() ?? 0) - (left.measuredAt?.getTime() ?? 0),
      ) ?? []);
    } catch {
      if (request === this.historyRequest && participantId === this.participantId && goalId === this.selected()?.id) {
        this.historyError.set(true);
      }
    } finally {
      if (request === this.historyRequest && participantId === this.participantId && goalId === this.selected()?.id) {
        this.historyLoading.set(false);
      }
    }
  }
  protected orderedOutcomes(goal: ParticipantGoalView) {
    return goal.outcomes ?? [];
  }
  protected outcomeTitle(goal: ParticipantGoalView, outcome: NonNullable<ParticipantGoalView['outcomes']>[number]) {
    return this.orderedOutcomes(goal).indexOf(outcome) === 0 ? goal.title : this.outcomeLabel(outcome.metricCode);
  }
  protected historyOutcomeLabel(goal: ParticipantGoalView, observation: ObservationView) {
    const outcome = this.orderedOutcomes(goal).find((item) => item.id === observation.outcomeId);
    return outcome ? this.outcomeTitle(goal, outcome) : goal.title ?? 'Cel';
  }
  protected targetText(outcome: NonNullable<ParticipantGoalView['outcomes']>[number]) {
    const sign = outcome.targetComparator === 'AT_MOST' ? '≤' : '≥';
    return `${sign} ${outcome.targetValue ?? '—'} ${outcome.unit ?? ''}`.trim();
  }
  protected progressStory(value?: string) {
    return ({
      TARGET_REACHED: 'Cel osiągnięty',
      PROGRESSING: 'Postęp zgodny z celem',
      IN_PROGRESS: 'W trakcie realizacji',
      MOVING_AWAY: 'Wynik oddala się od celu',
      UNCHANGED: 'Wynik bez zmiany',
      NOT_COMPARABLE: 'Nie można jeszcze ocenić postępu od wartości początkowej.',
      BASELINE_ONLY: 'Nie można jeszcze ocenić postępu od wartości początkowej.',
    } as Record<string, string>)[value ?? ''] ?? 'Brak aktualnego pomiaru';
  }
  protected measurementOutcomes(goal: ParticipantGoalView) {
    return this.orderedOutcomes(goal).filter((outcome) => !!this.measurementContext(outcome));
  }
  protected chooseMeasurementOutcome(outcomeId: string) {
    this.measurementOutcomeId.set(outcomeId);
  }
  protected requestMeasurement() {
    const goal = this.selected();
    const outcome = goal && this.measurementOutcomes(goal).find((item) => item.id === this.measurementOutcomeId());
    const context = outcome && this.measurementContext(outcome);
    if (context) this.measurementRequested.emit(context);
  }
  protected requestMeasurementFor(outcome: NonNullable<ParticipantGoalView['outcomes']>[number]) {
    const context = this.measurementContext(outcome);
    if (context) this.measurementRequested.emit(context);
  }
  protected async update() {
    const goal = this.selected();
    if (!goal?.id || !this.role || !this.has('UPDATE') || this.updateForm.invalid) return;
    const value = this.updateForm.getRawValue();
    await this.mutate(
      () =>
        this.api.participantGoals.updateParticipantGoal({
          participantId: this.participantId,
          goalId: goal.id!,
          actingContext: this.context(),
          idempotencyKey: crypto.randomUUID(),
          updateParticipantGoalRequest: {
            title: value.title,
            description: value.description || undefined,
            priority: value.priority,
            targetDate: value.targetDate ? new Date(value.targetDate) : undefined,
            expectedVersion: goal.version ?? 0,
          },
        }),
      'Cel został zaktualizowany.',
    );
  }
  protected async record() {
    const goal = this.selected();
    const value = this.observationForm.getRawValue();
    const measuredAt = new Date(value.measuredAt);
    if (!goal?.id || !this.role || this.observationForm.invalid || measuredAt > new Date()) return;
    const recorded = await this.mutate(
      () =>
        this.api.participantGoals.recordParticipantGoalObservation({
          participantId: this.participantId,
          goalId: goal.id!,
          actingContext: this.context(),
          idempotencyKey: crypto.randomUUID(),
          participantGoalObservationRequest: {
            outcomeId: value.outcomeId,
            value: value.value!,
            measuredAt,
            note: value.note || undefined,
            evidenceSource: value.evidenceSource || undefined,
          },
        }),
      'Pomiar został zapisany.',
    );
    if (recorded) await this.loadHistory();
  }
  protected async finish(action: 'ACHIEVE' | 'CANCEL') {
    const goal = this.selected();
    if (!goal?.id || !this.role) return;
    await this.mutate(
      () =>
        action === 'ACHIEVE'
          ? this.api.participantGoals.achieveParticipantGoal({
              participantId: this.participantId,
              goalId: goal.id!,
              actingContext: this.context(),
              idempotencyKey: crypto.randomUUID(),
              participantGoalVersionRequest: { expectedVersion: goal.version ?? 0 },
            })
          : this.api.participantGoals.cancelParticipantGoal({
              participantId: this.participantId,
              goalId: goal.id!,
              actingContext: this.context(),
              idempotencyKey: crypto.randomUUID(),
              participantGoalVersionRequest: { expectedVersion: goal.version ?? 0 },
            }),
      action === 'ACHIEVE' ? 'Cel został oznaczony jako osiągnięty.' : 'Cel został anulowany.',
    );
  }
  private async mutate(call: () => Promise<any>, success: string): Promise<boolean> {
    this.mutating.set(true);
    try {
      const updated = await call();
      this.selected.set(updated.goal ?? updated);
      this.panelMode.set('view');
      this.confirm.set(null);
      await this.refresh();
      this.changed.emit();
      return true;
    } catch (error) {
      const status = error instanceof ResponseError ? error.response.status : 0;
      if (status === 409 && this.selected()) await this.open(this.selected()!);
      else if (status === 404) this.close();
      return false;
    } finally {
      this.mutating.set(false);
    }
  }
}

@Component({
  selector: 'app-specialist-participant-workspace-page',
  standalone: true,
  imports: [
    ParticipantWorkspaceHeaderComponent,
    ParticipantSummaryStripComponent,
    ParticipantOperationalFocusComponent,
    ParticipantSituationComponent,
    ParticipantGoalsComponent,
    ParticipantDocumentationComponent,
    ParticipantAccessPanelComponent,
    PatientTimelineFiltersComponent,
    PatientTimelineComponent,
    PatientTimelineListViewComponent,
    PatientTimelineEventPanelComponent,
    ScheduleAppointmentDialogComponent,
    ParticipantMeasurementDialogComponent,
    DatePipe,
    RouterLink,
  ],
  styleUrl: './specialist-participant-workspace.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<main
    class="workspace"
    [class.event-selected]="!!selected()"
    [attr.aria-busy]="state() === 'loading'"
  >
    <p class="sr-only" aria-live="polite">{{ announcement() }}</p>
    @if (state() === 'loading') {
      <section class="state-card" role="status">
        <h1>Wczytywanie kartoteki…</h1>
        <p>Przygotowujemy bieżący obraz pracy z klientem.</p>
      </section>
    } @else if (state() === 'error') {
      <section class="state-card" role="alert">
        <h1>Nie udało się wczytać kartoteki</h1>
        <p>Spróbuj ponownie za chwilę.</p>
        <button mat-stroked-button type="button" (click)="reload()">Spróbuj ponownie</button>
      </section>
      @if (participantId() && actingContext()) {
        <details class="participant-access" open>
          <summary>Dostęp uczestnika</summary>
          <app-participant-access-panel [participantId]="participantId()" [role]="actingContext()!" />
        </details>
      }
    } @else if (workspace(); as data) {
      <app-participant-workspace-header
        [workspace]="data"
        [accessStatus]="accessStatus()"
        [accessStatusAvailable]="accessStatusAvailable()"
        [actions]="safeActions()"
        (requested)="perform($event)"
        (accessRequested)="accessOpen.set(!accessOpen())"
      />
      @if (participantId() && actingContext() && accessOpen()) {
        <details class="participant-access" [open]="accessOpen()">
          <summary>Dostęp uczestnika</summary>
          <app-participant-access-panel [participantId]="participantId()" [role]="actingContext()!" />
        </details>
      }
      <app-participant-summary-strip [workspace]="data" />
      <nav class="workspace-sections" aria-label="Sekcje kartoteki">
        <button type="button" [attr.aria-pressed]="section() === 'overview'" (click)="openSection('overview')">Przegląd</button>
        <button type="button" [attr.aria-pressed]="section() === 'plan'" (click)="openSection('plan')">Plan</button>
        <button type="button" [attr.aria-pressed]="section() === 'documentation'" (click)="openSection('documentation')">Dokumentacja</button>
        <button type="button" [attr.aria-pressed]="section() === 'history'" (click)="openSection('history')">Historia</button>
      </nav>
      @if (section() === 'overview') {
        <section class="workspace-overview" aria-label="Przegląd kartoteki">
          <div class="workspace-now">
            <app-participant-operational-focus [focus]="focus(data)" [busy]="startingAppointment()" (requested)="performFocus($event)" />
            @if (linkedSessionAppointment(data); as appointment) {
              <section class="linked-session-context" aria-label="Kontekst spotkania">
                <strong>Powiązana sesja</strong>
                <span>{{ appointment.plannedSessionTitle || appointment.plannedSessionId }}</span>
                @if (appointment.planId && appointment.revisionId) {
                  <a [routerLink]="['/specialist/clients', participantId(), 'plans', appointment.planId, 'revisions', appointment.revisionId]">Otwórz plan</a>
                }
              </section>
            }
            @if (data.recentMeasurements?.length) {
              <section class="recent-measurements" aria-labelledby="recent-measurements-title">
                <h2 id="recent-measurements-title">Ostatnie pomiary</h2>
                <ul>@for (measurement of data.recentMeasurements; track measurement.measurementId) { <li><strong>{{ measurement.label || 'Pomiar' }}</strong><span>{{ measurement.value }} {{ measurement.unit }}</span><time>{{ measurementTime(measurement.measuredAt) }}</time></li> }</ul>
              </section>
            }
          </div>
          <app-participant-situation [signals]="situationalSignals(data)" (requested)="performSignal($event)" />
        </section>
      } @else if (section() === 'plan') {
        <section class="workspace-plan" aria-label="Plan uczestnika">
          <section class="training-plan" aria-labelledby="plan-title">
            <h2 id="plan-title">PLAN TRENINGOWY</h2>
            @if (data.activePlan; as plan) {
              @if (plan.planId && plan.activeRevisionId) {
              <div class="active-plan-summary">
                <div><h3>{{ plan.name || 'Plan treningowy' }}</h3><p>Aktywny</p></div>
                @if (plan.nextPlannedSession) { <p><strong>Następna sesja:</strong> {{ plan.nextPlannedSession.title || 'Zaplanowana sesja' }}@if (plan.nextPlannedSession.scheduledAt) { · {{ plan.nextPlannedSession.scheduledAt | date: 'longDate' : '' : 'pl' }} }</p> }
                @if (plan.activeSessionCount) { <p>{{ plan.activeSessionCount }} zaplanowanych sesji</p> }
                @if (plan.lastCompletedSession?.completedAt) { <p>Ostatnia ukończona sesja: {{ plan.lastCompletedSession.completedAt | date: 'longDate' : '' : 'pl' }}</p> }
                <a mat-flat-button [routerLink]="['/specialist/clients', participantId(), 'plans', plan.planId, 'revisions', plan.activeRevisionId]">Otwórz plan</a>
              </div>
              } @else {
                <div class="empty-plan">
                  <h3>Brak aktywnego planu</h3>
                  <p>Utwórz plan treningowy, aby uporządkować kolejne sesje.</p>
                  <a mat-flat-button [routerLink]="['/specialist/clients', participantId(), 'plans', 'new']">Utwórz plan</a>
                  <a class="quiet-link" [routerLink]="['/specialist/clients', participantId(), 'plans']">Wszystkie plany</a>
                </div>
              }
            } @else {
              <div class="empty-plan">
                <h3>Brak aktywnego planu</h3>
                <p>Utwórz plan treningowy, aby uporządkować kolejne sesje.</p>
                <a mat-flat-button [routerLink]="['/specialist/clients', participantId(), 'plans', 'new']">Utwórz plan</a>
                <a class="quiet-link" [routerLink]="['/specialist/clients', participantId(), 'plans']">Wszystkie plany</a>
              </div>
            }
          </section>
          <app-participant-goals [participantId]="participantId()" [role]="actingContext()" [selectedGoalId]="goalId()" [createGoalRequested]="goalCreateRequest()" [measurementRefresh]="measurementRefresh()" (changed)="reload()" (measurementRequested)="openMeasurement($event)" />
        </section>
      } @else if (section() === 'documentation') {
        <section class="workspace-disclosure" aria-label="Dokumentacja uczestnika">
          <app-participant-documentation [participantId]="participantId()" [role]="actingContext()" [panelType]="recordPanelType()" [panelId]="recordPanelId()" [panelMode]="recordPanelMode()" [createNoteRequested]="noteCreateRequest()" (opened)="openRecord($event)" (closed)="closeRecord()" (changed)="reload()" />
        </section>
      } @else if (section() === 'history') {
      <section class="workspace-content">
        <section class="workspace-timeline" aria-labelledby="workspace-overview-title">
          <h2 id="workspace-overview-title">Historia współpracy</h2>
          <app-patient-timeline-filters
            [range]="range()"
            [selected]="types()"
            [view]="view()"
            (rangeChange)="setRange($event)"
            (selectedChange)="setTypes($event)"
            (clear)="setTypes([])"
            (viewChange)="setView($event)"
          />
          @if (view() === 'timeline') {
            <app-patient-timeline [groups]="groups()" (opened)="open($event)" />
          } @else {
            <app-patient-timeline-list-view [events]="events()" (opened)="open($event)" />
          }
          @if (nextCursor()) {
            <button class="older" type="button" (click)="older()">Pokaż wcześniejsze</button>
          }
        </section>
        @if (selected(); as event) {
          <app-patient-timeline-event-panel
            [event]="event"
            [appointment]="currentAppointment()"
            [goal]="currentGoal()"
            [goalUnavailable]="currentGoalUnavailable()"
            [saving]="savingOutcome()"
            [outsideRange]="selectedOutsideRange()"
            [participantDisplayName]="data.participant?.displayName"
            (closed)="close()"
            (outcome)="recordOutcome($event)"
          />
        }
      </section>
      }
      @if (scheduling()) {
        <app-schedule-appointment-dialog
          [saving]="savingAppointment()"
          [error]="appointmentError()"
          (closed)="closeSchedule()"
          (submitted)="schedule($event)"
        />
      }
      @if (measurementDialog()) {
        <app-participant-measurement-dialog [presets]="measurementPresets()" [requestedPresetId]="measurementRequest()?.presetId" [requestedBodyArea]="measurementRequest()?.bodyArea" [loading]="measurementCatalogLoading()" [saving]="savingMeasurement()" [error]="measurementError()" (closed)="closeMeasurement()" (submitted)="recordMeasurement($event)" />
      }
    }
  </main>`,
})
export class SpecialistParticipantWorkspacePage {
  private readonly api = inject(ApiFacade);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);
  private request = 0;
  private opener: HTMLElement | null = null;
  private timelineContext: string | null = null;
  protected readonly participantId = signal('');
  protected readonly actingContext = signal<'TRAINER' | 'PHYSIOTHERAPIST' | undefined>(undefined);
  protected readonly state = signal<'loading' | 'loaded' | 'error'>('loading');
  protected readonly workspace = signal<SpecialistParticipantWorkspaceView | null>(null);
  protected readonly events = signal<ParticipantTimelineEvent[]>([]);
  protected readonly nextCursor = signal<string | undefined>(undefined);
  protected readonly range = signal<WorkspaceRange>('2w');
  protected readonly types = signal<TimelineCategory[]>([]);
  protected readonly view = signal<WorkspaceView>('timeline');
  protected readonly selected = signal<ParticipantTimelineEvent | null>(null);
  protected readonly goalId = signal<string | undefined>(undefined);
  protected readonly announcement = signal('');
  protected readonly accessStatus = signal<string | undefined>(undefined);
  protected readonly accessStatusAvailable = signal(true);
  protected readonly accessOpen = signal(false);
  protected readonly noteCreateRequest = signal(0);
  protected readonly goalCreateRequest = signal(0);
  protected readonly accessNeedsAction = computed(() => this.accessStatusAvailable() && !!this.accessStatus() && this.accessStatus() !== 'ACTIVE');
  protected readonly scheduling = signal(false);
  protected readonly measurementDialog = signal(false);
  protected readonly measurementRequest = signal<{ presetId?: string; bodyArea?: string } | null>(null);
  protected readonly measurementRefresh = signal(0);
  protected readonly measurementCatalogLoading = signal(false);
  protected readonly measurementPresets = signal<ParticipantMeasurementPresetView[]>([]);
  protected readonly savingMeasurement = signal(false);
  protected readonly measurementError = signal(false);
  private measurementIdempotencyKey: string | undefined;
  protected readonly savingAppointment = signal(false);
  protected readonly appointmentError = signal(false);
  protected readonly currentAppointment = signal<AppointmentView | null>(null);
  protected readonly currentGoal = signal<ParticipantGoalView | null>(null);
  protected readonly currentGoalUnavailable = signal(false);
  protected readonly savingOutcome = signal(false);
  protected readonly startingAppointment = signal(false);
  protected readonly selectedOutsideRange = signal(false);
  protected readonly recordPanelType = signal<RecordPanelType | null>(null);
  protected readonly recordPanelId = signal<string | null>(null);
  protected readonly recordPanelMode = signal<'view' | 'edit'>('view');
  protected readonly section = signal<WorkspaceSection>('overview');
  protected readonly groups = computed(() =>
    groupEvents(this.events(), rangeDates(this.range()).granularity),
  );
  protected readonly safeActions = computed(() =>
    (this.workspace()?.quickActions ?? []).filter(
      (action) => !!actionLabels[action],
    ),
  );
  constructor() {
    this.route.queryParamMap.subscribe((params) => {
      if (params.get('sessionRecorded') === '1') this.announcement.set('Realizacja sesji została zapisana.');
      const range = params.get('range');
      const view = params.get('view');
      this.range.set(range === '3m' || range === '12m' ? range : '2w');
      this.view.set(view === 'list' ? 'list' : 'timeline');
      this.types.set(
        (params.get('types') ?? '')
          .split(',')
          .filter((type): type is TimelineCategory =>
            (timelineCategories as readonly string[]).includes(type),
          ),
      );
      const id = params.get('eventId');
      this.goalId.set(params.get('goalId') ?? undefined);
      const recordType = params.get('recordType');
      this.recordPanelType.set(recordType === 'interview' || recordType === 'note' ? recordType : null);
      this.recordPanelId.set(params.get('recordId'));
      this.recordPanelMode.set(params.get('recordMode') === 'edit' ? 'edit' : 'view');
      const requestedSection = params.get('section');
      this.section.set(
        id ? 'history' : this.goalId() ? 'plan' : this.recordPanelType() ? 'documentation'
          : requestedSection === 'plan' || requestedSection === 'documentation' || requestedSection === 'history'
            ? requestedSection : 'overview',
      );
      const participantId = this.route.snapshot.paramMap.get('participantId');
      if (!participantId) return;
      this.participantId.set(participantId);
      void this.loadActingContext();
      const context = `${participantId}:${this.range()}:${this.types().join(',')}:${this.view()}`;
      if (this.timelineContext === context && this.state() === 'loaded')
        void this.resolveSelection(participantId, id, this.events());
      else {
        this.timelineContext = context;
        void this.load(participantId, id);
      }
    });
  }
  protected focus(data: SpecialistParticipantWorkspaceView): OperationalFocusView | undefined {
    return data.focus;
  }
  protected situationalSignals(data: SpecialistParticipantWorkspaceView): SituationalSignal[] {
    return (data.situationalSignals ?? []).slice(0, 4);
  }
  protected linkedSessionAppointment(data: SpecialistParticipantWorkspaceView): ParticipantWorkspaceAppointmentView | undefined {
    const appointment = data.nextAppointment;
    return appointment?.plannedSessionId && appointment.appointmentId === data.focus?.appointmentId
      ? appointment
      : undefined;
  }
  protected openSection(section: WorkspaceSection) {
    const cleared: Record<string, string | null> = section === 'overview'
      ? { eventId: null, goalId: null, recordType: null, recordId: null, recordMode: null }
      : section === 'plan'
      ? { eventId: null, recordType: null, recordId: null, recordMode: null }
      : section === 'documentation'
        ? { eventId: null, goalId: null }
        : { goalId: null, recordType: null, recordId: null, recordMode: null };
    void this.navigate({ section, ...cleared });
  }
  private async loadActingContext(): Promise<void> {
    const onboarding = await this.api.onboarding.state().catch(() => undefined);
    const kind = onboarding?.profile?.specialistKind;
    this.actingContext.set(kind === 'TRAINER' || kind === 'PHYSIOTHERAPIST' ? kind : undefined);
  }
  protected async reload(): Promise<void> {
    const id = this.route.snapshot.paramMap.get('participantId');
    if (id) await this.load(id, this.route.snapshot.queryParamMap.get('eventId'));
  }
  private async load(
    participantId: string,
    selectedId: string | null,
  ): Promise<ParticipantTimelineEvent[] | null> {
    const request = ++this.request;
    this.state.set('loading');
    const dates = rangeDates(this.range());
    const clientRequest = this.api.specialistClients.list1();
    void clientRequest
      .then((items) => {
        if (request === this.request) {
          this.accessStatus.set(items.find((item) => item.participantId === participantId)?.accessStatus);
          this.accessStatusAvailable.set(true);
        }
      })
      .catch(() => {
        if (request === this.request) this.accessStatusAvailable.set(false);
      });
    try {
      const [workspace, timeline, , onboarding] = await Promise.all([
        this.api.participantWorkspace.workspace({ participantId }),
        this.api.participantWorkspace.timeline({
          participantId,
          from: dates.from,
          to: dates.to,
          types: this.types().join(',') || undefined,
          granularity: dates.granularity,
          limit: 100,
        }),
        clientRequest.catch(() => undefined),
        this.api.onboarding.state().catch(() => undefined),
      ]);
      if (request !== this.request) return null;
      const items = sortedEvents(timeline.items ?? []);
      const kind = onboarding?.profile?.specialistKind;
      this.actingContext.set(kind === 'TRAINER' || kind === 'PHYSIOTHERAPIST' ? kind : undefined);
      this.workspace.set(workspace);
      this.events.set(items);
      this.nextCursor.set(timeline.nextCursor);
      this.state.set('loaded');
      await this.resolveSelection(participantId, selectedId, items, request);
      return items;
    } catch {
      if (request === this.request) this.state.set('error');
      return null;
    }
  }
  protected setRange(range: WorkspaceRange) {
    void this.navigate({ range, eventId: null });
  }
  protected setTypes(types: TimelineCategory[]) {
    void this.navigate({ types: types.length ? types.join(',') : null, eventId: null });
  }
  protected setView(view: WorkspaceView) {
    void this.navigate({ view });
  }
  protected open(event: ParticipantTimelineEvent) {
    this.opener = document.activeElement as HTMLElement | null;
    const type = event.category === 'INTERVIEW' ? 'interview' : event.category === 'NOTE' ? 'note' : null;
    void this.navigate({
      eventId: event.eventId ?? null,
      section: 'history',
      recordType: type,
      recordId: type ? event.detail?.referenceId ?? null : null,
      recordMode: null,
    });
    this.focusPanel();
  }
  protected close() {
    const event = this.selected();
    void this.navigate(event?.category === 'INTERVIEW' || event?.category === 'NOTE'
      ? { section: 'history', eventId: null, recordType: null, recordId: null, recordMode: null }
      : { section: 'history', eventId: null });
    queueMicrotask(() => {
      if (this.opener?.isConnected) this.opener.focus();
    });
  }
  protected openRecord(record: { type: RecordPanelType; id: string; mode?: 'view' | 'edit' }) {
    this.opener = document.activeElement as HTMLElement | null;
    void this.navigate({ section: 'documentation', recordType: record.type, recordId: record.id, recordMode: record.mode === 'edit' ? 'edit' : null });
    this.focusRecordPanel();
  }
  protected closeRecord() {
    void this.navigate({ eventId: null, recordType: null, recordId: null, recordMode: null });
    queueMicrotask(() => { if (this.opener?.isConnected) this.opener.focus(); });
  }
  protected async recordOutcome(action: 'COMPLETE' | 'MARK_NO_SHOW'): Promise<void> {
    const event = this.selected();
    const appointment = this.currentAppointment();
    const appointmentId = this.appointmentId(event);
    if (!event || !appointment || !appointmentId || this.savingOutcome()) return;
    this.savingOutcome.set(true);
    try {
      const request = {
        id: appointmentId,
        idempotencyKey: crypto.randomUUID(),
        appointmentVersionCommand: { version: appointment.version },
      };
      if (action === 'COMPLETE') await this.api.appointments.complete(request);
      else await this.api.appointments.noShow(request);
      const participantId = this.route.snapshot.paramMap.get('participantId');
      const items = participantId ? await this.load(participantId, null) : null;
      const newest = items?.find((item) => this.appointmentId(item) === appointmentId);
      if (participantId && newest?.eventId) {
        await this.navigate({ eventId: newest.eventId });
        await this.resolveSelection(participantId, newest.eventId, items ?? []);
      } else {
        this.close();
        this.announcement.set('Wynik spotkania zapisano.');
      }
    } catch (error) {
      if (this.status(error) === 409) {
        await this.loadCurrentAppointment(event);
        this.announcement.set('Bieżące spotkanie zmieniło się i zostało odświeżone.');
      } else this.announcement.set('Nie udało się zapisać wyniku spotkania. Spróbuj ponownie.');
    } finally {
      this.savingOutcome.set(false);
    }
  }
  protected async older(): Promise<void> {
    const participantId = this.route.snapshot.paramMap.get('participantId');
    const cursor = this.nextCursor();
    if (!participantId || !cursor) return;
    const request = ++this.request;
    try {
      const timeline = await this.api.participantWorkspace.timeline({
        participantId,
        ...rangeDates(this.range()),
        types: this.types().join(',') || undefined,
        cursor,
        limit: 100,
      });
      if (request !== this.request) return;
      const ids = new Set(this.events().map((event) => event.eventId));
      this.events.update((events) =>
        sortedEvents([
          ...events,
          ...(timeline.items ?? []).filter((event) => !ids.has(event.eventId)),
        ]),
      );
      this.nextCursor.set(timeline.nextCursor);
    } catch {
      this.announcement.set('Nie udało się pobrać wcześniejszych zdarzeń.');
    }
  }
  protected perform(action: string) {
    if (action === 'SCHEDULE_APPOINTMENT') {
      this.appointmentError.set(false);
      this.scheduling.set(true);
    } else if (action === 'ADD_MEASUREMENT') {
      void this.openMeasurement();
    } else if (action === 'ADD_NOTE') {
      this.noteCreateRequest.update((request) => request + 1);
      this.openSection('documentation');
    } else if (action === 'ADD_GOAL') {
      this.goalCreateRequest.update((request) => request + 1);
      this.openSection('plan');
    } else if (action === 'OPEN_PLAN') this.openSection('plan');
    else if (action === 'OPEN_HISTORY' || action === 'OPEN_ATTENTION_ITEMS') this.openSection('history');
  }
  protected async performSignal(signal: SituationalSignal): Promise<void> {
    if (signal.action === 'OPEN_ATTENTION_ITEMS' && signal.attentionId) {
      await this.router.navigate(['/specialist-alerts'], { queryParams: { itemId: signal.attentionId } });
      return;
    }
    if (signal.action === 'OPEN_NEXT_APPOINTMENT' && signal.appointmentId) {
      this.section.set('history');
      await this.navigate({ section: 'history', goalId: null, recordType: null, recordId: null, recordMode: null });
      await this.openFocusedAppointment(signal.appointmentId);
      return;
    }
    if (signal.action === 'OPEN_ACTIVE_PLAN') {
      this.openSection('plan');
      return;
    }
    if (signal.action) this.perform(signal.action);
  }
  protected async openMeasurement(request?: { presetId?: string; bodyArea?: string }): Promise<void> {
    const participantId = this.participantId();
    if (!participantId) return;
    this.measurementRequest.set(null);
    this.measurementDialog.set(true); this.measurementError.set(false); this.measurementIdempotencyKey = undefined;
    this.measurementCatalogLoading.set(true);
    try {
      this.measurementPresets.set(await this.api.participantMeasurements.participantMeasurementCatalog({ participantId }));
      this.measurementRequest.set(request ?? null);
    } catch {
      this.measurementError.set(true);
    } finally {
      this.measurementCatalogLoading.set(false);
    }
  }
  protected closeMeasurement(): void {
    if (!this.savingMeasurement()) this.measurementDialog.set(false);
  }
  protected async recordMeasurement(command: import('../api/generated/src/models/ParticipantMeasurementCommand').ParticipantMeasurementCommand): Promise<void> {
    const participantId = this.participantId();
    if (!participantId || this.savingMeasurement()) return;
    this.savingMeasurement.set(true); this.measurementError.set(false);
    this.measurementIdempotencyKey ??= crypto.randomUUID();
    try {
      await this.api.participantMeasurements.recordParticipantMeasurement({ participantId, idempotencyKey: this.measurementIdempotencyKey, participantMeasurementCommand: command });
      this.measurementDialog.set(false); this.announcement.set('Pomiar został zapisany.');
      this.measurementRefresh.update((value) => value + 1);
      await this.load(participantId, this.section() === 'history' ? this.selected()?.eventId ?? null : null);
    } catch {
      this.measurementError.set(true);
    } finally {
      this.savingMeasurement.set(false);
    }
  }
  protected measurementTime(value?: Date): string {
    return value ? new Intl.DateTimeFormat('pl-PL', { dateStyle: 'medium', timeStyle: 'short' }).format(value) : '';
  }
  protected async performFocus(focus: OperationalFocusView): Promise<void> {
    if (focus.primaryAction === 'CONTINUE_INTERVIEW' && focus.interviewId) {
      await this.navigate({ section: 'documentation', eventId: null, goalId: null, recordType: 'interview', recordId: focus.interviewId, recordMode: 'edit' });
      this.focusRecordPanel();
      return;
    }
    if (focus.primaryAction === 'CONTINUE_CLOSEOUT' && focus.appointmentId) {
      await this.router.navigate(['/specialist/clients', this.participantId(), 'appointments', focus.appointmentId, 'closeout']);
      return;
    }
    if (focus.primaryAction === 'RECORD_SESSION_EXECUTION' && focus.appointmentId) {
      await this.router.navigate([
        '/specialist/clients', this.participantId(), 'appointments', focus.appointmentId, 'session',
      ]);
      return;
    }
    if (focus.primaryAction === 'START_APPOINTMENT') {
      await this.startFocusedAppointment(focus);
      return;
    }
    if (focus.primaryAction === 'OPEN_ATTENTION_ITEMS' && focus.attentionId) {
      await this.router.navigate(['/specialist-alerts'], { queryParams: { itemId: focus.attentionId } });
      return;
    }
    if (focus.primaryAction === 'OPEN_HISTORY' && focus.appointmentId) {
      this.section.set('history');
      await this.navigate({ section: 'history', goalId: null, recordType: null, recordId: null, recordMode: null });
      await this.openFocusedAppointment(focus.appointmentId);
      return;
    }
    if (focus.primaryAction) this.perform(focus.primaryAction);
  }
  private async startFocusedAppointment(focus: OperationalFocusView): Promise<void> {
    if (this.startingAppointment()) return;
    const appointment = this.workspace()?.nextAppointment;
    const appointmentId = focus.appointmentId;
    if (
      !appointmentId ||
      appointment?.appointmentId !== appointmentId ||
      !appointment.availableActions?.includes('START') ||
      typeof appointment.version !== 'number'
    ) {
      this.announcement.set('Spotkanie nie może zostać obecnie rozpoczęte. Kartoteka została odświeżona.');
      await this.reload();
      return;
    }
    this.startingAppointment.set(true);
    try {
      await this.api.appointments.start({
        id: appointmentId,
        idempotencyKey: crypto.randomUUID(),
        appointmentVersionCommand: { version: appointment.version },
      });
      this.announcement.set('Spotkanie rozpoczęto. Kartoteka została odświeżona.');
      await this.reload();
    } catch (error) {
      if (this.status(error) === 409) {
        this.announcement.set('Spotkanie zmieniło się i kartoteka została odświeżona.');
        await this.reload();
      } else {
        this.announcement.set('Nie udało się rozpocząć spotkania. Spróbuj ponownie.');
      }
    } finally {
      this.startingAppointment.set(false);
    }
  }
  private async openFocusedAppointment(appointmentId: string): Promise<void> {
    try {
      const appointment = await this.api.appointments.getSpecialistAppointment({ id: appointmentId });
      this.currentAppointment.set(appointment);
      this.selected.set({
        eventId: `appointment:${appointmentId}`,
        category: 'APPOINTMENT',
        status: appointment.status,
        effectiveFrom: appointment.startsAt,
        title: appointment.type,
        summary: appointment.shortPurpose,
        detail: { detailResourceId: appointmentId },
      });
      this.selectedOutsideRange.set(true);
      this.focusPanel();
    } catch {
      this.announcement.set('Nie udało się otworzyć wybranego spotkania.');
    }
  }
  protected closeSchedule() {
    if (!this.savingAppointment()) this.scheduling.set(false);
  }
  protected async schedule(value: {
    startsAt: string;
    endsAt: string;
    type: string;
    locationMode: string;
    location: string;
    shortPurpose: string;
  }): Promise<void> {
    const participantId = this.route.snapshot.paramMap.get('participantId');
    const startsAt = new Date(value.startsAt);
    const endsAt = new Date(value.endsAt);
    if (
      !participantId ||
      Number.isNaN(startsAt.getTime()) ||
      Number.isNaN(endsAt.getTime()) ||
      endsAt <= startsAt
    ) {
      this.appointmentError.set(true);
      return;
    }
    this.savingAppointment.set(true);
    this.appointmentError.set(false);
    try {
      await this.api.appointments.create2({
        idempotencyKey: crypto.randomUUID(),
        createCommand: {
          participantId,
          startsAt,
          endsAt,
          type: value.type as 'TRAINING' | 'PHYSIOTHERAPY' | 'ASSESSMENT' | 'CONSULTATION',
          locationMode: value.locationMode as 'IN_PERSON' | 'REMOTE' | 'PHONE',
          location: value.location || undefined,
          shortPurpose: value.shortPurpose || undefined,
        },
      });
      this.scheduling.set(false);
      this.announcement.set('Spotkanie zaplanowano. Kartoteka została odświeżona.');
      await this.reload();
    } catch {
      this.appointmentError.set(true);
    } finally {
      this.savingAppointment.set(false);
    }
  }
  private async loadCurrentAppointment(event: ParticipantTimelineEvent): Promise<void> {
    const appointmentId = this.appointmentId(event);
    if (event.category !== 'APPOINTMENT' || !appointmentId) return;
    try {
      this.currentAppointment.set(
        await this.api.appointments.getSpecialistAppointment({ id: appointmentId }),
      );
    } catch {
      this.currentAppointment.set(null);
    }
  }
  private async resolveSelection(
    participantId: string,
    eventId: string | null,
    items: ParticipantTimelineEvent[],
    request = this.request,
  ): Promise<void> {
    this.currentAppointment.set(null);
    this.currentGoal.set(null);
    this.currentGoalUnavailable.set(false);
    this.selectedOutsideRange.set(false);
    if (!eventId) {
      this.selected.set(null);
      return;
    }
    this.section.set('history');
    const listed = items.find((event) => event.eventId === eventId);
    if (listed) {
      this.selected.set(listed);
      this.openRecordForEvent(listed);
      void this.loadCurrentAppointment(listed);
      void this.loadCurrentGoal(listed);
      return;
    }
    try {
      const event = await this.api.participantWorkspace.timelineEvent({ participantId, eventId });
      if (request !== this.request) return;
      const listEvent = this.events().find((item) => item.eventId === event.eventId);
      const selected = listEvent ?? event;
      this.selected.set(selected);
      this.openRecordForEvent(selected);
      this.selectedOutsideRange.set(!listEvent);
      void this.loadCurrentAppointment(selected);
      void this.loadCurrentGoal(selected);
      this.focusPanel();
    } catch (error) {
      if (request !== this.request) return;
      if (this.status(error) === 404) {
        this.selected.set(null);
        this.announcement.set('Wybrane zdarzenie jest niedostępne.');
        await this.navigate({ eventId: null }, true);
        return;
      }
      this.selected.set(null);
      this.announcement.set('Nie udało się otworzyć wybranego zdarzenia.');
    }
  }
  private async loadCurrentGoal(event: ParticipantTimelineEvent): Promise<void> {
    const goalId = event.category === 'GOAL' ? event.detail?.referenceId : undefined;
    const actingContext = this.actingContext();
    if (!goalId || !actingContext) return;
    try {
      this.currentGoal.set(
        await this.api.participantGoals.getParticipantGoal({
          participantId: this.participantId(),
          goalId,
          actingContext,
        }),
      );
    } catch (error) {
      if (this.status(error) === 404) this.currentGoalUnavailable.set(true);
    }
  }
  private focusPanel() {
    queueMicrotask(() => {
      const panel = this.host.nativeElement.querySelector('.event-panel');
      if (panel instanceof HTMLElement) panel.focus();
    });
  }
  private openRecordForEvent(event: ParticipantTimelineEvent) {
    const type = event.category === 'INTERVIEW' ? 'interview' : event.category === 'NOTE' ? 'note' : null;
    const id = event.detail?.referenceId;
    if (type && id && !this.recordPanelId()) {
      void this.navigate({ recordType: type, recordId: id, recordMode: null });
    }
  }
  private focusRecordPanel() {
    queueMicrotask(() => {
      const panel = this.host.nativeElement.querySelector('.record-panel');
      if (panel instanceof HTMLElement) panel.focus();
    });
  }
  private appointmentId(event: ParticipantTimelineEvent | null): string | undefined {
    return event?.detail?.detailResourceId;
  }
  private status(error: unknown): number | undefined {
    return error instanceof ResponseError ? error.response.status : undefined;
  }
  private navigate(queryParams: Record<string, string | null>, replaceUrl = false) {
    return this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
      replaceUrl,
    });
  }
}
