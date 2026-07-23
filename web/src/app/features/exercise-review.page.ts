import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {ExerciseImportApi, ReviewQueueItem} from '../core/exercise-import.api';

const STATUS: Record<string, string> = {
  DRAFT: 'Szkic',
  IN_REVIEW: 'W recenzji',
  CHANGES_REQUESTED: 'Wymaga korekty',
  APPROVED: 'Zatwierdzone',
  PUBLISHED: 'Opublikowane',
  WITHDRAWN: 'Wycofane'
};

@Component({
  selector: 'app-exercise-review-page',
  imports: [RouterLink, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel"><h1>Szkice ćwiczeń</h1>
      <p class="muted">Sprawdź zaimportowane ćwiczenia, popraw dane i zatwierdź wymagane obszary.</p>
      <form (submit)="search($event)"><label>Wyszukiwanie <input #query [value]="filters().query || ''"></label><label>Status
        <select #status [value]="filters().status || ''">
          <option value="">Wszystkie</option>
          <option value="DRAFT">Szkic</option>
          <option value="CHANGES_REQUESTED">Wymaga korekty</option>
          <option value="IN_REVIEW">W recenzji</option>
          <option value="APPROVED">Zatwierdzone</option>
          <option value="PUBLISHED">Opublikowane</option>
        </select></label>
        <button mat-stroked-button>Filtruj</button>
        <button type="button" mat-button (click)="clear()">Wyczyść filtry</button>
      </form>
      @if (loading()) {
        <p aria-live="polite">Ładowanie szkiców…</p>
      } @else if (error()) {
        <p role="alert">{{ error() }}</p>
        <button mat-stroked-button (click)="load()">Spróbuj ponownie</button>
      } @else {
        <ul class="queue">@for (item of items(); track item.exerciseVersionId) {
          <li><h2>{{ item.canonicalName }}</h2>
            <p>{{ statusLabel(item.versionStatus) }} · wersja {{ item.versionNumber }}@if (item.importRowNumber) {
              · rekord {{ item.importRowNumber }}
            }</p>
            <p>Recenzje: {{ item.completedReviewAreas.length }} zakończone, {{ item.missingReviewAreas.length }}
              brakujące. Błędy: {{ item.errorCount }}, blokady: {{ item.blockerCount }}.</p><a mat-stroked-button
                                                                                               [routerLink]="['/admin/exercise-review', item.exerciseVersionId]">Otwórz</a>
          </li>
        } @empty {
          <li>Brak szkiców spełniających wybrane kryteria.</li>
        }</ul>
        <nav aria-label="Paginacja">
          <button mat-button [disabled]="page()===0" (click)="go(page()-1)">Poprzednia</button>
          <span>Strona {{ page() + 1 }}</span>
          <button mat-button [disabled]="(page()+1)*25 >= total()" (click)="go(page()+1)">Następna</button>
        </nav>
      }</section>`,
  styles: ['form{display:flex;gap:1rem;flex-wrap:wrap;margin-bottom:1rem}.queue{padding:0;list-style:none}.queue li{padding:1rem;border-bottom:1px solid var(--mat-sys-outline-variant)}h2{font-size:1.1rem}input,select{display:block;min-height:2.5rem;max-width:100%}@media(max-width:320px){form{gap:.5rem}.queue li{padding:.75rem}}']
})
export class ExerciseReviewPage {
  protected readonly items = signal<ReviewQueueItem[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly filters = signal<Record<string, string>>({});
  private readonly api = inject(ExerciseImportApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  constructor() {
    this.route.queryParamMap.subscribe(params => {
      const filters = Object.fromEntries([...params.keys].map(key => [key, params.get(key) ?? '']));
      this.filters.set(filters);
      this.page.set(Number(filters['page'] || 0));
      this.load();
    });
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set('');
    const f = this.filters();
    void this.api.reviewQueue({...f, page: this.page(), size: 25}).then(result => {
      this.items.set(result.content);
      this.total.set(result.totalElements);
    }).catch(() => this.error.set('Nie udało się pobrać kolejki szkiców.')).finally(() => this.loading.set(false));
  }

  protected search(event: SubmitEvent): void {
    event.preventDefault();
    const form = event.currentTarget as HTMLFormElement;
    const data = new FormData(form);
    this.navigate({query: String(data.get('query') ?? ''), status: String(data.get('status') ?? ''), page: '0'});
  }

  protected clear(): void {
    this.navigate({});
  }

  protected go(page: number): void {
    this.navigate({...this.filters(), page: String(page)});
  }

  protected statusLabel(value: string): string {
    return STATUS[value] ?? value;
  }

  private navigate(queryParams: Record<string, string>): void {
    void this.router.navigate([], {relativeTo: this.route, queryParams, queryParamsHandling: ''});
  }
}
