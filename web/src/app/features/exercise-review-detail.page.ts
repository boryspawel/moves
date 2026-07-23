import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {ActivatedRoute, RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {ExerciseImportApi, ReviewResult} from '../core/exercise-import.api';

@Component({
  selector: 'app-exercise-review-detail-page',
  imports: [RouterLink, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section class="panel"><a mat-button routerLink="/admin/exercise-review">← Wróć do kolejki</a><h1>Szczegóły recenzji</h1>@if (loading()) { <p aria-live="polite">Ładowanie wymagań recenzji…</p> } @else if (result(); as review) { <p>Stan gotowości: <strong>{{ review.status }}</strong></p><h2>Wymagania</h2><ul>@for (requirement of review.unmetRequirements; track requirement) { <li>{{ label(requirement) }}</li> } @empty { <li>Wszystkie wymagania są spełnione.</li> }</ul><p class="muted">Decyzje recenzenckie oraz publikacja pozostają dostępne wyłącznie w autoryzowanym workflow redakcyjnym.</p> } @else { <p role="alert">Nie udało się pobrać szczegółów recenzji.</p> }</section>`
})
export class ExerciseReviewDetailPage {
  protected readonly result = signal<ReviewResult | null>(null);
  protected readonly loading = signal(true);
  private readonly api = inject(ExerciseImportApi);
  private readonly route = inject(ActivatedRoute);

  constructor() {
    const id = this.route.snapshot.paramMap.get('versionId');
    if (id) void this.api.reviewStatus(id).then(result => this.result.set(result)).finally(() => this.loading.set(false)); else this.loading.set(false);
  }

  protected label(value: string): string {
    return ({
      REVIEW_CONTENT_REQUIRED: 'Wymagana recenzja treści',
      REVIEW_TECHNIQUE_REQUIRED: 'Wymagana recenzja techniki',
      REVIEW_ANATOMY_EXPOSURE_REQUIRED: 'Wymagana recenzja anatomii i ekspozycji',
      REVIEW_LICENSE_REQUIRED: 'Wymagana recenzja licencji',
      REVIEW_MEDIA_REQUIRED: 'Wymagana recenzja materiałów',
      TWO_INDEPENDENT_REVIEWERS_REQUIRED: 'Wymaganych jest dwóch niezależnych recenzentów'
    }[value] ?? value);
  }
}
