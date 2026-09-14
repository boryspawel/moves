import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import type { ParticipantLibraryEntry } from '../api/generated/src';
import { ApiFacade } from '../core/api.facade';

@Component({ selector: 'app-participant-exercise-set-library-page', standalone: true, imports: [MatButtonModule, RouterLink], changeDetection: ChangeDetectionStrategy.OnPush, template: `<main class="panel" aria-labelledby="library-title"><h1 id="library-title">Moje zestawy ćwiczeń</h1>@if (loading()) { <p role="status">Ładowanie zestawów…</p> } @else if (error()) { <p role="alert">Nie udało się pobrać biblioteki. <button mat-button (click)="load()">Spróbuj ponownie</button></p> } @else if (!entries().length) { <p>Nie masz jeszcze udostępnionych zestawów.</p> } @else { <ul>@for (entry of entries(); track entry.versionId) { <li><a [routerLink]="['/my-exercise-sets', entry.versionId]">{{ entry.title || 'Zestaw ćwiczeń' }} · v{{ entry.versionNumber }}</a><span> · {{ entry.profile || 'profil nieokreślony' }}</span></li> }</ul> }</main>` })
export class ParticipantExerciseSetLibraryPage {
  private readonly api = inject(ApiFacade).participantExerciseSets;
  readonly entries = signal<ParticipantLibraryEntry[]>([]); readonly loading = signal(true); readonly error = signal(false);
  constructor() { void this.load(); }
  async load() { this.loading.set(true); this.error.set(false); try { this.entries.set(await this.api.list3()); } catch { this.error.set(true); } finally { this.loading.set(false); } }
}
