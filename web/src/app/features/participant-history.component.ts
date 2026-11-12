import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import type { SpecialistParticipantHistoryItem } from '../api/generated/src/models/SpecialistParticipantHistoryItem';
import type { SpecialistParticipantHistoryNavigationTarget } from '../api/generated/src/models/SpecialistParticipantHistoryNavigationTarget';
import type { WorkspaceView } from './specialist-participant-workspace.geometry';

const appointmentTypes: Record<string, string> = {
  TRAINING: 'Trening', PHYSIOTHERAPY: 'Fizjoterapia', ASSESSMENT: 'Ocena', CONSULTATION: 'Konsultacja',
};
const appointmentStates: Record<string, string> = {
  SCHEDULED: 'Zaplanowane', CONFIRMED: 'Potwierdzone', IN_PROGRESS: 'W trakcie', COMPLETED: 'Zakończone',
  CANCELLED: 'Odwołane', NO_SHOW: 'Nieobecność',
};
const executionStates: Record<string, string> = {
  COMPLETED: 'wykonano', PARTIAL: 'wykonano częściowo', SKIPPED: 'pominięto', STOPPED: 'przerwano',
};
const standaloneTypes: Record<string, string> = {
  session: 'Planowana sesja', execution: 'Wykonanie', measurement: 'Pomiar', note: 'Notatka', interview: 'Wywiad', goal: 'Cel',
};
const measurementLabels: Record<string, string> = { BODY_WEIGHT: 'Masa ciała' };

