import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {ActivatedRoute, RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {ExerciseImportApi, ImportRecord, RecordDetail} from '../core/exercise-import.api';

@Component({
  selector: 'app-exercise-import-attention-page',
  imports: [RouterLink, MatButtonModule],
  template: `<section class="panel"><h1>Rekordy wymagające uwagi</h1><p class="muted">Pokazujemy tylko rekordy, których nie można bezpiecznie przygotować automatycznie.</p><ul class="attention">@for (record of records(); track record.id) { <li><h2>{{ name(record) }}</h2><p>Rekord {{ record.rowNumber }} · {{ record.sourceRecordKey || 'bez klucza źródłowego' }}</p>@if (record.status === 'MATCH_CANDIDATES') { @if (details()[record.id]; as detail) { <p>Wymaga rozstrzygnięcia dopasowania.</p><ul>@for (candidate of detail.matchCandidates; track candidate.id) { <li><strong>{{ candidate.exerciseName }}</strong><p>{{ rationale(candidate.reasons) }}</p><button mat-stroked-button type="button" (click)="decide(record.id, candidate.id, 'SAME')">To samo ćwiczenie</button><button mat-stroked-button type="button" (click)="decide(record.id, candidate.id, 'DIFFERENT')">Nowe ćwiczenie</button><button mat-button type="button" (click)="decide(record.id, candidate.id, 'UNSURE')">Nie potrafię rozstrzygnąć</button></li> }</ul> } } @else { <p>{{ label(record.status) }}</p><p class="muted">Sprawdź problemy importu lub eksportuj ich listę z ekranu importu.</p> }</li> } @empty { <li>Brak rekordów wymagających uwagi.</li> }</ul><a mat-button routerLink="/admin/exercise-import">Wróć do importu</a></section>`,
  styles: ['.attention{padding:0;list-style:none}.attention>li{padding:1rem 0;border-bottom:1px solid var(--mat-sys-outline-variant)}h2{font-size:1.1rem;margin:0}button{margin:.25rem .5rem .25rem 0}'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExerciseImportAttentionPage {
  protected readonly records = signal<ImportRecord[]>([]);
  protected readonly details = signal<Record<string, RecordDetail>>({});
  private readonly api = inject(ExerciseImportApi);
  private readonly route = inject(ActivatedRoute);

  constructor() {
    const id = this.route.snapshot.paramMap.get('batchId');
    if (id) void this.load(id);
  }

  protected name(record: ImportRecord): string {
    return record.sourceRecordKey || `Rekord ${record.rowNumber}`;
  }

  protected label(status: string): string {
    return ({
      BLOCKED_BY_MAPPING: 'Brakuje mapowania słownika.',
      BLOCKED_BY_LICENSE: 'Brakuje potwierdzenia licencji.',
      READY_FOR_DRAFT: 'Nie udało się utworzyć szkicu; operację można ponowić.',
      INVALID: 'Rekord zawiera błąd wymagający poprawy.'
    }[status] ?? 'Wymaga działania.');
  }

  protected rationale(reasons: unknown): string {
    return Array.isArray(reasons) ? reasons.join(', ') : 'Dopasowanie wymaga ręcznej weryfikacji.';
  }

  protected async decide(recordId: string, candidateId: string, decision: string): Promise<void> {
    const detail = await this.api.decide(recordId, candidateId, decision);
    this.details.update(current => ({...current, [recordId]: detail}));
    if (detail.status !== 'MATCH_CANDIDATES') this.records.update(items => items.filter(item => item.id !== recordId));
  }

  private async load(batchId: string): Promise<void> {
    const statuses = ['MATCH_CANDIDATES', 'BLOCKED_BY_MAPPING', 'BLOCKED_BY_LICENSE', 'INVALID'];
    const pages = await Promise.all(statuses.map(status => this.api.records(batchId, status)));
    const failed = await this.api.records(batchId, 'READY_FOR_DRAFT', 'ERROR');
    const items = [...pages.flatMap(page => page.content), ...failed.content];
    this.records.set(items);
    const candidates = items.filter(item => item.status === 'MATCH_CANDIDATES');
    const details = await Promise.all(candidates.map(item => this.api.record(item.id)));
    this.details.set(Object.fromEntries(details.map(detail => [detail.id, detail])));
  }
}
