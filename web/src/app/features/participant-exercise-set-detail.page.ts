import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';
import type { ParticipantVersionView } from '../api/generated/src';
import { ApiFacade } from '../core/api.facade';
import { ExerciseSetVersionPreviewComponent } from './exercise-sets/exercise-set-version-preview.component';

@Component({ selector: 'app-participant-exercise-set-detail-page', standalone: true, imports: [MatButtonModule, RouterLink, ExerciseSetVersionPreviewComponent], changeDetection: ChangeDetectionStrategy.OnPush, template: `<main class="panel"><a mat-button routerLink="/my-exercise-sets">← Moje zestawy</a>@if (loading()) { <p role="status">Ładowanie zestawu…</p> } @else if (error()) { <p role="alert">Nie udało się pobrać zestawu.</p> } @else if (version(); as current) { <app-exercise-set-version-preview [version]="current" [plannerLink]="true" /> }</main>` })
export class ParticipantExerciseSetDetailPage {
  private readonly api = inject(ApiFacade).participantExerciseSets; private readonly route = inject(ActivatedRoute);
  readonly version = signal<ParticipantVersionView | undefined>(undefined); readonly loading = signal(true); readonly error = signal(false);
  constructor() { void this.load(); }
  async load() { const versionId = this.route.snapshot.paramMap.get('versionId'); if (!versionId) { this.error.set(true); this.loading.set(false); return; } try { this.version.set(await this.api.version1({ versionId })); } catch { this.error.set(true); } finally { this.loading.set(false); } }
}
