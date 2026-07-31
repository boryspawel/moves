import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import type { SetView, VersionSummary } from '../../api/generated/src';
import { ApiFacade } from '../../core/api.facade';

@Component({
  selector: 'app-exercise-set-list-page',
  imports: [MatButtonModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './exercise-sets.scss',
  template: `<main class="exercise-sets panel" tabindex="-1"><header class="page-header"><div><h1>Zestawy ćwiczeń</h1><p>Twórz wersjonowane zestawy i publikuj tylko gotowe wersje.</p></div><a mat-flat-button routerLink="/exercise-sets/new">Nowy zestaw</a></header>
  <p class="sr-only" aria-live="polite">{{ announcement() }}</p>
  @if (loading()) { <p role="status">Ładowanie zestawów…</p> } @else if (error()) { <section class="error" role="alert"><p>Nie udało się pobrać zestawów.</p><button mat-stroked-button (click)="load()">Spróbuj ponownie</button></section> } @else if (!sets().length) { <section class="empty-state"><h2>Nie masz jeszcze zestawów.</h2><p>Utwórz pierwszy szkic, aby zacząć komponowanie.</p><a mat-flat-button routerLink="/exercise-sets/new">Utwórz zestaw</a></section> } @else { <ul class="set-list">@for (set of sets(); track set.id) { <li><article><h2>{{ latest(set)?.title || 'Zestaw bez tytułu' }}</h2><p>{{ latest(set)?.status === 'PUBLISHED' ? 'Opublikowano' : 'Szkic' }} · wersja {{ latest(set)?.versionNumber || '—' }}</p>@if (latest(set)?.id && set.id) {<a mat-button [routerLink]="routeFor(set, latest(set)!)">{{ latest(set)?.status === 'DRAFT' ? 'Edytuj' : 'Zobacz' }}</a>}</article></li>}</ul> }</main>`
})
export class ExerciseSetListPage {
  private readonly api = inject(ApiFacade).exerciseSets;
  readonly sets = signal<SetView[]>([]); readonly loading = signal(true); readonly error = signal(false); readonly announcement = signal('Ładowanie zestawów.');
  constructor() { this.load(); }
  async load() { this.loading.set(true); this.error.set(false); try { const sets = await this.api.list(); this.sets.set(sets); this.announcement.set(`Załadowano ${sets.length} zestawów.`); } catch { this.error.set(true); this.announcement.set('Nie udało się pobrać zestawów.'); } finally { this.loading.set(false); } }
  latest(set: SetView): VersionSummary | undefined { return [...(set.versions || [])].sort((a, b) => (b.versionNumber || 0) - (a.versionNumber || 0))[0]; }
  routeFor(set: SetView, version: VersionSummary): string[] { return version.status === 'DRAFT' ? ['/exercise-sets', set.id!, 'versions', version.id!, 'edit'] : ['/exercise-sets', set.id!, 'versions', version.id!]; }
}
