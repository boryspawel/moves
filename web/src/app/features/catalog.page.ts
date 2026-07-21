import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { CatalogItem } from '../api/generated/src';
import { ApiFacade } from '../core/api.facade';

@Component({
  selector: 'app-catalog-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <section class="panel">
      <h1>Katalog ćwiczeń</h1>
      <p class="muted">Wyświetlane są wyłącznie opublikowane, niezmienne wersje ćwiczeń.</p>
      <form (ngSubmit)="load()">
        <mat-form-field><mat-label>Szukaj po nazwie lub instrukcji</mat-label><input matInput type="search" [formControl]="query"></mat-form-field>
        <button mat-flat-button type="submit">Szukaj</button>
      </form>
      <p class="status" aria-live="polite" [class.error]="error()">{{ status() }}</p>
      <ul class="card-list">
        @for (exercise of exercises(); track exercise.versionId) {
          <li>
            <h2>{{ exercise.canonicalName }} <small>v{{ exercise.versionNumber }}</small></h2>
            <p class="muted">{{ exercise.primaryMovementPattern }} · {{ exercise.technicalLevel }} · {{ exercise.environment }}</p>
          </li>
        } @empty { <li>Brak ćwiczeń spełniających kryteria.</li> }
      </ul>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CatalogPage {
  private readonly api = inject(ApiFacade).catalog;
  protected readonly query = new FormControl('', { nonNullable: true });
  protected readonly exercises = signal<CatalogItem[]>([]);
  protected readonly status = signal('Ładowanie…');
  protected readonly error = signal(false);
  constructor() { void this.load(); }
  protected async load(): Promise<void> {
    this.error.set(false);
    try {
      const page = await this.api.list({ query: this.query.value || undefined });
      this.exercises.set(page.content ?? []);
      this.status.set(`${page.totalElements ?? this.exercises().length} wyników.`);
    }
    catch { this.error.set(true); this.status.set('Nie udało się pobrać katalogu.'); }
  }
}
