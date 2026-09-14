import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Small, actor-neutral display of an immutable exercise-set version. */
@Component({
  selector: 'app-exercise-set-version-preview',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section class="exercise-set-version-preview">
    <h2>{{ version.title || 'Zestaw ćwiczeń' }} · v{{ version.versionNumber }}</h2>
    <p>{{ items().length }} ćwiczeń@if (duration()) { · {{ duration() }} }</p>
    @if (version.profile || version.targetLevel || version.tags?.length) { <p>{{ characteristics() }}</p> }
    <ol>@for (item of items(); track item.id || item.exerciseVersionId) { <li><strong>{{ item.snapshot?.canonicalName || 'Ćwiczenie' }}</strong> — {{ dose(item.dose) }}@if (item.participantInstruction) { <span> · {{ item.participantInstruction }}</span> }</li> }</ol>
    @if (plannerLink && version.id) { <a [routerLink]="'/my-plans/new'" [queryParams]="{ exerciseSetVersionId: version.id }">Użyj w nowym planie</a> }
  </section>`,
})
export class ExerciseSetVersionPreviewComponent {
  @Input({ required: true }) version!: any;
  @Input() plannerLink = false;
  items = () => [...(this.version.items || [])].sort((a: any, b: any) => (a.position || 0) - (b.position || 0));
  dose(value: any): string { return value ? Object.entries(value).filter(([key]) => key !== 'type').map(([key, item]) => `${key}: ${item}`).join(' · ') : 'Brak dawkowania'; }
  duration(): string | undefined { const seconds = this.version.analysis?.metrics?.estimatedSeconds; return seconds == null ? undefined : `${Math.round(seconds / 60)} min`; }
  characteristics(): string { return [this.version.profile, this.version.targetLevel, ...(this.version.tags || [])].filter(Boolean).join(' · '); }
}
