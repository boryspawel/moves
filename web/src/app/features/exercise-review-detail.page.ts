import { ChangeDetectionStrategy, Component, HostListener, inject, signal } from '@angular/core';
import {
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  EditorialDetail,
  ExerciseImportApi,
  ExerciseImportApiError,
} from '../core/exercise-import.api';
import { EXERCISE_VERSION_STATUS_LABELS } from './exercise-review.page';

const LABELS: Record<string, string> = {
  CONTENT: 'Treść',
  TECHNIQUE: 'Technika',
  ANATOMY_EXPOSURE: 'Anatomia i ekspozycja',
  LICENSE: 'Licencja',
  MEDIA: 'Materiały',
  APPROVED: 'Zatwierdzono',
  CHANGES_REQUESTED: 'Wymagane korekty',
  PENDING: 'Oczekuje',
  REVIEW_CONTENT_REQUIRED: 'Wymagana recenzja treści',
  REVIEW_TECHNIQUE_REQUIRED: 'Wymagana recenzja techniki',
  REVIEW_ANATOMY_EXPOSURE_REQUIRED: 'Wymagana recenzja anatomii i ekspozycji',
  REVIEW_LICENSE_REQUIRED: 'Wymagana recenzja licencji',
  REVIEW_MEDIA_REQUIRED: 'Wymagana recenzja materiałów',
  LOAD_CHARACTERISTIC_REQUIRED: 'Brakuje profilu obciążenia',
  ANATOMY_EXPOSURE_REQUIRED: 'Brakuje anatomii',
  EVIDENCE_REQUIRED: 'Brakuje dowodów',
  UNRESOLVED_IMPORT_ISSUES: 'Są nierozwiązane problemy importu',
  SQUAT: 'Przysiad',
  HINGE: 'Zgięcie w biodrze',
  PUSH: 'Pchanie',
  PULL: 'Przyciąganie',
  LUNGE: 'Wykrok',
  CARRY: 'Przenoszenie',
  ROTATION: 'Rotacja',
  LOCOMOTION: 'Lokomo­cja',
  BREATHING: 'Oddychanie',
  MOBILITY: 'Mobilność',
  OTHER: 'Inne',
  STRENGTH: 'Siła',
  RECOVERY: 'Regeneracja',
  MOTOR_CONTROL: 'Kontrola ruchu',
  LOW: 'Niskie',
  MODERATE: 'Umiarkowane',
  HIGH: 'Wysokie',
  BEGINNER: 'Początkujący',
  FOUNDATIONAL: 'Podstawowy',
  INTERMEDIATE: 'Średniozaawansowany',
  ADVANCED: 'Zaawansowany',
  GYM: 'Siłownia',
  HOME: 'Dom',
  CLINIC: 'Gabinet',
  ANY: 'Dowolne',
  STANDING: 'Stojąca',
  SEATED: 'Siedząca',
  SUPINE: 'Leżenie tyłem',
  BODYWEIGHT: 'Masa ciała',
  MAT: 'Mata',
  WALL: 'Ściana',
  CHAIR: 'Krzesło',
  SAGITTAL: 'Strzałkowa',
  FRONTAL: 'Czołowa',
  TRANSVERSE: 'Poprzeczna',
  MULTIPLANAR: 'Wielopłaszczyznowa',
  CONCENTRIC: 'Koncentryczny',
  ECCENTRIC: 'Ekscentryczny',
  ISOMETRIC: 'Izometryczny',
  MIXED: 'Mieszany',
  FULL: 'Pełny',
  PARTIAL: 'Częściowy',
  VARIABLE: 'Zmienny',
  DYNAMIC: 'Dynamiczny',
  STABILIZATION: 'Stabilizacja',
  PRIMARY: 'Główny',
  SECONDARY: 'Drugorzędny',
  STABILIZER: 'Stabilizator',
  DYN_EXU: 'Dynamiczne',
  ISO_SEC: 'Izometryczne',
};
const MOVEMENT_PATTERNS = [
  'SQUAT',
  'HINGE',
  'PUSH',
  'PULL',
  'LUNGE',
  'CARRY',
  'ROTATION',
  'LOCOMOTION',
  'BREATHING',
  'MOBILITY',
  'OTHER',
];
const EQUIPMENT = ['BODYWEIGHT', 'MAT', 'WALL', 'CHAIR'];
const SELECTS = {
  stimulusType: ['STRENGTH', 'RECOVERY', 'MOTOR_CONTROL'],
  fatigueProfile: ['LOW', 'MODERATE', 'HIGH'],
  technicalLevel: ['BEGINNER', 'FOUNDATIONAL', 'INTERMEDIATE', 'ADVANCED'],
  environment: ['GYM', 'HOME', 'CLINIC', 'ANY'],
};

