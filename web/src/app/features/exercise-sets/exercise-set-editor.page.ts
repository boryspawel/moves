import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import type {
  AnalysisFinding,
  AnalysisView,
  AnatomyAnalysisView,
  Dose,
  ItemRequest,
  ItemRequestPhaseEnum,
  ItemView,
  MetadataRequest,
  MetadataRequestProfileEnum,
  VersionView,
} from '../../api/generated/src';
import { ApiFacade } from '../../core/api.facade';
import { ExercisePickerComponent, ExerciseSelection } from '../catalog/exercise-picker.component';
import { DoseEditorComponent } from './dose-editor.component';
import { BodyMapComponent } from '../anatomy/body-map.component';

const phases: ItemRequestPhaseEnum[] = ['PREPARATION', 'MAIN', 'ACCESSORY', 'COOLDOWN'];

@Component({
  selector: 'app-exercise-set-editor-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    RouterLink,
    ExercisePickerComponent,
    DoseEditorComponent,
    BodyMapComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './exercise-sets.scss',
  template: `<main class="exercise-sets panel" tabindex="-1">
    <a mat-button routerLink="/exercise-sets">← Zestawy</a>
    @if (loading()) {
      <p role="status">Ładowanie wersji…</p>
    } @else if (error()) {
      <section class="error" role="alert">
        <p>Nie udało się pobrać wersji.</p>
        <button mat-stroked-button (click)="load()">Spróbuj ponownie</button>
      </section>
    } @else if (version(); as current) {
      <header class="page-header">
        <div>
          <h1>{{ current.title || 'Zestaw bez nazwy' }}</h1>
          <p>
            {{
              readOnly()
                ? 'Opublikowana wersja tylko do odczytu.'
                : 'Szkic zapisuje metadane automatycznie.'
            }}
          </p>
        </div>
        @if (!readOnly()) {
          <button mat-flat-button [disabled]="!canPublish()" (click)="openPublishDialog()">
            Opublikuj
          </button>
        }
      </header>
      <p class="sr-only" aria-live="polite">{{ announcement() }}</p>
      <div class="editor-grid">
        <section class="metadata">
          <h2>Metadane</h2>
          <label>Tytuł<input #titleInput [formControl]="title" [readonly]="readOnly()" /></label
          ><label
            >Profil<select [formControl]="profile" [disabled]="readOnly()">
              <option value="">Nie określono</option>
              <option value="FULL_SELF_GUIDED">Pełny zestaw</option>
              <option value="WARMUP_MODULE">Rozgrzewka</option>
              <option value="MAIN_MODULE">Część główna</option>
              <option value="ACCESSORY_MODULE">Akcesoria</option>
              <option value="COOLDOWN_MODULE">Wyciszenie</option>
            </select></label
          ><label
            >Opis<textarea [formControl]="description" [readonly]="readOnly()"></textarea></label
          ><label
            >Poziom docelowy<input [formControl]="targetLevel" [readonly]="readOnly()" /></label
          ><label
            >Tagi (oddziel przecinkami)<input [formControl]="tags" [readonly]="readOnly()"
          /></label>
          @if (conflict()) {
            <section class="conflict" role="alert">
              Ktoś zmienił ten szkic.
              <button mat-button (click)="reloadAfterConflict()">Odśwież</button
              ><button mat-button (click)="cancelConflict()">Anuluj moje zmiany</button>
            </section>
          }
        </section>
        <aside class="summary">
          <h2>Podsumowanie</h2>
          <p>{{ current.items?.length || 0 }} ćwiczeń</p>
          <p>Profil: {{ current.profile || 'Nie określono' }}</p>
          <p>Wersja {{ current.versionNumber || '—' }} · {{ current.status }}</p>
          <p>{{ saveState() }}</p>
        </aside>
      </div>
      <section class="analysis" aria-labelledby="analysis-heading">
        <header>
          <h2 id="analysis-heading">Sugestie do zestawu</h2>
          @if (!readOnly()) {
            <button mat-stroked-button [disabled]="analysisLoading()" (click)="retryAnalysis()">
              {{ analysisStale() ? 'Odśwież sugestie' : 'Odśwież' }}
            </button>
          }
        </header>
        @if (analysisLoading()) {
          <p role="status">Sprawdzanie zestawu…</p>
        } @else if (analysisError()) {
          <p class="error" role="alert">Sugestie są chwilowo niedostępne.</p>
          <button mat-stroked-button (click)="retryAnalysis()">Spróbuj ponownie</button>
        } @else if (analysis(); as result) {
          @if (suggestions().length) {
            <ul>
              @for (finding of suggestions(); track $index) {
                <li>
                  {{ suggestionText(finding) }}
                  @if (finding.phase || finding.itemIds?.length || finding.field) {
                    <button mat-button (click)="navigateFinding(finding)">Przejdź do pola</button>
                  }
                </li>
              }
            </ul>
          } @else {
            <p>Brak sugestii do zestawu.</p>
          }
        } @else {
          <p>Sugestie nie są jeszcze dostępne.</p>
        }
      </section>
      <section class="analysis anatomy" aria-labelledby="anatomy-heading">
        <header>
          <h2 id="anatomy-heading">Ekspozycja i wzorce</h2>
          @if (!readOnly()) {
            <button mat-stroked-button [disabled]="anatomyLoading()" (click)="retryAnatomy()">
              {{ anatomyStale() ? 'Odśwież ekspozycję' : 'Odśwież' }}
            </button>
          }
        </header>
        @if (anatomyLoading()) {
          <p role="status">Ładowanie ekspozycji anatomicznej…</p>
        } @else if (anatomyError()) {
          <p class="error" role="alert">Nie udało się pobrać ekspozycji anatomicznej.</p>
          <button mat-stroked-button (click)="retryAnatomy()">Spróbuj ponownie</button>
        } @else if (anatomy(); as result) {
          <p class="muted">
            Ekspozycja anatomiczna jest opisem jakościowym i nie stanowi oceny klinicznej ani
            pomiaru siły.
          </p>
          <label
            >Wybierz kanał ekspozycji<select
              [value]="selectedAnatomyChannel()"
              (change)="selectedAnatomyChannel.set($any($event.target).value)"
            >
              <option value="">Wszystkie kanały</option>
              @for (channel of result.channels || []; track channel.loadChannel) {
                <option [value]="channel.loadChannel">
                  {{ channel.loadChannel || 'Nieokreślony kanał' }}
                </option>
              }
            </select></label
          >
          <h3>Kanały i udziały</h3>
          @if (visibleAnatomyChannels(result).length) {
            <div class="anatomy-channels">
              @for (channel of visibleAnatomyChannels(result); track channel.loadChannel) {
                <section>
                  <h4>{{ channel.loadChannel || 'Nieokreślony kanał' }}</h4>
                  <ul>
                    @for (
                      exposure of channel.structureExposures || [];
                      track exposure.anatomicalStructureId;
                      let rank = $index
                    ) {
                      <li>
                        <strong>#{{ rank + 1 }} · {{ exposure.anatomicalStructureId }}</strong
                        >: {{ exposure.coefficientLow ?? 0 }}–{{ exposure.coefficientHigh ?? 0 }} j.
                        <details>
                          <summary>Rozbicie i dowody</summary>
                          <ul>
                            @for (
                              breakdown of exposure.breakdowns || [];
                              track breakdown.contributionId
                            ) {
                              <li>
                                {{ breakdown.role || 'Brak roli' }} ·
                                {{ breakdown.coefficientLow ?? 0 }}–{{
                                  breakdown.coefficientHigh ?? 0
                                }}
                                j. · {{ breakdown.confidenceClass || 'brak pewności' }} ·
                                {{ breakdown.evidenceGrade || 'brak oceny dowodów' }}
                                @if (breakdown.laterality) {
                                  · {{ breakdown.laterality }}
                                }
                                @if (breakdown.evidence?.length) {
                                  <ul>
                                    @for (evidence of breakdown.evidence || []; track evidence.id) {
                                      <li>
                                        {{
                                          evidence.citation ||
                                            evidence.sourceUri ||
                                            'Brak opisu dowodu'
                                        }}
                                        @if (evidence.evidenceGrade) {
                                          ({{ evidence.evidenceGrade }})
                                        }
                                      </li>
                                    }
                                  </ul>
                                }
                              </li>
                            }
                          </ul>
                        </details>
                      </li>
                    }
                  </ul>
                </section>
              }
            </div>
          } @else {
            <p>Brak ekspozycji do pokazania.</p>
          }
          <h3>Wzorce ruchu</h3>
          @if (result.movementPatterns?.length) {
            <ul>
              @for (pattern of result.movementPatterns; track pattern.pattern) {
                <li>{{ pattern.pattern }} ({{ pattern.itemIds?.length || 0 }} ćw.)</li>
              }
            </ul>
          } @else {
            <p>Brak wzorców ruchu do pokazania.</p>
          }
          @if (result.findings?.length || result.missing?.length) {
            <h3>Ustalenia i braki danych</h3>
            <ul>
              @for (finding of result.findings || []; track finding.code) {
                <li>
                  <strong>{{ finding.code }}</strong
                  >: {{ finding.message || 'Brak opisu' }}
                </li>
              }
              @for (missing of result.missing || []; track missing.itemId) {
                <li>
                  <strong>{{ missing.code }}</strong> — ćwiczenie {{ missing.itemId }}
                </li>
              }
            </ul>
          }
          <app-body-map [analysis]="result" [loading]="anatomyLoading()" [error]="anatomyError()" />
        }
      </section>
      @for (phase of phases; track phase) {
        <section class="phase" [id]="'phase-' + phase" tabindex="-1">
          <h2>{{ phaseLabel(phase) }}</h2>
          @for (item of itemsIn(phase); track item.id) {
            <article class="item" [id]="'item-' + item.id" tabindex="-1">
              <header>
                <div>
                  <h3>{{ item.snapshot?.canonicalName || 'Ćwiczenie' }}</h3>
                  <p class="muted">{{ doseSummary(item.dose) }}</p>
                </div>
                @if (!readOnly() && item.id) {
                  <div class="item-actions">
                    <button mat-button (click)="editing.set(item); dose.set(undefined)">
                      Edytuj dawkowanie</button
                    ><button mat-button (click)="move(item, -1)" [disabled]="item.position === 1">
                      W górę</button
                    ><button
                      mat-button
                      (click)="move(item, 1)"
                      [disabled]="item.position === current.items!.length"
                    >
                      W dół</button
                    ><button mat-button (click)="remove(item)">Usuń</button>
                  </div>
                }
              </header>
            </article>
          }
          @if (!readOnly()) {
            <button mat-stroked-button (click)="openPicker(phase)">Dodaj ćwiczenie</button>
          }
        </section>
      }
      @if (!readOnly() && addingPhase()) {
        <section class="picker-wrap" tabindex="-1" #picker>
          <h2>Dodaj do: {{ phaseLabel(addingPhase()!) }}</h2>
          <app-exercise-picker [selectionEnabled]="true" (selected)="choose($event)" />
          @if (chosen()) {
            <h3>{{ chosen()!.name }}</h3>
            <app-dose-editor (changed)="dose.set($event)" /><button
              mat-flat-button
              [disabled]="!dose()"
              (click)="add()"
            >
              Dodaj ćwiczenie
            </button>
          }
          <button
            mat-button
            (click)="addingPhase.set(undefined); chosen.set(undefined); dose.set(undefined)"
          >
            Anuluj
          </button>
        </section>
      }
      @if (!readOnly() && editing(); as item) {
        <section class="picker-wrap">
          <h2>Edytuj dawkowanie: {{ item.snapshot?.canonicalName || 'Ćwiczenie' }}</h2>
          <p>Wprowadź kompletne dawkowanie. Brakujące wartości nie są uzupełniane.</p>
          <app-dose-editor (changed)="dose.set($event)" /><button
            mat-flat-button
            [disabled]="!dose()"
            (click)="update(item)"
          >
            Zapisz dawkowanie</button
          ><button mat-button (click)="editing.set(undefined); dose.set(undefined)">Anuluj</button>
        </section>
      }
      <dialog #publishDialog>
        <h2>Opublikować wersję?</h2>
        <p>Po publikacji wersja będzie tylko do odczytu.</p>
        <button mat-flat-button (click)="publishDialog.close(); publish()">
          Potwierdzam publikację</button
        ><button mat-button (click)="publishDialog.close()">Anuluj</button>
      </dialog>
    }
  </main>`,
})
export class ExerciseSetEditorPage {
  private readonly api = inject(ApiFacade).exerciseSets;
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private analysisTimer?: ReturnType<typeof setTimeout>;
  private analysisAbort?: AbortController;
  private analysisGeneration = 0;
  private anatomyTimer?: ReturnType<typeof setTimeout>;
  private anatomyAbort?: AbortController;
  private anatomyGeneration = 0;
  private metadataSaveInFlight = false;
  private metadataSaveQueued = false;
  @ViewChild('publishDialog') publishDialog?: ElementRef<HTMLDialogElement>;
  @ViewChild('picker') picker?: ElementRef<HTMLElement>;
  @ViewChild('titleInput') titleInput?: ElementRef<HTMLInputElement>;
  readonly phases = phases;
  readonly version = signal<VersionView | undefined>(undefined);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly conflict = signal(false);
  readonly announcement = signal('');
  readonly saveState = signal('');
  readonly publishing = signal(false);
  readonly addingPhase = signal<ItemRequestPhaseEnum | undefined>(undefined);
  readonly chosen = signal<ExerciseSelection | undefined>(undefined);
  readonly editing = signal<ItemView | undefined>(undefined);
  readonly dose = signal<Dose | undefined>(undefined);
  readonly analysis = signal<AnalysisView | undefined>(undefined);
  readonly analysisLoading = signal(false);
  readonly analysisError = signal(false);
  readonly analysisStale = signal(false);
  readonly anatomy = signal<AnatomyAnalysisView | undefined>(undefined);
  readonly selectedAnatomyChannel = signal('');
  readonly anatomyLoading = signal(false);
  readonly anatomyError = signal(false);
  readonly anatomyStale = signal(false);
  readonly title = new FormControl('', { nonNullable: true });
  readonly profile = new FormControl<MetadataRequestProfileEnum | ''>('', { nonNullable: true });
  readonly description = new FormControl('', { nonNullable: true });
  readonly targetLevel = new FormControl('', { nonNullable: true });
  readonly tags = new FormControl('', { nonNullable: true });
  constructor() {
    this.load();
    [this.title, this.profile, this.description, this.targetLevel, this.tags].forEach((control) =>
      control.valueChanges
        .pipe(debounceTime(500), takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.saveMetadata()),
    );
    this.destroyRef.onDestroy(() => {
      if (this.analysisTimer) clearTimeout(this.analysisTimer);
      this.analysisAbort?.abort();
      if (this.anatomyTimer) clearTimeout(this.anatomyTimer);
      this.anatomyAbort?.abort();
    });
  }
  readOnly() {
    return this.version()?.status !== 'DRAFT';
  }
  canPublish() {
    return !this.readOnly() && !this.metadataSaveInFlight && !this.publishing();
  }
  async load() {
    this.loading.set(true);
    this.error.set(false);
    try {
      const setId = this.route.snapshot.paramMap.get('exerciseSetId');
      const versionId = this.route.snapshot.paramMap.get('versionId');
      if (!setId || !versionId) {
        const created = await this.api.create();
        const first = created.versions?.[0];
        if (!created.id || !first?.id) throw new Error('Missing initial draft');
        await this.router.navigate(['/exercise-sets', created.id, 'versions', first.id, 'edit']);
        return;
      }
      const version = await this.api.version({ setId, versionId });
      this.acceptVersion(version, false);
      this.patchMetadata(version);
      this.announcement.set('Załadowano wersję zestawu.');
      if (version.status === 'PUBLISHED') {
        this.analysis.set(version.analysis);
        void this.runAnatomy();
      } else {
        this.scheduleAnalysis();
        this.scheduleAnatomy();
      }
    } catch {
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }
  private acceptVersion(version: VersionView, reanalyze = true) {
    this.version.set(version);
    if (version.status === 'PUBLISHED') {
      this.analysis.set(version.analysis);
      this.analysisStale.set(false);
      this.anatomyStale.set(false);
      if (reanalyze) void this.runAnatomy();
      return;
    }
    this.analysisStale.set(true);
    this.anatomyStale.set(true);
    if (reanalyze) {
      this.scheduleAnalysis();
      this.scheduleAnatomy();
    }
  }
  private patchMetadata(version: VersionView) {
    this.title.setValue(version.title || '', { emitEvent: false });
    this.profile.setValue(version.profile || '', { emitEvent: false });
    this.description.setValue(version.description || '', { emitEvent: false });
    this.targetLevel.setValue(version.targetLevel || '', { emitEvent: false });
    this.tags.setValue((version.tags || []).join(', '), { emitEvent: false });
  }
  private scheduleAnalysis() {
    if (this.readOnly()) return;
    if (this.analysisTimer) clearTimeout(this.analysisTimer);
    this.analysisTimer = setTimeout(() => void this.runAnalysis(), 350);
  }
  async retryAnalysis() {
    if (this.analysisTimer) clearTimeout(this.analysisTimer);
    await this.runAnalysis();
  }
  private async runAnalysis() {
    const version = this.version();
    if (!version?.exerciseSetId || !version.id || this.readOnly()) return;
    const generation = ++this.analysisGeneration;
    const lockVersion = version.lockVersion;
    this.analysisAbort?.abort();
    const abort = (this.analysisAbort = new AbortController());
    this.analysisLoading.set(true);
    this.analysisError.set(false);
    try {
      const result = await this.api.analysis(
        { setId: version.exerciseSetId, versionId: version.id },
        { signal: abort.signal },
      );
      const current = this.version();
      if (
        generation === this.analysisGeneration &&
        current?.id === version.id &&
        current.lockVersion === lockVersion &&
        result.analyzedLockVersion === lockVersion
      ) {
        this.analysis.set(result);
        this.analysisStale.set(false);
      }
    } catch (error) {
      if (!abort.signal.aborted && generation === this.analysisGeneration)
        this.analysisError.set(true);
    } finally {
      if (generation === this.analysisGeneration) this.analysisLoading.set(false);
    }
  }
  private scheduleAnatomy() {
    if (this.readOnly()) return;
    if (this.anatomyTimer) clearTimeout(this.anatomyTimer);
    this.anatomyTimer = setTimeout(() => void this.runAnatomy(), 350);
  }
  async retryAnatomy() {
    if (this.anatomyTimer) clearTimeout(this.anatomyTimer);
    await this.runAnatomy();
  }
  private async runAnatomy() {
    const version = this.version();
    if (!version?.exerciseSetId || !version.id) return;
    const generation = ++this.anatomyGeneration;
    const lockVersion = version.lockVersion;
    this.anatomyAbort?.abort();
    const abort = (this.anatomyAbort = new AbortController());
    this.anatomyLoading.set(true);
    this.anatomyError.set(false);
    try {
      const result = await this.api.anatomy(
        { setId: version.exerciseSetId, versionId: version.id },
        { signal: abort.signal },
      );
      const current = this.version();
      if (
        generation === this.anatomyGeneration &&
        current?.id === version.id &&
        (this.readOnly() ||
          (current.lockVersion === lockVersion && result.analyzedLockVersion === lockVersion))
      ) {
        this.anatomy.set(result);
        this.anatomyStale.set(false);
      }
    } catch {
      if (!abort.signal.aborted && generation === this.anatomyGeneration)
        this.anatomyError.set(true);
    } finally {
      if (generation === this.anatomyGeneration) this.anatomyLoading.set(false);
    }
  }
  visibleAnatomyChannels(result: AnatomyAnalysisView) {
    const selected = this.selectedAnatomyChannel();
    return (result.channels || []).filter(
      (channel) => !selected || channel.loadChannel === selected,
    );
  }
  saveMetadata() {
    const version = this.version();
    if (!version || this.readOnly()) return;
    if (this.metadataSaveInFlight) {
      this.metadataSaveQueued = true;
      return;
    }
    void this.persistMetadata(version);
  }
  private async persistMetadata(version: VersionView) {
    let continueQueuedSave = false;
    this.metadataSaveInFlight = true;
    this.saveState.set('Zapisywanie…');
    try {
      const updated = await this.api.metadata({
        setId: version.exerciseSetId!,
        versionId: version.id!,
        metadataRequest: this.metadata(version),
      });
      this.version.set(updated);
      this.saveState.set('Zapisano');
      if (this.metadataSaveQueued) {
        this.metadataSaveQueued = false;
        continueQueuedSave = true;
      } else this.acceptVersion(updated);
    } catch (error) {
      this.metadataSaveQueued = false;
      this.handle(error);
    } finally {
      this.metadataSaveInFlight = false;
      if (continueQueuedSave) this.saveMetadata();
    }
  }
  private metadata(version: VersionView): MetadataRequest {
    const profile = this.profile.value;
    return {
      title: this.title.value.trim(),
      ...(profile ? { profile } : {}),
      description: this.description.value.trim() || undefined,
      targetLevel: this.targetLevel.value.trim() || undefined,
      tags: this.tags.value
        .split(',')
        .map((value) => value.trim())
        .filter(Boolean),
      expectedVersion: version.lockVersion,
    } as MetadataRequest;
  }
  choose(selection: ExerciseSelection) {
    this.chosen.set(selection);
    this.dose.set(undefined);
  }
  openPicker(phase: ItemRequestPhaseEnum) {
    this.addingPhase.set(phase);
    queueMicrotask(() => this.picker?.nativeElement.focus());
  }
  async add() {
    const version = this.version();
    const choice = this.chosen();
    const dose = this.dose();
    const phase = this.addingPhase();
    if (!version || !choice || !dose || !phase) return;
    const request: ItemRequest = {
      exerciseVersionId: choice.exerciseVersionId,
      phase,
      dose,
      expectedVersion: version.lockVersion,
    };
    try {
      this.acceptVersion(
        await this.api.add({
          setId: version.exerciseSetId!,
          versionId: version.id!,
          itemRequest: request,
        }),
      );
      this.addingPhase.set(undefined);
      this.chosen.set(undefined);
      this.dose.set(undefined);
      this.announcement.set('Dodano ćwiczenie.');
    } catch (error) {
      this.handle(error);
    }
  }
  async update(item: ItemView) {
    const version = this.version();
    const dose = this.dose();
    if (!version || !item.id || !item.exerciseVersionId || !item.phase || !dose) return;
    const itemRequest: ItemRequest = {
      exerciseVersionId: item.exerciseVersionId,
      phase: item.phase,
      dose,
      participantInstruction: item.participantInstruction,
      specialistInstruction: item.specialistInstruction,
      expectedVersion: version.lockVersion,
    };
    try {
      this.acceptVersion(
        await this.api.update({
          setId: version.exerciseSetId!,
          versionId: version.id!,
          itemId: item.id,
          itemRequest,
        }),
      );
      this.editing.set(undefined);
      this.dose.set(undefined);
      this.announcement.set('Zapisano dawkowanie.');
    } catch (error) {
      this.handle(error);
    }
  }
  async remove(item: ItemView) {
    const version = this.version();
    if (!version || !item.id || version.lockVersion == null) return;
    try {
      this.acceptVersion(
        await this.api.remove({
          setId: version.exerciseSetId!,
          versionId: version.id!,
          itemId: item.id,
          expectedVersion: version.lockVersion,
        }),
      );
      this.announcement.set('Usunięto ćwiczenie.');
    } catch (error) {
      this.handle(error);
    }
  }
  async move(item: ItemView, offset: number) {
    const version = this.version();
    const itemCount = version?.items?.length || 0;
    if (!version || !item.id || item.position == null || itemCount === 0) return;
    const targetPosition = Math.min(itemCount, Math.max(1, item.position + offset));
    if (targetPosition === item.position) return;
    try {
      this.acceptVersion(
        await this.api.move({
          setId: version.exerciseSetId!,
          versionId: version.id!,
          moveRequest: { itemId: item.id, targetPosition, expectedVersion: version.lockVersion },
        }),
      );
      this.announcement.set('Zmieniono kolejność ćwiczenia.');
    } catch (error) {
      this.handle(error);
    }
  }
  async publish() {
    const version = this.version();
    if (!version || version.lockVersion == null || this.publishing()) return;
    this.publishing.set(true);
    try {
      const published = await this.api.publishVersion({
        setId: version.exerciseSetId!,
        versionId: version.id!,
        publishRequest: { expectedVersion: version.lockVersion },
      });
      this.acceptVersion(published, false);
      this.announcement.set('Opublikowano wersję zestawu.');
      await this.router.navigate([
        '/exercise-sets',
        published.exerciseSetId!,
        'versions',
        published.id!,
      ]);
    } catch (error) {
      const status = this.status(error);
      this.handle(error);
      this.analysisStale.set(true);
      if (status !== 409 && status != null) await this.retryAnalysis();
    } finally {
      this.publishing.set(false);
    }
  }
  openPublishDialog() {
    this.publishDialog?.nativeElement.showModal();
  }
  itemsIn(phase: ItemRequestPhaseEnum) {
    return (this.version()?.items || [])
      .filter((item) => item.phase === phase)
      .sort(
        (a, b) => (a.position || 0) - (b.position || 0) || (a.id || '').localeCompare(b.id || ''),
      );
  }
  phaseLabel(phase: ItemRequestPhaseEnum) {
    return {
      PREPARATION: 'Przygotowanie',
      MAIN: 'Część główna',
      ACCESSORY: 'Akcesoria',
      COOLDOWN: 'Wyciszenie',
    }[phase];
  }
  suggestions() {
    return this.analysis()?.findings || [];
  }
  suggestionText(finding: AnalysisFinding) {
    switch (finding.code) {
      case 'TITLE_REQUIRED':
        return 'Zestaw nie ma jeszcze tytułu.';
      case 'PROFILE_REQUIRED':
        return 'Nie określono profilu zestawu.';
      case 'ITEMS_REQUIRED':
        return 'Zestaw nie zawiera ćwiczeń.';
      case 'EQUIPMENT_TRANSITIONS':
        return 'W zestawie często zmienia się wymagany sprzęt.';
      case 'INVALID_PHASE_ORDER':
      case 'PROFILE_PHASE_MISMATCH':
      case 'PROFILE_PHASE_REQUIRED':
        return 'Warto sprawdzić kolejność ćwiczeń.';
      case 'TIME_ESTIMATE_UNAVAILABLE':
      case 'TIME_ESTIMATE_OVERFLOW':
        return 'Szacowany czas wykonania jest niedostępny.';
      case 'TIME_ESTIMATE_PARTIAL':
        return 'Szacowany czas wykonania może być niepełny.';
      case 'CONSECUTIVE_DUPLICATE_EXERCISE':
      case 'DUPLICATE_EXACT_EXERCISE_VERSION':
        return 'Warto sprawdzić powtarzające się ćwiczenia.';
      case 'DOSE_KIND_SWITCHING':
        return 'Warto sprawdzić sposób dawkowania ćwiczeń.';
      default:
        return 'Warto sprawdzić układ zestawu.';
    }
  }
  navigateFinding(finding: AnalysisFinding) {
    const itemId = finding.itemIds?.[0];
    const target = itemId
      ? document.getElementById(`item-${itemId}`)
      : finding.phase
        ? document.getElementById(`phase-${finding.phase}`)
        : finding.field
          ? this.titleInput?.nativeElement
          : undefined;
    if (target) {
      target.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
      target.focus();
      return;
    }
    if (!this.readOnly() && finding.phase === 'PREPARATION') this.openPicker('PREPARATION');
  }
  doseSummary(dose?: Dose) {
    if (!dose) return 'Brak prawidłowego dawkowania';
    switch (dose.type) {
      case 'STRENGTH':
        return `${dose.sets} × ${dose.reps}`;
      case 'AEROBIC':
        return `${dose.durationSeconds} s`;
      case 'ISOMETRIC':
        return `${dose.sets} × ${dose.holdSeconds} s`;
      case 'MOBILITY':
        return dose.reps != null
          ? `${dose.reps} powt.`
          : dose.durationSeconds != null
            ? `${dose.durationSeconds} s`
            : 'Brak prawidłowego dawkowania';
      case 'STRETCH':
        return `${dose.holdSeconds} s`;
      case 'BREATHING':
        return `${dose.cycles} cykli`;
    }
  }
  private status(error: unknown) {
    return error instanceof Response ? error.status : undefined;
  }
  private handle(error: unknown) {
    if (this.status(error) === 409) {
      this.conflict.set(true);
      this.saveState.set('Konflikt zmian');
      return;
    }
    this.saveState.set('Nie udało się zapisać');
  }
  async reloadAfterConflict() {
    this.conflict.set(false);
    await this.load();
  }
  cancelConflict() {
    this.conflict.set(false);
    const version = this.version();
    if (version) this.patchMetadata(version);
  }
}
