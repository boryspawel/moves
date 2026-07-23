import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnDestroy,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import {
  ExerciseImportApi,
  ImportBatch,
  ImportIssue,
  ImportSource,
} from '../core/exercise-import.api';

const MAX_FILE_BYTES = 10 * 1024 * 1024;

@Component({
  selector: 'app-exercise-import-page',
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatSelectModule,
  ],
  template: ` <section class="panel import-page">
    <h1>Import ćwiczeń</h1>
    <p class="muted">Wybierz plik JSONL. Aplikacja sprawdzi dane i przygotuje szkice ćwiczeń.</p>
    <p class="status" aria-live="polite" [class.error]="failed()">{{ message() }}</p>

    @if (sources().length > 1) {
      <mat-form-field
        ><mat-label>Źródło</mat-label
        ><mat-select [ngModel]="sourceId()" (ngModelChange)="sourceId.set($event)">
          @for (source of sources(); track source.id) {
            <mat-option [value]="source.id">{{ source.displayName }}</mat-option>
          }
        </mat-select></mat-form-field
      >
    }
    @if (!sources().length) {
      <button mat-stroked-button type="button" (click)="createStarterSource()">
        Zainicjalizuj źródło starterowe
      </button>
    }

    <div class="picker" [class.invalid]="fileError()">
      <input
        #picker
        id="exercise-jsonl"
        type="file"
        accept=".jsonl,application/x-ndjson"
        (change)="choose($event)"
      />
      <button mat-stroked-button type="button" (click)="picker.click()">Wybierz plik JSONL</button>
      @if (file(); as selected) {
        <span>{{ selected.name }} · {{ formatBytes(selected.size) }}</span
        ><button mat-button type="button" (click)="removeFile()">Usuń</button>
      }
      @if (fileError()) {
        <span class="error">{{ fileError() }}</span>
      }
    </div>
    <button mat-flat-button type="button" [disabled]="!canUpload()" (click)="upload()">
      Sprawdź i importuj
    </button>

    @if (batch(); as current) {
      <mat-card
        ><mat-card-content>
          <h2>Podsumowanie importu</h2>
          @if (isProcessing(current)) {
            <p aria-live="polite">Trwa sprawdzanie i przygotowywanie szkiców…</p>
          } @else {
            <p>{{ summary(current) }}</p>
          }
          <dl class="summary">
            <div>
              <dt>Zaimportowano</dt>
              <dd>{{ current.totalCount }}</dd>
            </div>
            <div>
              <dt>Utworzono szkice</dt>
              <dd>{{ current.draftedCount }}</dd>
            </div>
            <div>
              <dt>Bez zmian</dt>
              <dd>{{ current.unchangedCount }}</dd>
            </div>
            <div>
              <dt>Wymaga decyzji</dt>
              <dd>{{ attentionCount(current) }}</dd>
            </div>
            <div>
              <dt>Błędy</dt>
              <dd>{{ current.invalidCount }}</dd>
            </div>
            <div>
              <dt>Blokady</dt>
              <dd>{{ current.blockedCount }}</dd>
            </div>
          </dl>
          @if (!isProcessing(current) && attentionCount(current)) {
            <a
              mat-flat-button
              [routerLink]="['/admin/exercise-import/batches', current.id, 'attention']"
              >Przejdź do rekordów wymagających uwagi</a
            >
          }
          @if (!isProcessing(current) && current.draftedCount > 0) {
            <a
              mat-flat-button
              [routerLink]="['/admin/exercise-review']"
              [queryParams]="{ batchId: current.id }"
              >Zobacz utworzone szkice</a
            >
          }
          <a mat-button routerLink="/catalog">Wróć do katalogu</a>
        </mat-card-content></mat-card
      >
      @if (issues().length) {
        <mat-card
          ><mat-card-content
            ><h2>Problemy</h2>
            @for (group of groupedIssues(); track group.name) {
              <section>
                <h3>{{ group.name }}</h3>
                <ul>
                  @for (issue of group.items; track issue.code + issue.rowNumber) {
                    <li>
                      <strong
                        >{{ issue.rowNumber == null ? 'Plik' : 'Rekord ' + issue.rowNumber
                        }}{{ issue.sourceRecordKey ? ' · ' + issue.sourceRecordKey : '' }}</strong
                      ><br />{{ issue.message }}<br /><span class="muted"
                        >Dotyczy: {{ fieldLabel(issue.jsonPointer) }}.
                        {{ remedy(issue.code) }}</span
                      >
                    </li>
                  }
                </ul>
              </section>
            }
            <button mat-button type="button" (click)="exportIssues('csv')">Pobierz CSV</button
            ><button mat-button type="button" (click)="exportIssues('jsonl')">
              Pobierz JSONL
            </button></mat-card-content
          ></mat-card
        >
      }
      <details>
        <summary>Szczegóły techniczne</summary>
        <p>Przetwarzanie techniczne zakończyło się statusem: {{ current.status }}.</p>
      </details>
    }
  </section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseImportPage implements OnDestroy {
  private readonly api = inject(ExerciseImportApi);
  private timer?: number;
  protected readonly sources = signal<ImportSource[]>([]);
  protected readonly sourceId = signal('');
  protected readonly file = signal<File | null>(null);
  protected readonly fileError = signal('');
  protected readonly batch = signal<ImportBatch | null>(null);
  protected readonly issues = signal<ImportIssue[]>([]);
  protected readonly message = signal('Ładowanie źródeł…');
  protected readonly failed = signal(false);
  protected readonly canUpload = computed(() =>
    Boolean(this.sourceId() && this.file() && !this.fileError()),
  );

  constructor() {
    void this.loadSources();
  }

  ngOnDestroy(): void {
    if (this.timer) window.clearTimeout(this.timer);
  }

  protected choose(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.file.set(file);
    this.fileError.set(
      !file
        ? ''
        : !file.name.toLowerCase().endsWith('.jsonl')
          ? 'Wybierz plik z rozszerzeniem .jsonl.'
          : file.size > MAX_FILE_BYTES
            ? 'Plik nie może przekraczać 10 MiB.'
            : '',
    );
  }

  protected removeFile(): void {
    this.file.set(null);
    this.fileError.set('');
  }

  protected async upload(): Promise<void> {
    const source = this.sourceId(),
      file = this.file();
    if (!source || !file) return;
    await this.run(async () => {
      const result = await this.api.upload(source, file, false);
      await this.refresh(result.batchId);
    });
  }

  protected async createStarterSource(): Promise<void> {
    await this.run(async () => {
      const source = await this.api.createSource({
        code: 'MOVES_STARTER_V1',
        displayName: 'Moves starter exercises V1',
        defaultLocale: 'pl-PL',
        licenseCode: 'MOVES-INTERNAL-AUTHORING-1.0',
        licenseVerified: true,
      });
      await this.reloadSources(source.id);
    });
  }

  protected isProcessing(batch: ImportBatch): boolean {
    return ['QUEUED', 'PROCESSING', 'RECEIVED'].includes(batch.status);
  }

  protected attentionCount(batch: ImportBatch): number {
    return Math.max(0, batch.blockedCount);
  }

  protected summary(batch: ImportBatch): string {
    const attention = this.attentionCount(batch);
    return attention
      ? `Import zakończony. ${batch.draftedCount} szkiców jest gotowych, a ${attention} rekordów wymaga decyzji.`
      : `Import zakończony. Utworzono ${batch.draftedCount} szkiców.`;
  }

  protected formatBytes(size: number): string {
    return size < 1024 * 1024
      ? `${Math.ceil(size / 1024)} KiB`
      : `${(size / 1024 / 1024).toFixed(1)} MiB`;
  }

  protected groupedIssues(): { name: string; items: ImportIssue[] }[] {
    const groups = new Map<string, ImportIssue[]>();
    for (const issue of this.issues()) {
      const name = this.issueCategory(issue);
      groups.set(name, [...(groups.get(name) ?? []), issue]);
    }
    return [...groups].map(([name, items]) => ({ name, items }));
  }

  protected fieldLabel(pointer: string): string {
    return (
      {
        '/license': 'licencja',
        '/schemaVersion': 'wersja formatu',
        '/sourceRecordKey': 'klucz rekordu źródłowego',
        '/equipment': 'sprzęt',
        '/position': 'pozycja',
        '/contributions': 'anatomia',
        '/': 'cały rekord',
      }[pointer] ?? 'dane rekordu'
    );
  }

  protected remedy(code: string): string {
    return (
      {
        MALFORMED_JSON: 'Popraw składnię JSONL i zaimportuj plik ponownie.',
        UNSUPPORTED_SCHEMA_VERSION: 'Użyj obsługiwanej wersji pliku.',
        MAPPING_REQUIRED: 'Uzupełnij mapowanie słownika.',
        LICENSE_NOT_VERIFIED: 'Potwierdź prawo do wykorzystania danych.',
        DRAFT_CREATION_FAILED: 'Ponów utworzenie szkicu po usunięciu przyczyny technicznej.',
      }[code] ?? 'Popraw wskazane dane i zaimportuj plik ponownie.'
    );
  }

  protected async exportIssues(format: 'csv' | 'jsonl'): Promise<void> {
    const current = this.batch();
    if (!current) return;
    await this.api.downloadIssues(current.id, format);
  }

  private async loadSources(): Promise<void> {
    await this.run(() => this.reloadSources());
  }

  private issueCategory(issue: ImportIssue): string {
    if (issue.stage === 'PARSE') return 'Błędy pliku';
    if (issue.code === 'MAPPING_REQUIRED') return 'Brakujące mapowania';
    if (issue.severity === 'WARNING') return 'Ostrzeżenia';
    if (issue.code === 'DRAFT_CREATION_FAILED' || issue.stage === 'MATCH')
      return 'Wymagane decyzje redakcyjne';
    return 'Błędy rekordu';
  }

  private async reloadSources(selected?: string): Promise<void> {
    const sources = await this.api.sources();
    this.sources.set(sources);
    const starter = sources.find((source) => source.code === 'MOVES_STARTER_V1');
    this.sourceId.set(selected ?? starter?.id ?? sources[0]?.id ?? '');
    this.message.set(sources.length ? '' : 'Brak źródła importu.');
  }

  private async refresh(id: string): Promise<void> {
    const batch = await this.api.batch(id);
    this.batch.set(batch);
    if (!this.isProcessing(batch)) this.issues.set(await this.api.issues(id));
    else this.timer = window.setTimeout(() => void this.refresh(id), 1000);
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.failed.set(false);
    try {
      await action();
      this.message.set('');
    } catch (error) {
      this.failed.set(true);
      this.message.set(error instanceof Error ? error.message : 'Operacja nie powiodła się.');
    }
  }
}
