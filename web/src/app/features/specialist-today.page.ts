import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, Input, OnChanges, Output, EventEmitter, SimpleChanges, afterNextRender, computed, inject, signal } from '@angular/core';
import { DatePipe, NgStyle } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { SpecialistTodayApi } from './specialist-today.api';
import { actionLabel, appointmentStatusLabel, appointmentTypeLabel, locationModeLabel, priorityLabel } from './specialist-today.labels';
import { layoutOverlappingAppointments, positionPercent, type AppointmentLayout, type VisibleRange } from './specialist-today.geometry';
import type { SpecialistTodayView, TodayAppointment, TodayAttentionItem } from './specialist-today.types';

type LoadState = 'loading' | 'loaded' | 'error';

@Component({
  selector: 'app-day-timeline', standalone: true, imports: [NgStyle, MatButtonModule],
  template: `
    <section class="timeline" aria-labelledby="timeline-title">
      <h2 id="timeline-title">Plan dnia</h2>
      @if (!appointments.length) { <p class="empty-agenda">Nie masz dziś zaplanowanych spotkań.</p> }
      <div class="timeline-scroll" [style.--timeline-height.px]="timelineHeight">
        <div class="time-scale" aria-hidden="true">@for (hour of hours; track hour) { <span [style.top.%]="hour.position">{{ hour.label }}</span> }</div>
        <div class="timeline-canvas" [style.--timeline-height.px]="timelineHeight">
          @for (availability of availabilityLayout; track availability.startsAt + availability.endsAt) { <div class="availability" [class.unavailable]="availability.type === 'UNAVAILABLE' || availability.type === 'BLOCKED'" [ngStyle]="availability.style"><span>{{ availabilityLabel(availability.type) }}</span></div> }
          @for (item of layouts; track item.appointment.appointmentId) { <button type="button" class="appointment" [class.current]="item.appointment.isCurrent" [ngStyle]="styleFor(item)" [attr.aria-label]="appointmentAria(item.appointment)" (click)="opened.emit(item.appointment)"><span class="appointment-time">{{ time(item.appointment.startsAt) }}–{{ time(item.appointment.endsAt) }}</span><strong>{{ item.appointment.participantLabel }}</strong><span>{{ typeLabel(item.appointment.type) }}</span><small>{{ item.appointment.isCurrent ? 'Trwa teraz · ' : '' }}{{ statusLabel(item.appointment.status) }}</small></button> }
          @if (showNow) { <div class="now-line" aria-hidden="true" [style.top.%]="nowPosition"><span>Teraz</span></div> }
        </div>
      </div>
      <ol class="sr-only" aria-label="Agenda spotkań">@for (appointment of appointments; track appointment.appointmentId) { <li><button type="button" (click)="opened.emit(appointment)">{{ appointmentAria(appointment) }}</button></li> }</ol>
    </section>`,
  styleUrl: './specialist-today.page.scss', changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DayTimelineComponent implements OnChanges {
  @Input({ required: true }) view!: SpecialistTodayView;
  @Input({ required: true }) now!: Date;
  @Output() readonly opened = new EventEmitter<TodayAppointment>();
  protected range!: VisibleRange; protected layouts: AppointmentLayout[] = []; protected availabilityLayout: Array<{ startsAt: string; endsAt: string; type: string; style: Record<string, string> }> = [];
  protected hours: Array<{ label: string; position: number }> = []; protected showNow = false; protected nowPosition = 0; protected timelineHeight = 640;
  protected readonly typeLabel = appointmentTypeLabel; protected readonly statusLabel = appointmentStatusLabel;
  get appointments(): TodayAppointment[] { return this.view.appointments.filter(item => item.status !== 'CANCELLED'); }
  ngOnChanges(_: SimpleChanges): void {
    const visible = this.view.visibleRange;
    this.range = { start: new Date(visible!.startsAt!), end: new Date(visible!.endsAt!) };
    this.layouts = layoutOverlappingAppointments(this.appointments, this.range);
    this.availabilityLayout = this.view.availabilityWindows.map(item => ({ ...item, style: { top: `${positionPercent(item.startsAt, this.range)}%`, height: `${Math.max(0, positionPercent(item.endsAt, this.range) - positionPercent(item.startsAt, this.range))}%` } }));
    const durationMinutes = Math.max(60, (this.range.end.getTime() - this.range.start.getTime()) / 60_000); this.timelineHeight = Math.max(420, Math.min(1_040, durationMinutes * 1.1));
    this.hours = Array.from({ length: Math.ceil(durationMinutes / 60) + 1 }, (_, index) => { const date = new Date(this.range.start.getTime() + index * 3_600_000); return { label: this.time(date.toISOString()), position: positionPercent(date, this.range) }; });
    const today = this.localDate(this.now); this.showNow = today === this.view.localDate && this.now >= this.range.start && this.now <= this.range.end; this.nowPosition = positionPercent(this.now, this.range);
  }
  protected styleFor(item: AppointmentLayout): Record<string, string> { const width = 100 / item.columns; return { top: `${item.top}%`, height: `${item.height}%`, left: `calc(${item.column * width}% + 4px)`, width: `calc(${width}% - 8px)` }; }
  protected time(value: string): string { return new Intl.DateTimeFormat('pl-PL', { timeZone: this.view.timeZoneId, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(value)); }
  protected availabilityLabel(type: string): string { return type === 'UNAVAILABLE' || type === 'BLOCKED' ? 'Niedostępność' : 'Dostępność'; }
  protected appointmentAria(item: TodayAppointment): string { return `${item.participantLabel}, ${appointmentTypeLabel(item.type)}, od ${this.time(item.startsAt)} do ${this.time(item.endsAt)}, ${appointmentStatusLabel(item.status)}`; }
  private localDate(value: Date): string { return new Intl.DateTimeFormat('en-CA', { timeZone: this.view.timeZoneId }).format(value); }
}

@Component({
  selector: 'app-today-attention-panel', standalone: true, imports: [RouterLink, MatButtonModule],
  template: `<aside class="attention" aria-labelledby="attention-title"><h2 id="attention-title">Wymaga uwagi</h2>@if (items.length) { <ul>@for (item of items.slice(0, 6); track item.id) { <li><strong>{{ item.participantLabel || 'Uczestnik' }}</strong><span>{{ item.title }}</span><small>{{ priorityLabel(item.priority) }}{{ item.dueAt ? ' · termin ' + date(item.dueAt) : '' }}</small></li> }</ul><a mat-button routerLink="/specialist-alerts">Zobacz wszystkie</a> } @else { <p>Brak spraw wymagających reakcji.</p> }</aside>`,
  styleUrl: './specialist-today.page.scss', changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TodayAttentionPanelComponent {
  @Input({ required: true }) items: TodayAttentionItem[] = []; @Input() timeZoneId = 'Europe/Warsaw'; protected readonly priorityLabel = priorityLabel;
  protected date(value: string): string { return new Intl.DateTimeFormat('pl-PL', { timeZone: this.timeZoneId, day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(value)); }
}

@Component({
  selector: 'app-specialist-today-page', imports: [MatButtonModule, DayTimelineComponent, TodayAttentionPanelComponent],
  template: `<section class="today-page" aria-labelledby="today-title" [attr.aria-busy]="state() === 'loading'"><header class="today-header"><div><h1 id="today-title" #title tabindex="-1">Dzisiaj, {{ dateLabel() }}</h1>@if (view()?.currentAppointment; as current) { <p class="summary">Trwa teraz: {{ time(current.startsAt) }} · {{ current.participantLabel }} · {{ typeLabel(current.type) }}</p> } @else if (view()?.nextAppointment; as next) { <p class="summary">Następne: {{ time(next.startsAt) }} · {{ next.participantLabel }} · {{ typeLabel(next.type) }}</p> }</div><div class="date-controls" aria-label="Nawigacja po dniach"><button mat-icon-button type="button" aria-label="Poprzedni dzień" (click)="move(-1)">‹</button><button mat-stroked-button type="button" (click)="goToday()">Dzisiaj</button><button mat-icon-button type="button" aria-label="Następny dzień" (click)="move(1)">›</button></div></header>
    @if (state() === 'loading') { <p class="status" role="status">Wczytywanie planu dnia…</p> } @else if (state() === 'error') { <section class="error-card" role="alert"><h2 tabindex="-1">Nie udało się pobrać planu dnia</h2><p>Sprawdź połączenie i spróbuj ponownie.</p><button mat-flat-button type="button" (click)="load(date() ?? undefined)">Spróbuj ponownie</button></section> } @else if (view(); as data) { <div class="today-layout"><app-day-timeline [view]="data" [now]="now()" (opened)="openDetails($event)" /><app-today-attention-panel [items]="data.attentionItems" [timeZoneId]="data.timeZoneId" /></div> }
    @if (selected(); as appointment) { <section class="details-panel" role="dialog" aria-modal="false" aria-labelledby="details-title" (keydown.escape)="closeDetails()"><button class="close" type="button" aria-label="Zamknij szczegóły" (click)="closeDetails()">×</button><h2 id="details-title" tabindex="-1">{{ appointment.participantLabel }}</h2><p>{{ time(appointment.startsAt) }}–{{ time(appointment.endsAt) }} · {{ typeLabel(appointment.type) }}</p><p>{{ statusLabel(appointment.status) }}{{ appointment.locationMode ? ' · ' + locationLabel(appointment.locationMode) : '' }}{{ appointment.location ? ': ' + appointment.location : '' }}</p>@if (appointment.shortPurpose) { <p>{{ appointment.shortPurpose }}</p> }<button mat-flat-button type="button" (click)="closeDetails()">{{ actionLabel(appointment.availableActions[0]) }}</button></section> }
  </section>`, styleUrl: './specialist-today.page.scss', changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpecialistTodayPage {
  private readonly api = inject(SpecialistTodayApi); private readonly route = inject(ActivatedRoute); private readonly router = inject(Router); private readonly destroyRef = inject(DestroyRef); private readonly host = inject(ElementRef<HTMLElement>);
  protected readonly state = signal<LoadState>('loading'); protected readonly view = signal<SpecialistTodayView | null>(null); protected readonly date = signal<string | null>(null); protected readonly selected = signal<TodayAppointment | null>(null); protected readonly now = signal(new Date());
  protected readonly typeLabel = appointmentTypeLabel; protected readonly statusLabel = appointmentStatusLabel; protected readonly locationLabel = locationModeLabel; protected readonly actionLabel = actionLabel;
  private request = 0; private opener: HTMLElement | null = null;
  protected readonly dateLabel = computed(() => new Intl.DateTimeFormat('pl-PL', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date(`${this.date() ?? this.today()}T12:00:00`)));
  private skipRouteLoadFor: string | null = null;
  constructor() { this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => { const parameter = params.get('date'); if (this.validDate(parameter)) { if (this.skipRouteLoadFor === parameter) { this.skipRouteLoadFor = null; return; } this.date.set(parameter); void this.load(parameter); return; } this.date.set(null); void this.load(); }); const timer = window.setInterval(() => this.now.set(new Date()), 60_000); this.destroyRef.onDestroy(() => window.clearInterval(timer)); }
  protected move(days: number): void { const next = new Date(`${this.date() ?? this.view()?.localDate ?? this.today()}T12:00:00`); next.setDate(next.getDate() + days); void this.navigate(this.isoDate(next)); }
  protected goToday(): void { void this.router.navigate([], { relativeTo: this.route, queryParams: { date: null }, replaceUrl: true }); }
  protected async load(date?: string): Promise<void> { const request = ++this.request; this.state.set('loading'); this.selected.set(null); try { const view = await this.api.get(date); if (request !== this.request) return; this.view.set(view); this.date.set(view.localDate); this.state.set('loaded'); if (!date) { this.skipRouteLoadFor = view.localDate; void this.router.navigate([], { relativeTo: this.route, queryParams: { date: view.localDate }, replaceUrl: true }); } this.focusHeader(); } catch { if (request !== this.request) return; this.state.set('error'); } }
  protected openDetails(appointment: TodayAppointment): void { this.opener = document.activeElement as HTMLElement | null; this.selected.set(appointment); afterNextRender(() => this.host.nativeElement.querySelector('.details-panel h2')?.focus()); }
  protected closeDetails(): void { this.selected.set(null); this.opener?.focus(); this.opener = null; }
  protected time(value: string): string { const zone = this.view()?.timeZoneId ?? 'Europe/Warsaw'; return new Intl.DateTimeFormat('pl-PL', { timeZone: zone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(value)); }
  private navigate(date: string): Promise<boolean> { return this.router.navigate([], { relativeTo: this.route, queryParams: { date }, queryParamsHandling: '' }); }
  private today(): string { return this.isoDate(new Date()); }
  private isoDate(value: Date): string { return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`; }
  private validDate(value: string | null): value is string { return !!value && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(new Date(`${value}T12:00:00`).getTime()); }
  private focusHeader(): void { afterNextRender(() => this.host.nativeElement.querySelector('#today-title')?.focus()); }
}
