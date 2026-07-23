import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import {
  ExerciseImportApi,
  ExerciseImportApiError,
  ReviewQueueItem,
} from '../core/exercise-import.api';

export const EXERCISE_VERSION_STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Szkic',
  IN_REVIEW: 'W recenzji',
  CHANGES_REQUESTED: 'Wymaga korekty',
  APPROVED: 'Zatwierdzone',
  PUBLISHED: 'Opublikowane',
  WITHDRAWN: 'Wycofane',
};
const AREAS: Record<string, string> = {
  CONTENT: 'treść',
  TECHNIQUE: 'technika',
  ANATOMY_EXPOSURE: 'anatomia i ekspozycja',
  LICENSE: 'licencja',
  MEDIA: 'materiały',
};

@Component({
  selector: 'app-exercise-review-page',
  imports: [RouterLink, MatButtonModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section class="panel review-page">
    <h1>Szkice ćwiczeń</h1>
    <p class="muted">Sprawdź zaimportowane ćwiczenia, popraw dane i zatwierdź wymagane obszary.</p>
    <form (submit)="apply($event)">
      <label>Nazwa <input name="query" [value]="filters()['query'] || ''" /></label
      ><label
        >Status
        <select name="status" [value]="filters()['status'] || ''">
          <option value="">Wszystkie</option>
          @for (entry of statuses; track entry[0]) {
            <option [value]="entry[0]">{{ entry[1] }}</option>
          }
        </select></label
      ><label
        >Gotowość
        <select name="readyToPublish" [value]="filters()['readyToPublish'] || ''">
          <option value="">Wszystkie</option>
          <option value="true">Gotowe do publikacji</option>
          <option value="false">Niegotowe</option>
        </select></label
      ><label
        ><input type="checkbox" name="action" [checked]="filters()['action'] === 'true'" /> Tylko
        wymagające działania</label
      ><label
        >Sortuj
        <select name="sort" [value]="filters()['sort'] || 'default'">
          <option value="default">Wymagające działania</option>
          <option value="newest">Najnowsze</option>
          <option value="name">Nazwa</option>
        </select></label
      ><button mat-stroked-button>Filtruj</button
      ><button type="button" mat-button (click)="clear()">Wyczyść filtry</button>
    </form>
    @if (loading()) {
      <p aria-live="polite">Ładowanie szkiców…</p>
    } @else if (error()) {
      <p role="alert">{{ error() }}</p>
      <button mat-stroked-button (click)="load()">Spróbuj ponownie</button>
    } @else {
      <ul class="queue">
        @for (item of items(); track item.exerciseVersionId) {
          <li>
            <div>
              <h2>{{ item.canonicalName }}</h2>
              <p>
                {{ status(item.versionStatus) }} · wersja {{ item.versionNumber }}
                @if (item.importRowNumber) {
                  · rekord importu {{ item.importRowNumber }}
                }
              </p>
              <p>
                Recenzje: {{ item.completedReviewAreas.length }} /
                {{ item.completedReviewAreas.length + item.missingReviewAreas.length }}.
                @if (item.missingReviewAreas.length) {
                  Brakuje: {{ areas(item.missingReviewAreas) }}.
                }
                Błędy: {{ item.errorCount }}, blokady: {{ item.blockerCount }}.
              </p>
              <p class="muted">Aktualizacja: {{ item.updatedAt | date: 'short' }}</p>
            </div>
            <a mat-stroked-button [routerLink]="['/admin/exercise-review', item.exerciseVersionId]"
              >Otwórz</a
            >
          </li>
        } @empty {
          <li>Brak szkiców spełniających wybrane kryteria.</li>
        }
      </ul>
      <nav aria-label="Paginacja">
        <button mat-button [disabled]="page() === 0" (click)="go(page() - 1)">Poprzednia</button
        ><span>Strona {{ page() + 1 }} z {{ pages() || 1 }}</span
        ><button mat-button [disabled]="page() + 1 >= pages()" (click)="go(page() + 1)">
          Następna
        </button>
      </nav>
    }
  </section>`,
  styles: [
    `
      form {
        display: flex;
        gap: 0.75rem;
        flex-wrap: wrap;
        margin-bottom: 1rem;
      }
      label {
        display: grid;
        gap: 0.25rem;
      }
      input,
      select {
        min-height: 2.75rem;
        max-width: 100%;
      }
      .queue {
        padding: 0;
        list-style: none;
      }
      .queue li {
        display: flex;
        gap: 1rem;
        justify-content: space-between;
        align-items: center;
        padding: 1rem;
        border-bottom: 1px solid var(--app-border);
      }
      h2 {
        font-size: 1.1rem;
        margin: 0;
      }
      @media (max-width: 320px) {
        .queue li {
          padding: 0.75rem;
          align-items: flex-start;
          flex-direction: column;
        }
      }
    `,
  ],
})
export class ExerciseReviewPage {
  protected readonly items = signal<ReviewQueueItem[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly page = signal(0);
  protected readonly pages = signal(0);
  protected readonly filters = signal<Record<string, string>>({});
  protected readonly statuses = Object.entries(EXERCISE_VERSION_STATUS_LABELS);
  private readonly api = inject(ExerciseImportApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  constructor() {
    this.route.queryParamMap.subscribe((params) => {
      const f = Object.fromEntries(params.keys.map((key) => [key, params.get(key) ?? '']));
      this.filters.set(f);
      this.page.set(Math.max(0, Number(f['page'] || 0)));
      this.load();
    });
  }
  protected load(): void {
    this.loading.set(true);
    this.error.set('');
    const f: Record<string, string | number> = { ...this.filters(), page: this.page(), size: 25 };
    delete f['action'];
    if (this.filters()['action'] === 'true') f['actionNeeded'] = 'true';
    void this.api
      .reviewQueue(f)
      .then((r) => {
        this.items.set(r.content);
        this.pages.set(r.totalPages);
      })
      .catch((e) =>
        this.error.set(
          e instanceof ExerciseImportApiError && e.status === 403
            ? 'Nie masz uprawnień do kolejki szkiców.'
            : 'Nie udało się pobrać kolejki szkiców.',
        ),
      )
      .finally(() => this.loading.set(false));
  }
  protected apply(event: SubmitEvent): void {
    event.preventDefault();
    const data = new FormData(event.currentTarget as HTMLFormElement);
    this.navigate({
      query: String(data.get('query') || ''),
      status: String(data.get('status') || ''),
      readyToPublish: String(data.get('readyToPublish') || ''),
      sort: String(data.get('sort') || 'default'),
      action: data.get('action') ? 'true' : '',
      page: '0',
    });
  }
  protected clear(): void {
    this.navigate({});
  }
  protected go(page: number): void {
    this.navigate({ ...this.filters(), page: String(page) });
  }
  protected status(v: string): string {
    return EXERCISE_VERSION_STATUS_LABELS[v] ?? v;
  }
  protected areas(values: string[]): string {
    return values.map((v) => AREAS[v] ?? v).join(', ');
  }
  private navigate(queryParams: Record<string, string>): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: '' });
  }
}
