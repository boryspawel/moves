import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import {
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  ProfileTypeRequestProfileTypeEnum,
  SlotRequestDayOfWeekEnum,
  SpecialistProfileRequestSpecialistKindEnum,
  type SpecialistProfileRequest,
  type State,
} from '../api/generated/src';
import { detectedBrowserTimeZone } from '../core/browser-time-zone';
import { ApiFacade } from '../core/api.facade';
import { OnboardingStateService } from '../core/onboarding-state.service';
import {
  OnboardingAvailabilityComponent,
  OnboardingBasicProfileComponent,
  OnboardingCompletionComponent,
  OnboardingLegalAcknowledgementsComponent,
  OnboardingProfileTypeComponent,
  OnboardingProgressComponent,
  type ProfileValue,
  type ProgressStep,
} from './onboarding-steps';
import { toPresentationStage, type OnboardingPresentationStage } from './onboarding-presentation';

type LoadState = 'loading' | 'load-error' | 'loaded';
type AvailabilitySlotForm = FormGroup<{
  dayOfWeek: FormControl<string>;
  startTime: FormControl<string>;
  endTime: FormControl<string>;
  timeZone: FormControl<string>;
}>;
export const timeRangeValidator: ValidatorFn = (control): ValidationErrors | null => {
  const { startTime, endTime } = control.value ?? {};
  return startTime && endTime && endTime <= startTime ? { invalidTimeRange: true } : null;
};

