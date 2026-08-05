import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  ViewEncapsulation,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiFacade } from '../core/api.facade';
import { ResponseError } from '../api/generated/src/runtime';
import type { SpecialistParticipantWorkspaceView } from '../api/generated/src/models/SpecialistParticipantWorkspaceView';
import type { ParticipantTimelineEvent } from '../api/generated/src/models/ParticipantTimelineEvent';
import type { AppointmentView } from '../api/generated/src/models/AppointmentView';
import type { ParticipantGoalView } from '../api/generated/src/models/ParticipantGoalView';
import type { PresetView } from '../api/generated/src/models/PresetView';
import type { CreateFromPresetRequestPresetIdEnum, CreateFromPresetRequestTargetComparatorEnum } from '../api/generated/src/models/CreateFromPresetRequest';
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

const actionLabels: Record<string, string> = { SCHEDULE_APPOINTMENT: 'Zaplanuj spotkanie' };
const label = (value: string | undefined, labels: Record<string, string>) =>
  value ? (labels[value] ?? value.replace(/_/g, ' ').toLocaleLowerCase('pl-PL')) : 'Brak danych';

@Component({
  selector: 'app-participant-workspace-header',
  standalone: true,
  imports: [MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
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
    @if (actions.length) {
      <div class="quick-actions" aria-label="Szybkie działania">
        @for (action of actions; track action) {
          <button mat-flat-button type="button" (click)="requested.emit(action)">
            {{ actionLabel(action) }}
          </button>
        }
      </div>
    }
  </header>`,
})
export class ParticipantWorkspaceHeaderComponent {
  @Input({ required: true }) workspace!: SpecialistParticipantWorkspaceView;
  @Input() accessStatus?: string;
  @Input() accessStatusAvailable = true;
  @Input() actions: string[] = [];
  @Output() requested = new EventEmitter<string>();
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
  template: `<section class="summary-strip" aria-label="Podsumowanie klienta">
    <div>
      <strong>Aktywny plan</strong
      ><span>{{ workspace.activePlan?.name || 'Brak aktywnego planu' }}</span>
    </div>
    <div>
      <strong>Cele</strong><span>{{ goals(workspace.goals?.length) }}</span>
    </div>
    <div>
      <strong>Realizacja</strong
      ><span>{{ realization(workspace.adherenceSummary?.completedSessions) }}</span>
    </div>
    <div>
      <strong>Ostatnia aktywność</strong
      ><span>{{
        workspace.recentProgress?.latestActivityAt ? 'Zarejestrowana' : 'Brak danych'
      }}</span>
    </div>
    <div>
      <strong>Wymaga reakcji</strong><span>{{ attention(workspace.activeProblems?.length) }}</span>
    </div>
  </section>`,
})
export class ParticipantSummaryStripComponent {
  @Input({ required: true }) workspace!: SpecialistParticipantWorkspaceView;
  protected goals = goalsSummary;
  protected realization = realizationSummary;
  protected attention = attentionSummary;
}

@Component({
  selector: 'app-patient-timeline-filters',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
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
    ({ APPOINTMENT: 'Spotkania', SESSION: 'Planowane sesje', EXECUTION: 'Wykonania' })[type] ??
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
    <h2 id="event-panel-title">{{ title(event) }}</h2>
    @if (outsideRange) {
      <p role="status">Zdarzenie znajduje się poza aktualnie wybranym zakresem historii.</p>
    }
    @if (description(event); as description) {
      <p>{{ description }}</p>
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
  protected description = eventDescription;
  protected goalPerspective = (value?: string) => goalPerspective[value ?? ''] ?? 'Brak danych';
  protected goalStatus = (value?: string) => goalStatus[value ?? ''] ?? 'Brak danych';
  protected location = appointmentLocation;
  protected pastScheduled = isPastScheduled;
  protected purpose = appointmentPurpose;
  protected status = statusLabel;
  protected time = eventTimeLabel;
  protected title = humanEventTitle;
}

@Component({
  selector: 'app-schedule-appointment-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section
    class="appointment-dialog"
    role="dialog"
    aria-modal="true"
    aria-labelledby="appointment-dialog-title"
  >
    <form [formGroup]="form" (ngSubmit)="submitted.emit(form.getRawValue())">
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
        >Termin zakończenia<input type="datetime-local" formControlName="endsAt" required /></label
      ><label
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
    endsAt: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    type: new FormControl('TRAINING', { nonNullable: true }),
    locationMode: new FormControl('IN_PERSON', { nonNullable: true }),
    location: new FormControl('', { nonNullable: true }),
    shortPurpose: new FormControl('', { nonNullable: true }),
  });
}

const goalStatus: Record<string, string> = {
  ACTIVE: 'Aktywny',
  ACHIEVED: 'Osiągnięty',
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
  TARGET_REACHED: 'Cel osiągnięty',
  NOT_COMPARABLE: 'Brak porównania',
};
const comparator: Record<string, string> = { AT_LEAST: 'co najmniej', AT_MOST: 'nie więcej niż' };
@Component({
  selector: 'app-participant-goals',
  standalone: true,
  imports: [MatButtonModule, MatInputModule, ReactiveFormsModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section class="goals-workspace" aria-labelledby="goals-title">
    <div class="goals-heading">
      <div>
        <h2 id="goals-title">Cele</h2>
        <p>{{ active().length }} aktywne · {{ completed().length }} zakończone</p>
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
    } @else if (!goals().length) {
      <p>Brak zdefiniowanych celów.</p>
    } @else {
      <div class="goal-cards">
        @for (goal of active(); track goal.id) {
          <button mat-stroked-button type="button" class="goal-card" (click)="open(goal)">
            <strong>{{ goal.title }}</strong
            ><span>{{ status(goal.status) }} · {{ perspective(goal.category) }}</span>
            @if (goal.targetDate) {
              <span>Termin: {{ goal.targetDate | date: 'longDate' : '' : 'pl' }}</span>
            }
            @for (outcome of goal.outcomes ?? []; track outcome.id) {
              <span
                >{{ outcomeLabel(outcome.metricCode) }}: {{ outcome.targetValue }} {{ outcome.unit }} ({{
                  progressLabel(outcome.progressState)
                }})</span
              >
            }
          </button>
        }
      </div>
      @if (completed().length) {
        <details>
          <summary>Zakończone cele ({{ completed().length }})</summary>
          <div class="goal-cards">
            @for (goal of completed(); track goal.id) {
              <button mat-stroked-button type="button" class="goal-card" (click)="open(goal)">
                <strong>{{ goal.title }}</strong
                ><span>{{ status(goal.status) }}</span>
              </button>
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
          @if (panelMode() === 'view') {
            <div class="goal-panel-actions" aria-label="Działania dla celu">
              @if (isMutable(goal) && has('UPDATE')) {
                <button mat-stroked-button type="button" (click)="panelMode.set('edit')">Edytuj</button>
              }
              @if (isMutable(goal) && has('RECORD_OBSERVATION')) {
                <button mat-flat-button type="button" (click)="panelMode.set('observation')">Dodaj pomiar</button>
              }
            </div>
          }
        </header>
        @if (panelMode() === 'view') {
          <div class="goal-panel-body">
            @if (eventContext) {
              <p class="goal-event-context">Zdarzenie na osi czasu: {{ eventContext }}</p>
            }
            @if (goal.description) {
              <p>{{ goal.description }}</p>
            }
            <section class="goal-panel-section" aria-labelledby="goal-definition-title">
              <h3 id="goal-definition-title">Cel</h3>
              @for (outcome of goal.outcomes ?? []; track outcome.id) {
                <dl class="goal-facts">
                  <dt>Wartość początkowa:</dt><dd>{{ outcome.baseline ?? 'Brak' }}{{ outcome.baseline != null ? ' ' + outcome.unit : '' }}</dd>
                  <dt>Wartość docelowa:</dt><dd>{{ outcome.targetValue }} {{ outcome.unit }}</dd>
                  <dt>Porównanie:</dt><dd>{{ comparatorLabel(outcome.targetComparator) }}</dd>
                </dl>
              }
              <dl class="goal-facts"><dt>Termin:</dt><dd>{{ goal.targetDate ? (goal.targetDate | date: 'longDate' : '' : 'pl') : 'Brak' }}</dd></dl>
            </section>
            <section class="goal-panel-section" aria-labelledby="goal-progress-title">
              <h3 id="goal-progress-title">Postęp</h3>
              @for (outcome of goal.outcomes ?? []; track outcome.id) {
                <dl class="goal-facts">
                  <dt>Ostatni pomiar:</dt><dd>{{ outcome.latestObservation ? outcome.latestObservation.value + ' ' + outcome.unit : 'Brak pomiarów' }}</dd>
                  <dt>Stan:</dt><dd>{{ progressLabel(outcome.progressState) }}</dd>
                  <dt>Liczba pomiarów:</dt><dd>{{ outcome.observationCount ?? 0 }}</dd>
                </dl>
              }
            </section>
            <section class="goal-panel-section" aria-labelledby="goal-history-title">
              <h3 id="goal-history-title">Historia pomiarów</h3>
              @if (historyLoading()) {
                <p role="status">Wczytywanie historii pomiarów…</p>
              } @else if (history().length) {
                <ol class="goal-observation-history">
                  @for (observation of history(); track observation.id) {
                    <li><strong>{{ observation.value }} {{ observation.unit }}</strong><span>{{ observation.measuredAt | date: 'short' : '' : 'pl' }}</span>@if (observation.note) { <span>{{ observation.note }}</span> }</li>
                  }
                </ol>
              } @else {
                <p>Brak zapisanych pomiarów.</p>
              }
            </section>
            <div class="goal-panel-lifecycle-actions">
              @if (isMutable(goal) && has('ACHIEVE')) {
                <button mat-stroked-button type="button" [disabled]="mutating()" (click)="confirm.set('ACHIEVE')">Oznacz jako osiągnięty</button>
              }
              @if (isMutable(goal) && has('CANCEL')) {
                <button mat-stroked-button type="button" [disabled]="mutating()" (click)="confirm.set('CANCEL')">Anuluj cel</button>
              }
            </div>
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
  @Input({ required: true }) participantId!: string;
  @Input() role?: 'TRAINER' | 'PHYSIOTHERAPIST';
  @Input() eventContext?: string;
  @Output() changed = new EventEmitter<void>();
  protected readonly goals = signal<ParticipantGoalView[]>([]);
  protected readonly state = signal<'loading' | 'loaded' | 'error'>('loading');
  protected readonly selected = signal<ParticipantGoalView | null>(null);
  protected readonly panelMode = signal<'view' | 'edit' | 'observation'>('view');
  protected readonly creating = signal(false);
  protected readonly mutating = signal(false);
  protected readonly formError = signal(false);
  protected readonly confirm = signal<'ACHIEVE' | 'CANCEL' | null>(null);
  protected readonly history = signal<any[]>([]);
  protected readonly historyLoading = signal(false);
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
  protected outcomeLabel = outcomeMetricLabel;
  protected comparatorLabel = (value?: string) => comparator[value ?? ''] ?? 'Brak';
  ngOnChanges() {
    void this.refresh();
  }
  protected perspectiveLabel() { return this.role === 'TRAINER' ? 'Wynik sportowy' : 'Powrót do funkcji'; }
  private context() {
    return this.role! as never;
  }
  protected async refresh() {
    if (!this.role || !this.participantId) return;
    this.state.set('loading');
    try {
      this.goals.set(
        await this.api.participantGoals.listParticipantGoals({
          participantId: this.participantId,
          actingContext: this.context(),
        }),
      );
      this.state.set('loaded');
    } catch {
      this.state.set('error');
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
    try {
      const detail = await this.api.participantGoals.getParticipantGoal({
        participantId: this.participantId,
        goalId: goal.id,
        actingContext: this.context(),
      });
      this.selected.set(detail);
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
      void this.loadHistory();
    } catch {
      this.selected.set(null);
    }
  }
  protected close() {
    this.selected.set(null);
    this.panelMode.set('view');
    this.confirm.set(null);
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
    this.historyLoading.set(true);
    try {
      this.history.set(
        (
          await this.api.participantGoals.listParticipantGoalObservations({
            participantId: this.participantId,
            goalId: goal.id,
            actingContext: this.context(),
            limit: 20,
          })
        ).items ?? [],
      );
    } finally {
      this.historyLoading.set(false);
    }
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
    await this.mutate(
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
  private async mutate(call: () => Promise<any>, success: string) {
    this.mutating.set(true);
    try {
      const updated = await call();
      this.selected.set(updated.goal ?? updated);
      this.panelMode.set('view');
      this.confirm.set(null);
      await this.refresh();
      this.changed.emit();
    } catch (error) {
      const status = error instanceof ResponseError ? error.response.status : 0;
      if (status === 409 && this.selected()) await this.open(this.selected()!);
      else if (status === 404) this.close();
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
    ParticipantGoalsComponent,
    PatientTimelineFiltersComponent,
    PatientTimelineComponent,
    PatientTimelineListViewComponent,
    PatientTimelineEventPanelComponent,
    ScheduleAppointmentDialogComponent,
  ],
  styleUrl: './specialist-participant-workspace.page.scss',
  encapsulation: ViewEncapsulation.None,
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
    } @else if (workspace(); as data) {
      <app-participant-workspace-header
        [workspace]="data"
        [accessStatus]="accessStatus()"
        [accessStatusAvailable]="accessStatusAvailable()"
        [actions]="safeActions()"
        (requested)="perform($event)"
      /><app-participant-summary-strip [workspace]="data" /><app-participant-goals
        [participantId]="participantId()"
        [role]="actingContext()"
        (changed)="reload()"
      />
      <section class="workspace-content">
        <section class="workspace-timeline" aria-labelledby="workspace-overview-title">
          <h2 id="workspace-overview-title">Historia współpracy</h2>
          @if (hasAttention(data)) {
            <section class="attention-alerts" aria-labelledby="attention-title">
              <h3 id="attention-title">Wymaga uwagi</h3>
              @for (item of data.attentionItems ?? []; track item.id) {
                <article role="alert">
                  <strong>{{ item.title || 'Wymaga sprawdzenia' }}</strong>
                  @if (item.neutralReason) {
                    <p>{{ item.neutralReason }}</p>
                  }
                </article>
              }
              @for (problem of data.activeProblems ?? []; track problem.problemId) {
                <article role="alert">
                  <strong>Aktywny problem</strong>
                  @if (problem.shortDescription) {
                    <p>{{ problem.shortDescription }}</p>
                  }
                </article>
              }
            </section>
          }
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
      @if (scheduling()) {
        <app-schedule-appointment-dialog
          [saving]="savingAppointment()"
          [error]="appointmentError()"
          (closed)="closeSchedule()"
          (submitted)="schedule($event)"
        />
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
  protected readonly announcement = signal('');
  protected readonly accessStatus = signal<string | undefined>(undefined);
  protected readonly accessStatusAvailable = signal(true);
  protected readonly scheduling = signal(false);
  protected readonly savingAppointment = signal(false);
  protected readonly appointmentError = signal(false);
  protected readonly currentAppointment = signal<AppointmentView | null>(null);
  protected readonly currentGoal = signal<ParticipantGoalView | null>(null);
  protected readonly currentGoalUnavailable = signal(false);
  protected readonly savingOutcome = signal(false);
  protected readonly selectedOutsideRange = signal(false);
  protected readonly groups = computed(() =>
    groupEvents(this.events(), rangeDates(this.range()).granularity),
  );
  protected readonly safeActions = computed(() =>
    (this.workspace()?.quickActions ?? []).filter((action) => !!actionLabels[action]),
  );
  constructor() {
    this.route.queryParamMap.subscribe((params) => {
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
      const participantId = this.route.snapshot.paramMap.get('participantId');
      if (!participantId) return;
      this.participantId.set(participantId);
      const context = `${participantId}:${this.range()}:${this.types().join(',')}:${this.view()}`;
      if (this.timelineContext === context && this.state() === 'loaded')
        void this.resolveSelection(participantId, id, this.events());
      else {
        this.timelineContext = context;
        void this.load(participantId, id);
      }
    });
  }
  protected hasAttention(data: SpecialistParticipantWorkspaceView) {
    return !!(data.attentionItems?.length || data.activeProblems?.length);
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
    try {
      const [workspace, timeline, clients, onboarding] = await Promise.all([
        this.api.participantWorkspace.workspace({ participantId }),
        this.api.participantWorkspace.timeline({
          participantId,
          from: dates.from,
          to: dates.to,
          types: this.types().join(',') || undefined,
          granularity: dates.granularity,
          limit: 100,
        }),
        this.api.specialistClients.list1().catch(() => undefined),
        this.api.onboarding.state().catch(() => undefined),
      ]);
      if (request !== this.request) return null;
      const items = sortedEvents(timeline.items ?? []);
      const client = clients?.find((item) => item.participantId === participantId);
      this.accessStatus.set(client?.accessStatus);
      this.accessStatusAvailable.set(clients !== undefined);
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
    void this.navigate({ eventId: event.eventId ?? null });
    this.focusPanel();
  }
  protected close() {
    void this.navigate({ eventId: null });
    queueMicrotask(() => {
      if (this.opener?.isConnected) this.opener.focus();
    });
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
    const listed = items.find((event) => event.eventId === eventId);
    if (listed) {
      this.selected.set(listed);
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
