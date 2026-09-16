import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { ExerciseImportApi, ImportRecord, RecordDetail } from '../core/exercise-import.api';

@Component({
  selector: 'app-exercise-import-attention-page',
  imports: [FormsModule, RouterLink, MatButtonModule],
  template: `<section class="panel">
    <h1>Rekordy wymagające uwagi</h1>
    <p class="muted">
      Pokazujemy tylko rekordy, których nie można bezpiecznie przygotować automatycznie.
    </p>
    @if (completedDraft(); as versionId) {<p aria-live="polite">Szkic został przygotowany. <a [routerLink]="['/admin/exercise-catalog', versionId]">Otwórz w katalogu</a></p>}
    <ul class="attention">
      @for (record of records(); track record.id) {
        <li>
          <h2>{{ name(record) }}</h2>
          <p>
            Rekord {{ record.rowNumber }} · {{ record.sourceRecordKey || 'bez klucza źródłowego' }}
          </p>
          @if (record.status === 'MATCH_CANDIDATES') {
            @if (details()[record.id]; as detail) {
              <p>Wymaga rozstrzygnięcia dopasowania.</p>
              <ul>
                @for (candidate of detail.matchCandidates; track candidate.id) {
                  <li>
                    <strong>{{ candidate.exerciseName }}</strong>
                    <p>{{ rationale(candidate.reasons) }}</p>
                    <button
                      mat-stroked-button
                      type="button"
                      (click)="decide(record.id, candidate.id, 'SAME')"
                    >
                      To samo ćwiczenie</button
                    ><button
                      mat-stroked-button
                      type="button"
                      (click)="decide(record.id, candidate.id, 'DIFFERENT')"
                    >
                      Nowe ćwiczenie</button
                    ><button
                      mat-button
                      type="button"
                      (click)="decide(record.id, candidate.id, 'UNSURE')"
                    >
                      Nie potrafię rozstrzygnąć
                    </button>
                  </li>
                }
              </ul>
            }
          }
          @if (details()[record.id]; as detail) {@for (proposal of detail.mappingProposals ?? []; track proposal.id) {<section><h3>Mapowanie: {{proposal.dictionaryType}}</h3><p>Wartość źródłowa: {{proposal.rawValue}}</p><label>Wartość katalogowa <select class="app-native-control" [ngModel]="mappingChoice(proposal)" (ngModelChange)="selectMapping(proposal.id, $event)">@for (choice of proposal.canonicalChoices ?? []; track choice.value) {<option [value]="choice.value">{{choice.displayName}}</option>}</select></label><button mat-stroked-button (click)="decideMapping(record.id, proposal.id, 'APPROVED')">Zatwierdź mapowanie</button><button mat-button (click)="decideMapping(record.id, proposal.id, 'REJECTED')">Odrzuć</button></section>} @if (detail.licenseRemediation; as remediation) {<section><h3>Licencja wymaga korekty</h3><p>{{remediation.message}}</p><p class="muted">Utwórz zastępcze źródło z potwierdzoną licencją i wczytaj plik ponownie.</p><a mat-stroked-button routerLink="/admin/exercise-import">Przejdź do importu</a></section>}}
          @if (record.status !== 'MATCH_CANDIDATES') {
            <p>{{ label(record.status) }}</p>
            @if (record.status === 'READY_FOR_DRAFT') {
              <button mat-stroked-button type="button" (click)="retry(record.id)">Ponów utworzenie szkicu</button>
            } @else {<p class="muted">Sprawdź problemy importu lub eksportuj ich listę z ekranu importu.</p>}
          }
        </li>
      } @empty {
        <li>Brak rekordów wymagających uwagi.</li>
      }
    </ul>
    <a mat-button routerLink="/admin/exercise-import">Wróć do importu</a>
  </section>`,
  styles: [
    '.attention{padding:0;list-style:none}.attention>li{padding:1rem 0;border-bottom:1px solid var(--app-border)}h2{font-size:1.1rem;margin:0}button{margin:.25rem .5rem .25rem 0}',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseImportAttentionPage {
  protected readonly records = signal<ImportRecord[]>([]);
  protected readonly details = signal<Record<string, RecordDetail>>({});
  protected readonly completedDraft = signal<string | null>(null);
  private readonly choices = signal<Record<string, string>>({});
  private batchId = '';
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
    return (
      {
        BLOCKED_BY_MAPPING: 'Brakuje mapowania słownika.',
        BLOCKED_BY_LICENSE: 'Brakuje potwierdzenia licencji.',
        READY_FOR_DRAFT: 'Nie udało się utworzyć szkicu; operację można ponowić.',
        INVALID: 'Rekord zawiera błąd wymagający poprawy.',
      }[status] ?? 'Wymaga działania.'
    );
  }

  protected rationale(reasons: unknown): string {
    return Array.isArray(reasons) ? reasons.join(', ') : 'Dopasowanie wymaga ręcznej weryfikacji.';
  }

  protected async decide(recordId: string, candidateId: string, decision: string): Promise<void> {
    const detail = await this.api.decide(recordId, candidateId, decision);
    this.details.update((current) => ({ ...current, [recordId]: detail }));
    if (detail.status !== 'MATCH_CANDIDATES')
      this.records.update((items) => items.filter((item) => item.id !== recordId));
  }

  protected async retry(recordId: string): Promise<void> {
    const result = await this.api.createDraft(recordId);
    if (result.exerciseVersionId) { this.completedDraft.set(result.exerciseVersionId); await this.load(this.batchId); }
  }

  protected mappingChoice(proposal: { id?: string; proposedCanonicalValue?: string }): string {
    return proposal.id ? this.choices()[proposal.id] ?? proposal.proposedCanonicalValue ?? '' : '';
  }
  protected selectMapping(id: string | undefined, value: string): void { if (id) this.choices.update(current => ({...current, [id]: value})); }
  protected async decideMapping(recordId: string, mappingId: string | undefined, decision: 'APPROVED' | 'REJECTED'): Promise<void> {
    if (!mappingId) return;
    await this.api.decideMapping(mappingId, decision, decision === 'APPROVED' ? this.choices()[mappingId] ?? this.details()[recordId]?.mappingProposals?.find((proposal) => proposal.id === mappingId)?.proposedCanonicalValue : undefined);
    const detail = await this.api.record(recordId);
    if (detail.draftVersionId) this.completedDraft.set(detail.draftVersionId);
    await this.load(this.batchId);
  }

  private async load(batchId: string): Promise<void> {
    this.batchId = batchId;
    const statuses = ['MATCH_CANDIDATES', 'BLOCKED_BY_MAPPING', 'BLOCKED_BY_LICENSE', 'INVALID', 'READY_FOR_DRAFT'];
    const pages = await Promise.all(statuses.map((status) => this.api.records(batchId, status)));
    const items = pages.flatMap((page) => page.content);
    const details = await Promise.all(items.map((item) => this.api.record(item.id)));
    const attention = details.filter((detail) => detail.status === 'MATCH_CANDIDATES' || (detail.issues ?? []).some((issue) => ['ERROR', 'BLOCKER'].includes(issue.severity)));
    this.records.set(attention);
    const candidates = attention.filter((item) => item.status === 'MATCH_CANDIDATES');
    this.details.set(Object.fromEntries(details.map((detail) => [detail.id, detail])));
  }
}