@Component({
  selector: 'app-onboarding-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    OnboardingProgressComponent,
    OnboardingProfileTypeComponent,
    OnboardingLegalAcknowledgementsComponent,
    OnboardingBasicProfileComponent,
    OnboardingAvailabilityComponent,
    OnboardingCompletionComponent,
  ],
  template: ` <section
    class="onboarding"
    aria-labelledby="onboarding-title"
    [attr.aria-busy]="loadState() === 'loading' || submittingStage() !== null"
  >
    <header class="onboarding-header">
      <h1 id="onboarding-title">Skonfiguruj konto</h1>
      <p>Jeszcze kilka informacji i możesz zacząć.</p>
    </header>
    <div class="onboarding-layout">
      <aside class="progress-area">
        <p class="mobile-step">Krok {{ mobileStep() }} z 4</p>
        <app-onboarding-progress [steps]="progressSteps()" [neutral]="loadState() !== 'loaded'" />
      </aside>
      <div class="step-area">
        <p class="status" role="status" aria-live="polite">{{ message() }}</p>
        @if (loadState() === 'loading') {
          <section class="onboarding-card loading" aria-busy="true">
            <span class="skeleton"></span><span class="skeleton short"></span>
          </section>
        } @else if (loadState() === 'load-error') {
          <section class="onboarding-card error-card" role="alert">
            <h2 #errorHeading tabindex="-1">Nie udało się wczytać konfiguracji</h2>
            <p>Sprawdź połączenie i spróbuj ponownie.</p>
            <button mat-flat-button type="button" (click)="retry()">Spróbuj ponownie</button>
          </section>
        } @else if (presentationStage() === 'profile-type') {
          <app-onboarding-profile-type
            [busy]="isSubmitting('profile-type')"
            (continue)="selectRole($event)"
          />
        } @else if (presentationStage() === 'legal') {
          <app-onboarding-legal-acknowledgements
            [busy]="isSubmitting('legal')"
            (acknowledged)="acknowledgeLegal()"
          />
        } @else if (presentationStage() === 'basic-profile') {
          <app-onboarding-basic-profile
            [form]="profileForm"
            [profileType]="state()?.profileType"
            [busy]="isSubmitting('basic-profile')"
            (saved)="saveProfile($event)"
          />
        } @else if (presentationStage() === 'availability') {
          <app-onboarding-availability
            [form]="availabilityForm"
            [busy]="isSubmitting('availability')"
            (add)="addSlot()"
            (remove)="removeSlot($event)"
            (saved)="saveAvailability()"
          />
        } @else if (presentationStage() === 'complete') {
          <app-onboarding-completion />
        } @else {
          <section class="onboarding-card error-card" role="alert">
            <h2 tabindex="-1">Nie możemy wyświetlić tego kroku</h2>
          </section>
        }
      </div>
    </div>
  </section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingPage {
  private readonly stateStore = inject(OnboardingStateService);
  private readonly api = inject(ApiFacade).onboarding;
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly injector = inject(Injector);
  protected readonly state = signal<State | null>(null);
  protected readonly loadState = signal<LoadState>('loading');
  protected readonly message = signal('');
  protected readonly submittingStage = signal<OnboardingPresentationStage | null>(null);
  protected readonly presentationStage = computed(() =>
    this.state() ? toPresentationStage(this.state()!) : undefined,
  );
  protected readonly mobileStep = computed(
    () =>
      ({ 'profile-type': 1, legal: 2, 'basic-profile': 3, availability: 4, complete: 4 })[
        this.presentationStage() ?? 'profile-type'
      ],
  );
  protected readonly progressSteps = computed<ProgressStep[]>(() => {
    const stage = this.presentationStage();
    const indexes: Record<OnboardingPresentationStage, number> = {
      'profile-type': 0,
      legal: 1,
      'basic-profile': 2,
      availability: 3,
      complete: 4,
    };
    const index = stage ? indexes[stage] : -1;
    return ['Typ profilu', 'Dokumenty', 'Dane profilu', 'Dostępność'].map((label, i) => ({
      label,
      state:
        stage === 'legal' && i === 1
          ? 'blocked'
          : stage === 'complete' || index > i
            ? 'complete'
            : index === i
              ? 'current'
              : 'upcoming',
    }));
  });
  protected readonly profileForm = new FormGroup({
    displayName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(80)],
    }),
    specialistKind: new FormControl('TRAINER', { nonNullable: true }),
    timeZoneId: new FormControl(detectedBrowserTimeZone() ?? 'Europe/Warsaw', {
      nonNullable: true,
      validators: Validators.required,
    }),
  });
  protected readonly availabilityForm = new FormGroup({
    slots: new FormArray<AvailabilitySlotForm>([this.createSlot()]),
  });

  constructor() {
    void this.refresh();
  }
  protected isSubmitting(stage: OnboardingPresentationStage): boolean {
    return this.submittingStage() === stage;
  }
  protected retry(): void {
    void this.refresh(true);
  }
  protected selectRole(profileType: ProfileTypeRequestProfileTypeEnum): void {
    void this.submit(
      'profile-type',
      () => this.api.selectProfileType({ profileTypeRequest: { profileType } }),
      'Typ profilu zapisany.',
    );
  }
  protected acknowledgeLegal(): void {
    void this.submit(
      'legal',
      () =>
        this.api.legal({
          legalRequest: { termsAccepted: true, privacyNoticeAcknowledged: true },
        }),
      'Potwierdzenia zapisane.',
    );
  }
  protected saveProfile(value: ProfileValue): void {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      this.focusFirstInvalid();
      return;
    }
    void this.submit(
      'basic-profile',
      () =>
        this.state()?.profileType === 'SPECIALIST'
          ? this.api.specialistProfile({
              specialistProfileRequest: {
                displayName: value.displayName,
                specialistKind: value.specialistKind as SpecialistProfileRequestSpecialistKindEnum,
                timeZoneId: value.timeZoneId,
              } as unknown as SpecialistProfileRequest,
            })
          : this.api.participantProfile({
              participantProfileRequest: {
                displayName: value.displayName,
                timeZoneId: value.timeZoneId,
              },
            }),
      'Profil zapisany.',
    );
  }
  protected addSlot(): void {
    this.availabilityForm.controls.slots.push(this.createSlot());
  }
  protected removeSlot(index: number): void {
    if (this.availabilityForm.controls.slots.length > 1)
      this.availabilityForm.controls.slots.removeAt(index);
  }
  protected saveAvailability(): void {
    if (this.availabilityForm.invalid) {
      this.availabilityForm.markAllAsTouched();
      this.focusFirstInvalid();
      return;
    }
    const slots = this.availabilityForm
      .getRawValue()
      .slots.map((slot) => ({ ...slot, dayOfWeek: slot.dayOfWeek as SlotRequestDayOfWeekEnum }));
    void this.submit(
      'availability',
      () => this.api.availability({ availabilityRequest: { slots } }),
      'Dostępność zapisana.',
    );
  }
  private createSlot(): AvailabilitySlotForm {
    return new FormGroup(
      {
        dayOfWeek: new FormControl<string>(SlotRequestDayOfWeekEnum.Monday, { nonNullable: true }),
        startTime: new FormControl('09:00', { nonNullable: true, validators: Validators.required }),
        endTime: new FormControl('10:00', { nonNullable: true, validators: Validators.required }),
        timeZone: new FormControl(detectedBrowserTimeZone() ?? 'Europe/Warsaw', {
          nonNullable: true,
          validators: Validators.required,
        }),
      },
      { validators: timeRangeValidator },
    );
  }
  private async refresh(force = false): Promise<void> {
    this.loadState.set('loading');
    try {
      this.state.set(await this.stateStore.get(force));
      this.loadState.set('loaded');
      this.message.set('');
    } catch {
      this.state.set(null);
      this.loadState.set('load-error');
      this.message.set('');
      this.focusAfterRender('.error-card h2');
    }
  }
  private async submit(
    stage: OnboardingPresentationStage,
    request: () => Promise<State>,
    success: string,
  ): Promise<void> {
    if (this.isSubmitting(stage)) return;
    this.submittingStage.set(stage);
    this.message.set('');
    try {
      const state = await request();
      this.stateStore.set(state);
      this.state.set(state);
      this.message.set(success);
      this.focusAfterRender('.onboarding-card h2');
    } catch {
      this.message.set('Nie udało się zapisać tego kroku. Sprawdź dane i spróbuj ponownie.');
    } finally {
      this.submittingStage.set(null);
    }
  }
  private focusAfterRender(selector: string): void {
    afterNextRender(
      () => {
        (
          (this.host.nativeElement as HTMLElement).querySelector(selector) as HTMLElement | null
        )?.focus();
      },
      { injector: this.injector },
    );
  }
  private focusFirstInvalid(): void {
    afterNextRender(
      () => {
        (
          (this.host.nativeElement as HTMLElement).querySelector(
            'input.ng-invalid, mat-select.ng-invalid',
          ) as HTMLElement | null
        )?.focus();
      },
      { injector: this.injector },
    );
  }
}
