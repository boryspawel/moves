import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ApiFacade } from '../core/api.facade';
import { EditorialCatalogItem, ResponseError } from '../api/generated/src';
import { editorialStatusLabel } from './editorial-status.presentation';

@Component({selector: 'app-exercise-catalog-admin-page', imports: [FormsModule, RouterLink, MatButtonModule], changeDetection: ChangeDetectionStrategy.OnPush, template: `
<section class="panel"><h1>Zarządzanie katalogiem ćwiczeń</h1><p>Twórz i redaguj wersje katalogowe. Opublikowane dane pozostają tylko do odczytu.</p><p><a mat-flat-button routerLink="/admin/exercise-catalog/new">Dodaj ćwiczenie</a> <a mat-stroked-button routerLink="/admin/exercise-import">Importuj</a></p>
<form (ngSubmit)="load()"><label>Wyszukaj <input class="app-native-control" name="query" [(ngModel)]="query" /></label><button mat-stroked-button>Wyszukaj</button></form>
@if (error()) { <p role="alert">{{error()}}</p> } @if (loading()) { <p aria-live="polite">Ładowanie…</p> } @else {<ul>@for (item of items(); track item.versionId) {<li><strong>{{item.canonicalName}}</strong> · wersja {{item.versionNumber}} · {{statusLabel(item.status)}} <a mat-button [routerLink]="['/admin/exercise-catalog', item.versionId]">Otwórz</a></li>} @empty {<li>Brak ćwiczeń.</li>}</ul>}</section>`})
export class ExerciseCatalogAdminPage {
  private readonly api = inject(ApiFacade).catalogAdmin;
  readonly items = signal<EditorialCatalogItem[]>([]); readonly loading = signal(false); readonly error = signal('');
  query = '';
  readonly statusLabel = editorialStatusLabel;
  constructor() { this.load(); }
  async load() { this.loading.set(true); this.error.set(''); try { const page = await this.api.listEditorialExercises({query: this.query || undefined, page: 0, size: 20}); this.items.set(page.content ?? []); } catch (error: unknown) { this.error.set(error instanceof ResponseError && error.response.status === 403 ? 'Brak uprawnień do redakcji katalogu.' : 'Nie udało się pobrać katalogu.'); } finally { this.loading.set(false); } }
}