@Component({
  selector: 'app-exercise-review-detail-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './exercise-review-detail.page.scss',
  template: ` <main class="review-page" #page tabindex="-1">
    <a mat-button routerLink="/admin/exercise-review">← Wróć do kolejki</a>
    @if (loading()) {
      <p aria-live="polite">Ładowanie szkicu…</p>
    } @else if (error()) {
      <p role="alert">{{ error() }}</p>
      <button mat-stroked-button (click)="load()">Spróbuj ponownie</button>
    } @else if (detail(); as d) {
      <header class="review-header">
        <div>
          <h1>{{ d.editor.version.canonicalName }}</h1>
          <p>{{ status(d.editor.version.status) }} · wersja {{ d.editor.version.versionNumber }}</p>
        </div>
        @if (readonly()) {
          <a mat-flat-button [routerLink]="['/catalog', d.editor.version.exerciseVersionId]"
            >Zobacz w katalogu</a
          >
        }
      </header>
      @if (message()) {
        <p class="notice" [class.error]="isError()" aria-live="polite">{{ message() }}</p>
      }
      <div class="review-layout">
        <div class="review-content">
          @if (readonly()) {
            <p class="readonly" role="status">
              Wersja jest opublikowana i dostępna wyłącznie do odczytu.
            </p>
          }
          <section class="panel">
            <h2>Podstawowe dane</h2>
            @if (editing()) {
              <form [formGroup]="editForm" (ngSubmit)="save()" class="form-grid">
                <mat-form-field class="full"
                  ><mat-label>Nazwa</mat-label
                  ><input matInput formControlName="canonicalName" /></mat-form-field
                ><mat-form-field class="full"
                  ><mat-label>Instrukcja</mat-label
                  ><textarea
                    matInput
                    formControlName="instruction"
                    rows="5"
                  ></textarea></mat-form-field
                ><mat-form-field
                  ><mat-label>Wzorce ruchowe</mat-label
                  ><mat-select multiple formControlName="movementPatterns">
                    @for (x of movementPatterns; track x) {
                      <mat-option [value]="x">{{ label(x) }}</mat-option>
                    }
                  </mat-select></mat-form-field
                ><mat-form-field
                  ><mat-label>Sprzęt</mat-label
                  ><mat-select multiple formControlName="requiredEquipment">
                    @for (x of equipment; track x) {
                      <mat-option [value]="x">{{ label(x) }}</mat-option>
                    }
                  </mat-select></mat-form-field
                >
                @for (field of selectFields; track field) {
                  <mat-form-field
                    ><mat-label>{{ fieldLabel(field) }}</mat-label
                    ><mat-select [formControlName]="field">
                      @for (x of selectOptions[field]; track x) {
                        <mat-option [value]="x">{{ label(x) }}</mat-option>
                      }
                    </mat-select></mat-form-field
                  >
                }
                <div class="actions full">
                  <button mat-flat-button [disabled]="saving() || editForm.invalid">
                    {{ saving() ? 'Zapisywanie…' : 'Zapisz korektę' }}</button
                  ><button
                    mat-button
                    type="button"
                    [disabled]="saving()"
                    (click)="editing.set(false)"
                  >
                    Anuluj
                  </button>
                </div>
              </form>
            } @else {
              <p>{{ d.editor.version.instruction }}</p>
              <p>
                <strong>Wzorce:</strong> {{ labels(d.editor.version.movementPatterns)
                }}<br /><strong>Sprzęt:</strong>
                {{ labels(d.editor.version.requiredEquipment, 'Brak specjalnego sprzętu') }}
              </p>
              @if (!readonly()) {
                <button mat-stroked-button (click)="startEditing(d)">Edytuj</button>
              }
            }
          </section>
          <section class="panel">
            <h2>Profil obciążenia</h2>
            <div class="load-grid">
              @for (x of d.editor.loadCharacteristics; track $index) {
                <article class="load-card">
                  <span>{{ label(value(x, 'characteristicType')) }}</span
                  ><small
                    >{{ label(value(x, 'movementPlane')) }} ·
                    {{ label(value(x, 'contractionType')) }} ·
                    {{ label(value(x, 'rangeOfMotion')) }}</small
                  >
                </article>
              } @empty {
                <p>Brak profilu obciążenia.</p>
              }
            </div>
          </section>
          <section class="panel">
            <h2>Anatomia</h2>
            <ul class="anatomy-list">
              @for (x of d.anatomyContributions; track x.code + x.role) {
                <li>
                  <strong>{{ x.displayName || label(x.code) }}</strong
                  ><span
                    >{{ label(x.structureType) }} · {{ label(x.role) }} ·
                    {{ label(x.loadChannel) }} · {{ label(x.contributionBand) }}</span
                  ><small>{{ label(x.confidenceClass) }} · {{ label(x.evidenceGrade) }}</small>
                </li>
              } @empty {
                <li>Brak wkładów anatomicznych.</li>
              }
            </ul>
          </section>
          <section class="panel">
            <h2>Dowody</h2>
            <ul class="evidence-list">
              @for (x of d.editor.evidence; track $index) {
                <li>
                  {{ value(x, 'citation') }} <span>{{ label(value(x, 'evidenceGrade')) }}</span>
                  @if (safeUri(value(x, 'sourceUri')); as uri) {
                    <a [href]="uri" target="_blank" rel="noopener noreferrer">Otwórz źródło</a>
                  }
                </li>
              } @empty {
                <li>Brak dowodów.</li>
              }
            </ul>
          </section>
          @if (d.importProblems.length || d.importMetadata) {
            <details>
              <summary>Szczegóły importu</summary>
              @if (d.importMetadata) {
                <p>
                  Klucz źródłowy: {{ d.importMetadata.sourceRecordKey || 'Nieokreślone' }} · rekord
                  {{ d.importMetadata.rowNumber }}
                </p>
              }
              <ul>
                @for (p of d.importProblems; track p.code + p.message) {
                  <li>
                    <strong>{{ label(p.severity) }}</strong
                    >: {{ p.message }}
                  </li>
                }
              </ul>
            </details>
          }
        </div>
        <aside class="review-sidebar panel">
          <h2>Recenzja i publikacja</h2>
          <ul class="requirements">
            @for (requirement of d.review.unmetRequirements; track requirement) {
              <li>{{ label(requirement) }}</li>
            } @empty {
              <li>Wszystkie wymagania są spełnione.</li>
            }
          </ul>
          <div class="areas">
            @for (area of reviewAreas(d); track area) {
              <article class="review-area" [id]="'review-' + area" tabindex="-1">
                <h3>{{ label(area) }}</h3>
                <p>{{ areaState(d, area) }}</p>
                @if (!readonly()) {
                  <form [formGroup]="areaForm(area)" (ngSubmit)="review(area, 'APPROVED')">
                    <mat-form-field
                      ><mat-label>Komentarz recenzenta</mat-label
                      ><textarea matInput formControlName="comment" rows="3"></textarea>
                    </mat-form-field>
                    <div class="actions">
                      <button mat-stroked-button [disabled]="submitting(area)">Zatwierdź</button
                      ><button
                        mat-button
                        type="button"
                        [disabled]="submitting(area)"
                        (click)="review(area, 'CHANGES_REQUESTED')"
                      >
                        Poproś o korektę
                      </button>
                    </div>
                  </form>
                }
              </article>
            }
          </div>
          @if (!readonly()) {
            @if (d.readyToPublish) {
              <button mat-flat-button [disabled]="publishing()" (click)="publish()">
                {{ publishing() ? 'Publikowanie…' : 'Opublikuj ćwiczenie' }}
              </button>
            } @else {
              <button mat-flat-button disabled aria-describedby="publish-requirements">
                Opublikuj ćwiczenie
              </button>
              <p id="publish-requirements" class="muted">
                Publikacja będzie możliwa po spełnieniu wymagań.
              </p>
            }
          }
        </aside>
      </div>
    }
  </main>`,
})
export class ExerciseReviewDetailPage {
  protected readonly detail = signal<EditorialDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected readonly isError = signal(false);
  protected readonly editing = signal(false);
  protected readonly readonly = signal(false);
  protected readonly saving = signal(false);
  protected readonly publishing = signal(false);
  protected readonly submittingArea = signal<string | null>(null);
  protected readonly movementPatterns = MOVEMENT_PATTERNS;
  protected readonly equipment = EQUIPMENT;
  protected readonly selectOptions = SELECTS;
  protected readonly selectFields = Object.keys(SELECTS) as Array<keyof typeof SELECTS>;
  protected readonly editForm = inject(FormBuilder).nonNullable.group({
    canonicalName: ['', Validators.required],
    instruction: ['', Validators.required],
    movementPatterns: [[] as string[], Validators.required],
    requiredEquipment: [[] as string[]],
    stimulusType: ['', Validators.required],
    fatigueProfile: ['', Validators.required],
    technicalLevel: ['', Validators.required],
    environment: ['', Validators.required],
  });
  private readonly forms = new Map<string, FormGroup<{ comment: FormControl<string> }>>();
  private id = '';
  private readonly api = inject(ExerciseImportApi);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  constructor() {
    this.id = this.route.snapshot.paramMap.get('versionId') || '';
    this.load();
  }
  @HostListener('window:beforeunload', ['$event']) unload(event: BeforeUnloadEvent): void {
    if (this.editing()) {
      event.preventDefault();
      event.returnValue = true;
    }
  }
  protected load(restore = false, focusId?: string): void {
    if (!this.id) {
      this.loading.set(false);
      this.error.set('Nie znaleziono szkicu.');
      return;
    }
    const scrollY = restore ? window.scrollY : 0;
    const activeId = focusId ?? (restore ? document.activeElement?.id : undefined);
    this.loading.set(true);
    this.error.set('');
    void this.api
      .editorialDetail(this.id)
      .then((d) => {
        this.detail.set(d);
        this.readonly.set(d.editor.version.status === 'PUBLISHED');
        if (restore)
          requestAnimationFrame(() => {
            window.scrollTo({ top: scrollY });
            (activeId ? document.getElementById(activeId) : null)?.focus();
          });
      })
      .catch((e) =>
        this.error.set(
          e instanceof ExerciseImportApiError && e.status === 403
            ? 'Nie masz uprawnień do tego szkicu.'
            : 'Nie udało się pobrać szczegółów szkicu.',
        ),
      )
      .finally(() => this.loading.set(false));
  }
  protected startEditing(d: EditorialDetail): void {
    const v = d.editor.version;
    this.editForm.reset({
      canonicalName: v.canonicalName,
      instruction: v.instruction,
      movementPatterns: v.movementPatterns,
      requiredEquipment: v.requiredEquipment,
      stimulusType: v.stimulusType,
      fatigueProfile: v.fatigueProfile,
      technicalLevel: v.technicalLevel,
      environment: v.environment,
    });
    this.editing.set(true);
  }
  protected save(): void {
    const d = this.detail();
    if (!d || this.editForm.invalid || this.saving()) return;
    this.saving.set(true);
    const value = this.editForm.getRawValue();
    const { canonicalName, ...version } = value;
    void this.api
      .updateEditorialDraft(this.id, {
        canonicalName,
        expectedVersion: d.review.version,
        version: { ...version, mediaReference: d.editor.version.mediaReference },
      })
      .then(() => {
        this.message.set('Korekta została zapisana.');
        this.isError.set(false);
        this.editing.set(false);
        this.load(true);
      })
      .catch((e) =>
        this.failure(
          e,
          'Nie udało się zapisać korekty.',
          e instanceof ExerciseImportApiError && e.status === 409,
        ),
      )
      .finally(() => this.saving.set(false));
  }
  protected areaForm(area: string) {
    let form = this.forms.get(area);
    if (!form) {
      form = this.fb.nonNullable.group({ comment: '' });
      this.forms.set(area, form);
    }
    return form;
  }
  protected submitting(area: string): boolean {
    return this.submittingArea() === area;
  }
  protected review(area: string, decision: string): void {
    const d = this.detail();
    if (!d || this.submittingArea()) return;
    this.submittingArea.set(area);
    const anchor = `review-${area}`;
    void this.api
      .review(this.id, area, decision, this.areaForm(area).getRawValue().comment, d.review.version)
      .then((result) => {
        this.detail.update((current) =>
          current
            ? { ...current, review: result, readyToPublish: current.readyToPublish }
            : current,
        );
        this.message.set('Zapisano decyzję recenzenta.');
        this.isError.set(false);
        this.load(true, anchor);
      })
      .catch((e) => this.failure(e, 'Nie udało się zapisać recenzji.'))
      .finally(() => this.submittingArea.set(null));
  }
  protected publish(): void {
    const d = this.detail();
    if (!d || !d.readyToPublish || this.publishing()) return;
    this.publishing.set(true);
    void this.api
      .publish(this.id, d.review.version)
      .then(() => {
        this.message.set('Ćwiczenie zostało opublikowane.');
        this.isError.set(false);
        this.load(true);
      })
      .catch((e) => this.failure(e, 'Nie udało się opublikować ćwiczenia.'))
      .finally(() => this.publishing.set(false));
  }
  protected status(v: string): string {
    return EXERCISE_VERSION_STATUS_LABELS[v] ?? 'Nieokreślone';
  }
  protected label(v: unknown): string {
    return LABELS[String(v)] ?? 'Nieokreślone';
  }
  protected labels(values: string[] | undefined, empty = 'Nieokreślone'): string {
    return values?.length ? values.map((x) => this.label(x)).join(', ') : empty;
  }
  protected fieldLabel(field: string): string {
    return (
      {
        stimulusType: 'Rodzaj bodźca',
        fatigueProfile: 'Profil zmęczenia',
        technicalLevel: 'Poziom techniczny',
        environment: 'Środowisko',
      } as Record<string, string>
    )[field];
  }
  protected value(value: Record<string, unknown>, key: string): string {
    return String(value[key] ?? '');
  }
  protected reviewAreas(d: EditorialDetail): string[] {
    return d.review.requiredAreas ?? [];
  }
  protected areaState(d: EditorialDetail, area: string): string {
    const review = (d.review.reviews ?? []).find((item: any) => item.area === area) as any;
    return review
      ? `${this.label(review.decision || review.status)}${review.reviewerName ? ` · ${review.reviewerName}` : ''}${review.reviewedAt ? ` · ${new Date(review.reviewedAt).toLocaleDateString('pl-PL')}` : ''}${review.comment ? ` · ${review.comment}` : ''}`
      : 'Oczekuje na decyzję';
  }
  protected safeUri(value: unknown): string | null {
    const uri = String(value || '');
    return /^https:\/\//.test(uri) ? uri : null;
  }
  private failure(e: unknown, fallback: string, reload = false): void {
    this.isError.set(true);
    this.message.set(
      e instanceof ExerciseImportApiError && e.status === 409
        ? 'Dane zmieniły się w międzyczasie. Pobrano aktualną wersję.'
        : e instanceof Error
          ? e.message
          : fallback,
    );
    if (reload) this.load(true);
  }
}