@Component({
  selector: 'app-participant-history',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`
    :host { display:block; min-width:0; }
    ol { list-style:none; margin:0; padding:0; }
    .history-timeline { display:grid; gap:var(--space-2); }
    .history-item { border-bottom:1px solid var(--app-border, #d9dde3); }
    .history-item > button { align-items:start; background:none; border:0; color:inherit; cursor:pointer; display:grid; gap:var(--space-1); min-height:44px; padding:var(--space-3) 0; text-align:left; width:100%; }
    .history-item.encounter > button { grid-template-columns:minmax(9rem, .35fr) minmax(0, 1fr); }
    .history-time, .history-kind, .history-support { color:var(--app-text-muted, #5d6570); font-size:.875rem; }
    .history-summary { display:grid; gap:.25rem; min-width:0; }
    .history-summary strong { font-size:1rem; }
    .history-status { font-weight:600; }
    .history-status.deviation { color:var(--app-warning, #8b4d00); }
    .history-support { display:flex; flex-wrap:wrap; gap:var(--space-2); }
    .history-support .matched { font-weight:600; color:inherit; }
    .history-detail { border-bottom:1px solid var(--app-border, #d9dde3); padding:var(--space-3) 0 var(--space-4); }
    .history-detail > button { float:right; min-height:44px; }
    .history-detail h2 { margin:0 3rem var(--space-2) 0; }
    .history-detail dl { display:grid; gap:.25rem var(--space-3); grid-template-columns:max-content minmax(0, 1fr); margin:var(--space-3) 0; }
    .history-detail dt { color:var(--app-text-muted, #5d6570); }
    .history-detail dd { margin:0; }
    .history-detail ul { display:grid; gap:var(--space-1); list-style:none; padding:0; }
    .history-highlight { outline:2px solid var(--app-primary, #295aa8); outline-offset:2px; }
    @media (max-width: 640px) { .history-item.encounter > button { grid-template-columns:1fr; } }
  `],
  template: `
    @if (!items.length) { <p>Brak zdarzeń w wybranym zakresie.</p> }
    <ol class="history-timeline" [attr.aria-label]="view === 'timeline' ? 'Chronologiczna historia' : 'Historia w widoku listy'">
      @for (item of items; track item.stableItemId) {
        <li class="history-item" [class.encounter]="item.kind === 'ENCOUNTER'">
          <button type="button" [attr.data-history-item]="item.stableItemId" (click)="opened.emit(item)">
            <time class="history-time">{{ time(item.historyAt) }}</time>
            <span class="history-summary">
              @if (item.kind === 'ENCOUNTER' && item.encounter; as encounter) {
                <strong>{{ appointmentType(encounter.appointment?.type) }}</strong>
                <span class="history-status" [class.deviation]="isDeviation(encounter.appointment?.status)">{{ appointmentStatus(encounter.appointment?.status) }}</span>
                @if (encounter.linkedSession || encounter.executions?.length) {
                  <span>{{ sessionSummary(encounter) }}</span>
                }
                @if (supportSummary(encounter); as support) { <span class="history-support" [class.matched]="matches(item, 'MEASUREMENT') || matches(item, 'NOTE')">{{ support }}</span> }
              } @else if (item.standalone; as standalone) {
                <span class="history-kind">{{ standaloneKind(standalone) }}</span>
                <strong>{{ standaloneSummary(standalone) }}</strong>
              }
            </span>
          </button>
        </li>
      }
    </ol>
    @if (selected; as item) {
      <aside class="history-detail" role="dialog" aria-modal="false" aria-labelledby="history-detail-title" tabindex="-1" (keydown.escape)="closed.emit()">
        <button type="button" aria-label="Zamknij szczegóły historii" (click)="closed.emit()">×</button>
        <h2 id="history-detail-title">{{ detailTitle(item) }}</h2>
        @if (outsideRange) { <p role="status">Zdarzenie znajduje się poza aktualnie wybranym zakresem historii.</p> }
        @if (item.kind === 'ENCOUNTER' && item.encounter; as encounter) {
          <dl>
            <dt>Termin</dt><dd>{{ time(encounter.appointment?.startsAt) }}</dd>
            <dt>Status</dt><dd>{{ appointmentStatus(encounter.appointment?.status) }}</dd>
            @if (encounter.linkedSession) { <dt>Planowana sesja</dt><dd>{{ sessionSummary(encounter) }}</dd> }
          </dl>
          @if (encounter.lifecycle?.length) { <section><h3>Przebieg spotkania</h3><ul>@for (fact of encounter.lifecycle; track fact.id) { <li [attr.data-history-child-id]="fact.id" [attr.tabindex]="highlightKind === 'LIFECYCLE' && highlightId === fact.id ? -1 : null" [class.history-highlight]="highlightKind === 'LIFECYCLE' && highlightId === fact.id">{{ appointmentStatus(fact.toStatus) }} · {{ time(fact.effectiveAt) }}</li> }</ul></section> }
          @if (encounter.executions?.length) { <section><h3>Realizacja</h3><ul>@for (execution of encounter.executions; track execution.id) { <li>{{ executionSummary(execution) }}</li> }</ul></section> }
          @if (encounter.measurements?.length) { <section><h3>Pomiary</h3><ul>@for (measurement of encounter.measurements; track measurement.id) { <li [attr.data-history-child-id]="measurement.id" [attr.tabindex]="highlightKind === 'MEASUREMENT' && highlightId === measurement.id ? -1 : null" [class.history-highlight]="highlightKind === 'MEASUREMENT' && highlightId === measurement.id">{{ measurementSummary(measurement) }}</li> }</ul></section> }
          @if (encounter.documentation?.length) { <section><h3>Dokumentacja</h3><ul>@for (document of encounter.documentation; track document.id) { <li [attr.data-history-child-id]="document.id" [attr.tabindex]="highlightKind === 'DOCUMENTATION' && highlightId === document.id ? -1 : null" [class.history-highlight]="highlightKind === 'DOCUMENTATION' && highlightId === document.id">{{ documentSummary(document) }}</li> }</ul></section> }
          @if (protectedDocumentationTarget?.id) { <button type="button" (click)="protectedDocumentationRequested.emit()">Otwórz dokumentację</button> }
          @if (appointmentActions.includes('COMPLETE')) { <button type="button" [disabled]="saving" (click)="outcome.emit('COMPLETE')">Odbyło się</button> }
          @if (appointmentActions.includes('MARK_NO_SHOW')) { <button type="button" [disabled]="saving" (click)="outcome.emit('MARK_NO_SHOW')">Nieobecność</button> }
        } @else if (item.standalone; as standalone) {
          <p>{{ standaloneSummary(standalone) }}</p>
        }
      </aside>
    }
  `,
})
export class ParticipantHistoryComponent {
  @Input() items: SpecialistParticipantHistoryItem[] = [];
  @Input() view: WorkspaceView = 'timeline';
  @Input() selected: SpecialistParticipantHistoryItem | null = null;
  @Input() outsideRange = false;
  @Input() highlightKind?: string;
  @Input() highlightId?: string;
  @Input() appointmentActions: string[] = [];
  @Input() saving = false;
  @Input() protectedDocumentationTarget?: SpecialistParticipantHistoryNavigationTarget;
  @Output() opened = new EventEmitter<SpecialistParticipantHistoryItem>();
  @Output() closed = new EventEmitter<void>();
  @Output() outcome = new EventEmitter<'COMPLETE' | 'MARK_NO_SHOW'>();
  @Output() protectedDocumentationRequested = new EventEmitter<void>();

