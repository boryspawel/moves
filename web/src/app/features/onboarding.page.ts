import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  ProfileTypeRequestProfileTypeEnum,
  SlotRequestDayOfWeekEnum,
  SpecialistProfileRequestSpecialistKindEnum,
  type State
} from '../api/generated/src';
import { ApiFacade } from '../core/api.facade';
import { detectedBrowserTimeZone } from '../core/browser-time-zone';
import { toPresentationStage, type OnboardingPresentationStage } from './onboarding-presentation';

type LoadState = 'loading' | 'load-error' | 'loaded';

@Component({
  selector: 'app-onboarding-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <section class="panel" aria-labelledby="onboarding-title">
      <h1 id="onboarding-title">Profil i onboarding</h1>
      <div class="status" aria-live="polite">{{ message() }}</div>
    </section>

    @if (loadState() === 'loading') {
      <section class="panel" aria-busy="true">Ładujemy dane onboardingu…</section>
    } @else if (loadState() === 'load-error') {
      <section class="panel" role="alert">
        <p>Nie udało się wczytać danych onboardingu. Spróbuj ponownie.</p>
        <button mat-flat-button type="button" (click)="retry()">Spróbuj ponownie</button>
      </section>
    } @else if (presentationStage() === 'profile-type') {
      <mat-card class="panel"><mat-card-title>Wybierz typ profilu</mat-card-title><mat-card-actions>
        <button mat-flat-button type="button" [disabled]="isSubmitting('profile-type')" (click)="selectRole(ProfileType.Participant)">Uczestnik</button>
        <button mat-stroked-button type="button" [disabled]="isSubmitting('profile-type')" (click)="selectRole(ProfileType.Specialist)">Specjalista</button>
      </mat-card-actions></mat-card>
    } @else if (presentationStage() === 'legal') {
      <mat-card class="panel"><mat-card-title>Dokumenty</mat-card-title><mat-card-content>
        <p role="alert">Nie możemy bezpiecznie pokazać dokumentów do akceptacji: w aplikacji nie ma zatwierdzonych publicznych treści ani adresów dokumentów.</p>
      </mat-card-content></mat-card>
    } @else if (presentationStage() === 'basic-profile') {
      <mat-card class="panel"><mat-card-title>Dane profilu</mat-card-title><mat-card-content>
        <form class="form-grid" [formGroup]="profileForm" (ngSubmit)="saveProfile()">
          <mat-form-field><mat-label>Nazwa wyświetlana</mat-label><input matInput formControlName="displayName" autocomplete="name"></mat-form-field>
          @if (state()?.profileType === 'SPECIALIST') {
            <mat-form-field><mat-label>Specjalizacja</mat-label><mat-select formControlName="specialistKind"><mat-option value="TRAINER">Trener</mat-option><mat-option value="PHYSIOTHERAPIST">Fizjoterapeuta</mat-option></mat-select></mat-form-field>
          }
          @if (state()?.profileType === 'PARTICIPANT') {
            <mat-form-field><mat-label>Strefa czasowa</mat-label><input matInput formControlName="timeZoneId"></mat-form-field>
          }
          <div class="full"><button mat-flat-button type="submit" [disabled]="profileForm.invalid || isSubmitting('basic-profile')">Zapisz profil</button></div>
        </form>
      </mat-card-content></mat-card>
    } @else if (presentationStage() === 'availability') {
      <mat-card class="panel"><mat-card-title>Dostępność cykliczna</mat-card-title><mat-card-content>
        <form class="form-grid" [formGroup]="availabilityForm" (ngSubmit)="saveAvailability()">
          <mat-form-field><mat-label>Dzień</mat-label><mat-select formControlName="dayOfWeek"><mat-option value="MONDAY">Poniedziałek</mat-option><mat-option value="TUESDAY">Wtorek</mat-option></mat-select></mat-form-field>
          <mat-form-field><mat-label>Od</mat-label><input matInput type="time" formControlName="startTime"></mat-form-field>
          <mat-form-field><mat-label>Do</mat-label><input matInput type="time" formControlName="endTime"></mat-form-field>
          <mat-form-field><mat-label>Strefa czasowa</mat-label><input matInput formControlName="timeZone"></mat-form-field>
          <div class="full"><button mat-flat-button type="submit" [disabled]="availabilityForm.invalid || isSubmitting('availability')">Zapisz dostępność</button></div>
        </form>
      </mat-card-content></mat-card>
    } @else if (presentationStage() === 'complete') {
      <section class="panel"><h2>Onboarding ukończony</h2><p>Twój profil jest gotowy do użycia.</p></section>
    } @else {
      <section class="panel" role="alert">Nie możemy bezpiecznie wyświetlić bieżącego kroku onboardingu.</section>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OnboardingPage {
  private readonly api = inject(ApiFacade).onboarding;
  protected readonly state = signal<State | null>(null);
  protected readonly loadState = signal<LoadState>('loading');
  protected readonly message = signal('');
  protected readonly actionError = signal<OnboardingPresentationStage | null>(null);
  protected readonly submittingStage = signal<OnboardingPresentationStage | null>(null);
  protected readonly presentationStage = computed(() => {
    const state = this.state();
    return state ? toPresentationStage(state) : undefined;
  });
  protected readonly ProfileType = ProfileTypeRequestProfileTypeEnum;
  protected readonly profileForm = new FormGroup({
    displayName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(80)] }),
    specialistKind: new FormControl('TRAINER', { nonNullable: true }),
    timeZoneId: new FormControl(detectedBrowserTimeZone() ?? '', { nonNullable: true })
  });
  protected readonly availabilityForm = new FormGroup({
    dayOfWeek: new FormControl('MONDAY', { nonNullable: true }),
    startTime: new FormControl('09:00', { nonNullable: true, validators: Validators.required }),
    endTime: new FormControl('10:00', { nonNullable: true, validators: Validators.required }),
    timeZone: new FormControl(detectedBrowserTimeZone() ?? '', { nonNullable: true, validators: Validators.required })
  });

  constructor() { void this.refresh(); }

  protected isSubmitting(stage: OnboardingPresentationStage): boolean { return this.submittingStage() === stage; }

  protected retry(): void { void this.refresh(); }

  protected selectRole(profileType: typeof ProfileTypeRequestProfileTypeEnum[keyof typeof ProfileTypeRequestProfileTypeEnum]): void {
    void this.submit('profile-type', () => this.api.selectProfileType({ profileTypeRequest: { profileType } }), 'Typ profilu zapisany.');
  }

  protected saveProfile(): void {
    const { displayName, specialistKind, timeZoneId } = this.profileForm.getRawValue();
    void this.submit('basic-profile', () => this.state()?.profileType === 'SPECIALIST'
      ? this.api.specialistProfile({ specialistProfileRequest: { displayName, specialistKind: specialistKind as SpecialistProfileRequestSpecialistKindEnum } })
      : this.api.participantProfile({ participantProfileRequest: { displayName, ...(timeZoneId ? { timeZoneId } : {}) } }), 'Profil zapisany.');
  }

  protected saveAvailability(): void {
    const value = this.availabilityForm.getRawValue();
    void this.submit('availability', () => this.api.availability({ availabilityRequest: { slots: [{ ...value, dayOfWeek: value.dayOfWeek as SlotRequestDayOfWeekEnum }] } }), 'Dostępność zapisana.');
  }

  private async refresh(): Promise<void> {
    this.loadState.set('loading');
    try {
      this.state.set(await this.api.state());
      this.loadState.set('loaded');
      this.message.set('');
    } catch {
      this.state.set(null);
      this.loadState.set('load-error');
      this.message.set('');
    }
  }

  private async submit(stage: OnboardingPresentationStage, request: () => Promise<State>, success: string): Promise<void> {
    if (this.isSubmitting(stage)) return;
    this.submittingStage.set(stage);
    this.actionError.set(null);
    try {
      this.state.set(await request());
      this.message.set(success);
    } catch {
      this.actionError.set(stage);
      this.message.set('Nie udało się zapisać tego kroku. Sprawdź dane i spróbuj ponownie.');
    } finally {
      this.submittingStage.set(null);
    }
  }
}
