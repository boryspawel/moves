import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatError, MatFormFieldModule, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import {
  ProfileTypeRequestProfileTypeEnum,
  SlotRequestDayOfWeekEnum,
  type StateProfileTypeEnum,
} from '../api/generated/src';

export type ProgressStep = {
  label: string;
  state: 'complete' | 'current' | 'blocked' | 'upcoming';
};
export type ProfileValue = { displayName: string; specialistKind: string; timeZoneId: string };
export const supportedTimeZones = [
  'Europe/Warsaw',
  'Europe/London',
  'Europe/Berlin',
  'Europe/Paris',
  'America/New_York',
  'America/Chicago',
  'America/Los_Angeles',
  'Asia/Tokyo',
  'Australia/Sydney',
  'UTC',
];

@Component({
  selector: 'app-onboarding-progress',
  standalone: true,
  template: `<ol class="onboarding-progress" aria-label="Postęp konfiguracji">
    @for (step of steps; track step.label) {
      <li
        [class]="neutral ? 'upcoming' : step.state"
        [attr.aria-current]="!neutral && step.state === 'current' ? 'step' : null"
      >
        <span class="marker" aria-hidden="true">{{
          !neutral && step.state === 'complete'
            ? '✓'
            : !neutral && step.state === 'blocked'
              ? '!'
              : ''
        }}</span
        ><span>{{ step.label }}</span>
      </li>
    }
  </ol>`,
  styles: [
    `
      .onboarding-progress {
        display: grid;
        gap: 0;
        list-style: none;
        padding: 0;
        margin: 0;
      }
      .onboarding-progress li {
        display: flex;
        align-items: center;
        gap: 12px;
        min-height: 56px;
        color: var(--onboarding-text-muted);
        font-size: 0.92rem;
      }
      .marker {
        display: grid;
        place-items: center;
        width: 28px;
        height: 28px;
        flex: 0 0 28px;
        border: 1px solid var(--onboarding-border-strong);
        border-radius: 50%;
        background: var(--onboarding-surface);
        font-weight: 700;
      }
      .onboarding-progress li:not(:last-child) .marker {
        position: relative;
      }
      .onboarding-progress li:not(:last-child) .marker:after {
        content: '';
        position: absolute;
        top: 28px;
        width: 1px;
        height: 28px;
        background: var(--onboarding-border);
      }
      li.complete {
        color: var(--onboarding-text-secondary);
      }
      li.complete .marker {
        border-color: var(--onboarding-success);
        background: var(--onboarding-success-soft);
        color: var(--onboarding-success);
      }
      li.current {
        color: var(--onboarding-text);
        font-weight: 700;
      }
      li.current .marker {
        border-color: var(--onboarding-primary);
        background: var(--onboarding-primary-soft);
        color: var(--onboarding-primary);
      }
      li.blocked .marker {
        border-color: var(--onboarding-warning);
        background: var(--onboarding-warning-soft);
        color: var(--onboarding-warning);
      }
      @media (max-width: 839px) {
        .onboarding-progress {
          display: block;
          height: 8px;
          border-radius: 999px;
          background: var(--onboarding-border);
          overflow: hidden;
        }
        .onboarding-progress li {
          display: none;
        }
        .onboarding-progress li.current {
          display: block;
          width: 25%;
          height: 100%;
          background: var(--onboarding-primary);
        }
        .onboarding-progress li.complete {
          display: block;
          float: left;
          width: 25%;
          height: 100%;
          background: var(--onboarding-success);
        }
        .onboarding-progress li .marker,
        .onboarding-progress li span {
          display: none;
        }
        .onboarding-progress li.blocked {
          display: block;
          float: left;
          width: 25%;
          height: 100%;
          background: var(--onboarding-warning);
        }
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingProgressComponent {
  @Input({ required: true }) steps: ProgressStep[] = [];
  @Input() neutral = false;
}

@Component({
  selector: 'app-onboarding-profile-type',
  standalone: true,
  imports: [MatButtonModule, MatCardModule],
  template: `<mat-card class="onboarding-card"
    ><mat-card-header
      ><mat-card-title><h2 tabindex="-1">Wybierz typ profilu</h2></mat-card-title></mat-card-header
    ><mat-card-content
      ><p>Wybór zapisze się dopiero po wybraniu „Kontynuuj”.</p>
      <div class="role-options" role="radiogroup" aria-label="Typ profilu">
        <button
          type="button"
          role="radio"
          class="role-card"
          [class.selected]="selected === types.Participant"
          [attr.aria-checked]="selected === types.Participant"
          (click)="selected = types.Participant"
        >
          <strong>Uczestnik</strong
          ><span>Chcę realizować treningi i korzystać z przygotowanych planów.</span></button
        ><button
          type="button"
          role="radio"
          class="role-card"
          [class.selected]="selected === types.Specialist"
          [attr.aria-checked]="selected === types.Specialist"
          (click)="selected = types.Specialist"
        >
          <strong>Specjalista</strong
          ><span>Chcę przygotowywać plany i pracować z uczestnikami.</span>
        </button>
      </div></mat-card-content
    ><mat-card-actions
      ><button
        mat-flat-button
        type="button"
        [disabled]="!selected || busy"
        (click)="continue.emit(selected!)"
      >
        Kontynuuj
      </button></mat-card-actions
    ></mat-card
  >`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingProfileTypeComponent {
  protected readonly types = ProfileTypeRequestProfileTypeEnum;
  @Input() busy = false;
  @Output() readonly continue = new EventEmitter<ProfileTypeRequestProfileTypeEnum>();
  protected selected?: ProfileTypeRequestProfileTypeEnum;
}

@Component({
  selector: 'app-onboarding-legal-blocker',
  standalone: true,
  imports: [MatCardModule],
  template: `<mat-card class="onboarding-card legal-blocker" role="alert"
    ><mat-card-header
      ><mat-card-title><h2 tabindex="-1">Dokumenty</h2></mat-card-title></mat-card-header
    ><mat-card-content
      ><p>
        Nie możemy bezpiecznie pokazać dokumentów do akceptacji: w aplikacji nie ma zatwierdzonych
        publicznych treści ani adresów dokumentów.
      </p></mat-card-content
    ></mat-card
  >`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingLegalBlockerComponent {}

@Component({
  selector: 'app-onboarding-basic-profile',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatError,
    MatHint,
    MatLabel,
  ],
  template: `<mat-card class="onboarding-card"
    ><mat-card-header
      ><mat-card-title><h2 tabindex="-1">Dane profilu</h2></mat-card-title></mat-card-header
    ><mat-card-content
      ><form class="form-grid" [formGroup]="form" (ngSubmit)="saved.emit(form.getRawValue())">
        <mat-form-field appearance="outline"
          ><mat-label>Nazwa wyświetlana</mat-label
          ><input matInput formControlName="displayName" autocomplete="name" />
          @if (form.controls.displayName.hasError('required')) {
            <mat-error>Podaj nazwę wyświetlaną.</mat-error>
          }
          @if (form.controls.displayName.hasError('maxlength')) {
            <mat-error>Nazwa może mieć maksymalnie 80 znaków.</mat-error>
          }
        </mat-form-field>
        @if (profileType === 'SPECIALIST') {
          <mat-form-field appearance="outline"
            ><mat-label>Rodzaj specjalisty</mat-label
            ><mat-select formControlName="specialistKind"
              ><mat-option value="TRAINER">Trener</mat-option
              ><mat-option value="PHYSIOTHERAPIST">Fizjoterapeuta</mat-option></mat-select
            ></mat-form-field
          >
        }
        @if (profileType === 'PARTICIPANT') {
          <mat-form-field appearance="outline"
            ><mat-label>Strefa czasowa</mat-label
            ><mat-select formControlName="timeZoneId">
              @for (zone of timeZones; track zone) {
                <mat-option [value]="zone">{{ zone }}</mat-option>
              }</mat-select
            ><mat-hint>Wykryto automatycznie</mat-hint></mat-form-field
          >
        }
        <div class="full">
          <button mat-flat-button type="submit" [disabled]="busy">Zapisz profil</button>
        </div>
      </form></mat-card-content
    ></mat-card
  >`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingBasicProfileComponent {
  @Input({ required: true }) form!: FormGroup<{
    displayName: FormControl<string>;
    specialistKind: FormControl<string>;
    timeZoneId: FormControl<string>;
  }>;
  @Input() profileType?: StateProfileTypeEnum;
  @Input() busy = false;
  @Output() readonly saved = new EventEmitter<ProfileValue>();
  protected readonly timeZones = supportedTimeZones;
}

@Component({
  selector: 'app-onboarding-availability',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatError,
    MatLabel,
  ],
  template: `<mat-card class="onboarding-card"
    ><mat-card-header
      ><mat-card-title><h2 tabindex="-1">Typowa dostępność</h2></mat-card-title></mat-card-header
    ><mat-card-content
      ><p>Dodaj wszystkie powtarzalne przedziały, w których jesteś dostępny/a.</p>
      <form [formGroup]="form" (ngSubmit)="saved.emit()">
        <div formArrayName="slots" class="availability-list">
          @for (slot of slots.controls; track $index; let index = $index) {
            <fieldset [formGroupName]="index">
              <legend>Przedział {{ index + 1 }}</legend>
              <mat-form-field appearance="outline"
                ><mat-label>Dzień</mat-label
                ><mat-select formControlName="dayOfWeek">
                  @for (day of days; track day.value) {
                    <mat-option [value]="day.value">{{ day.label }}</mat-option>
                  }
                </mat-select></mat-form-field
              ><mat-form-field appearance="outline"
                ><mat-label>Od</mat-label
                ><input matInput type="time" formControlName="startTime" /></mat-form-field
              ><mat-form-field appearance="outline"
                ><mat-label>Do</mat-label><input matInput type="time" formControlName="endTime" />
                @if (
                  slot.hasError('invalidTimeRange') &&
                  (slot.get('startTime')?.touched || slot.get('endTime')?.touched)
                ) {
                  <mat-error
                    >Godzina zakończenia musi być późniejsza od godziny rozpoczęcia.</mat-error
                  >
                }</mat-form-field
              ><mat-form-field appearance="outline"
                ><mat-label>Strefa czasowa</mat-label
                ><mat-select formControlName="timeZone">
                  @for (zone of timeZones; track zone) {
                    <mat-option [value]="zone">{{ zone }}</mat-option>
                  }
                </mat-select></mat-form-field
              ><button
                mat-button
                type="button"
                (click)="remove.emit(index)"
                [disabled]="slots.length === 1"
              >
                Usuń przedział
              </button>
            </fieldset>
          }
        </div>
        <div class="availability-actions">
          <button mat-stroked-button type="button" (click)="add.emit()">
            Dodaj kolejny przedział</button
          ><button mat-flat-button type="submit" [disabled]="busy">Zapisz dostępność</button>
        </div>
      </form></mat-card-content
    ></mat-card
  >`,
  styles: [
    `
      .availability-list {
        display: grid;
        gap: 16px;
      }
      fieldset {
        display: grid;
        grid-template-columns: repeat(4, minmax(0, 1fr));
        gap: 8px;
        border: 1px solid var(--onboarding-border);
        border-radius: 12px;
        padding: 16px;
      }
      .availability-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        margin-top: 20px;
      }
      @media (max-width: 680px) {
        fieldset {
          grid-template-columns: 1fr 1fr;
        }
      }
      @media (max-width: 480px) {
        fieldset {
          grid-template-columns: 1fr;
        }
        .availability-actions button {
          width: 100%;
        }
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingAvailabilityComponent {
  @Input({ required: true }) form!: FormGroup<{ slots: FormArray }>;
  @Input() busy = false;
  @Output() readonly add = new EventEmitter<void>();
  @Output() readonly remove = new EventEmitter<number>();
  @Output() readonly saved = new EventEmitter<void>();
  protected readonly timeZones = supportedTimeZones;
  protected readonly days = [
    { value: SlotRequestDayOfWeekEnum.Monday, label: 'Poniedziałek' },
    { value: SlotRequestDayOfWeekEnum.Tuesday, label: 'Wtorek' },
    { value: SlotRequestDayOfWeekEnum.Wednesday, label: 'Środa' },
    { value: SlotRequestDayOfWeekEnum.Thursday, label: 'Czwartek' },
    { value: SlotRequestDayOfWeekEnum.Friday, label: 'Piątek' },
    { value: SlotRequestDayOfWeekEnum.Saturday, label: 'Sobota' },
    { value: SlotRequestDayOfWeekEnum.Sunday, label: 'Niedziela' },
  ];
  get slots(): FormArray {
    return this.form.controls.slots;
  }
}

@Component({
  selector: 'app-onboarding-completion',
  standalone: true,
  imports: [MatButtonModule, MatCardModule, RouterLink],
  template: `<mat-card class="onboarding-card completion"
    ><mat-card-header
      ><mat-card-title><h2 tabindex="-1">Konto gotowe</h2></mat-card-title></mat-card-header
    ><mat-card-content><p>Twój profil jest gotowy do użycia.</p></mat-card-content
    ><mat-card-actions
      ><a mat-flat-button [routerLink]="auth.hasRole('SPECIALIST') ? '/specialist/today' : '/catalog'">{{ auth.hasRole('SPECIALIST') ? 'Przejdź do dzisiaj' : 'Przejdź do katalogu' }}</a></mat-card-actions
    ></mat-card
  >`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingCompletionComponent { protected readonly auth = inject(AuthService); }