  protected appointmentType = (value?: string) => appointmentTypes[value ?? ''] ?? 'Spotkanie';
  protected appointmentStatus = (value?: string) => appointmentStates[value ?? ''] ?? 'Brak statusu';
  protected isDeviation = (value?: string) => ['CANCELLED', 'NO_SHOW'].includes(value ?? '');
  protected matches = (item: SpecialistParticipantHistoryItem, kind: string) => item.matchedKinds?.has(kind as never) ?? false;
  protected time = (value?: Date) => value ? new Intl.DateTimeFormat('pl-PL', { dateStyle: 'long', timeStyle: 'short' }).format(value) : 'Brak czasu';
  protected sessionSummary(encounter: NonNullable<SpecialistParticipantHistoryItem['encounter']>) {
    const execution = encounter.executions?.[0];
    const session = encounter.linkedSession;
    const title = session?.kind ? this.appointmentType(session.kind) : 'Powiązana sesja';
    return execution ? `${title} · ${this.executionSummary(execution)}` : title;
  }
  protected executionSummary(execution: { outcome?: string; resultCount?: number }) {
    const outcome = executionStates[execution.outcome ?? ''] ?? 'zarejestrowano realizację';
    return execution.resultCount == null ? outcome : `${execution.resultCount} ćwiczeń · ${outcome}`;
  }
  protected supportSummary(encounter: NonNullable<SpecialistParticipantHistoryItem['encounter']>) {
    const parts: string[] = [];
    if (encounter.measurements?.length === 1) parts.push(this.measurementSummary(encounter.measurements[0]));
    else if (encounter.measurements?.length) parts.push(`${encounter.measurements.length} pomiary`);
    if (encounter.documentation?.length) parts.push(`${encounter.documentation.length} ${encounter.documentation.length === 1 ? 'notatka' : 'notatki'}`);
    return parts.join(' · ');
  }
  protected measurementSummary(measurement: { metricCode?: string; value?: number; unit?: string }) {
    return `${measurementLabels[measurement.metricCode ?? ''] ?? 'Pomiar'} ${measurement.value ?? ''} ${measurement.unit ?? ''}`.trim();
  }
  protected documentSummary(document: { category?: string; status?: string }) {
    return `${document.category === 'INTERVIEW' ? 'Wywiad' : 'Notatka'}${document.status ? ` · ${document.status.replace(/_/g, ' ').toLocaleLowerCase('pl-PL')}` : ''}`;
  }
  protected standaloneKind(standalone: NonNullable<SpecialistParticipantHistoryItem['standalone']>) { return standaloneTypes[Object.keys(standalone).find((key) => !!standalone[key as keyof typeof standalone]) ?? ''] ?? 'Zdarzenie'; }
  protected standaloneSummary(standalone: NonNullable<SpecialistParticipantHistoryItem['standalone']>) {
    if (standalone.measurement) return this.measurementSummary(standalone.measurement);
    if (standalone.execution) return this.executionSummary(standalone.execution);
    if (standalone.goal) return standalone.goal.eventType?.replace(/_/g, ' ').toLocaleLowerCase('pl-PL') ?? 'Zdarzenie celu';
    if (standalone.note) return 'Notatka';
    if (standalone.interview) return 'Wywiad';
    return 'Planowana sesja';
  }
  protected detailTitle(item: SpecialistParticipantHistoryItem) { return item.kind === 'ENCOUNTER' ? this.appointmentType(item.encounter?.appointment?.type) : this.standaloneKind(item.standalone!); }
}
