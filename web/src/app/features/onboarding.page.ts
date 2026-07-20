import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
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

@Component({
  selector: 'app-onboarding-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <div class="grid two-column">
      <section class="panel" aria-labelledby="onboarding-title">
        <h1 id="onboarding-title">Profil i onboarding</h1>
        <p class="muted">Aktualny etap: <strong>{{ state()?.stage ?? 'ładowanie' }}</strong></p>
        <p>Brakujące kroki: {{ state()?.missingSteps?.join(', ') || 'brak' }}</p>
        <div class="status" aria-live="polite" [class.error]="failed()">{{ message() }}</div>
      </section>

      @if (!state()?.profileType) {
        <mat-card class="panel">
          <mat-card-title>Wybierz typ profilu</mat-card-title>
          <mat-card-actions>
            <button mat-flat-button type="button" (click)="selectRole(ProfileType.Participant)">Uczestnik</button>
            <button mat-stroked-button type="button" (click)="selectRole(ProfileType.Specialist)">Specjalista</button>
          </mat-card-actions>
        </mat-card>
      }

      <mat-card class="panel">
        <mat-card-title>Dane profilu</mat-card-title>
        <mat-card-content>
          <form class="form-grid" [formGroup]="profileForm" (ngSubmit)="saveProfile()">
            <mat-form-field><mat-label>Nazwa wyświetlana</mat-label><input matInput formControlName="displayName" autocomplete="name"></mat-form-field>
            @if (state()?.profileType === 'SPECIALIST') {
              <mat-form-field><mat-label>Specjalizacja</mat-label><mat-select formControlName="specialistKind">
                <mat-option value="TRAINER">Trener</mat-option><mat-option value="PHYSIOTHERAPIST">Fizjoterapeuta</mat-option>
              </mat-select></mat-form-field>
            }
            <div class="full"><button mat-flat-button type="submit" [disabled]="profileForm.invalid">Zapisz profil</button></div>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card class="panel">
        <mat-card-title>Dokumenty</mat-card-title>
        <mat-card-content><p>Potwierdź aktualne wersje regulaminu i informacji o prywatności.</p></mat-card-content>
        <mat-card-actions><button mat-flat-button type="button" (click)="acceptLegal()">Potwierdzam oba dokumenty</button></mat-card-actions>
      </mat-card>

      <mat-card class="panel">
        <mat-card-title>Dostępność cykliczna</mat-card-title>
        <mat-card-content>
          <form class="form-grid" [formGroup]="availabilityForm" (ngSubmit)="saveAvailability()">
            <mat-form-field><mat-label>Dzień</mat-label><mat-select formControlName="dayOfWeek"><mat-option value="MONDAY">Poniedziałek</mat-option><mat-option value="TUESDAY">Wtorek</mat-option></mat-select></mat-form-field>
            <mat-form-field><mat-label>Od</mat-label><input matInput type="time" formControlName="startTime"></mat-form-field>
            <mat-form-field><mat-label>Do</mat-label><input matInput type="time" formControlName="endTime"></mat-form-field>
            <mat-form-field><mat-label>Strefa IANA</mat-label><input matInput formControlName="timeZone"></mat-form-field>
            <div class="full"><button mat-flat-button type="submit" [disabled]="availabilityForm.invalid">Zapisz dostępność</button></div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OnboardingPage {
  private readonly api = inject(ApiFacade).onboarding;
  protected readonly state = signal<State | null>(null);
  protected readonly message = signal('');
  protected readonly failed = signal(false);
  protected readonly ProfileType = ProfileTypeRequestProfileTypeEnum;
  protected readonly profileForm = new FormGroup({
    displayName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(80)] }),
    specialistKind: new FormControl('TRAINER', { nonNullable: true })
  });
  protected readonly availabilityForm = new FormGroup({
    dayOfWeek: new FormControl('MONDAY', { nonNullable: true }),
    startTime: new FormControl('09:00', { nonNullable: true, validators: Validators.required }),
    endTime: new FormControl('10:00', { nonNullable: true, validators: Validators.required }),
    timeZone: new FormControl('Europe/Warsaw', { nonNullable: true, validators: Validators.required })
  });

  constructor() { void this.refresh(); }

  protected selectRole(profileType: typeof ProfileTypeRequestProfileTypeEnum[keyof typeof ProfileTypeRequestProfileTypeEnum]): void {
    void this.execute(this.api.selectProfileType({ profileTypeRequest: { profileType } }), 'Typ profilu zapisany.');
  }

  protected saveProfile(): void {
    const displayName = this.profileForm.controls.displayName.value;
    const request = this.state()?.profileType === 'SPECIALIST'
      ? this.api.specialistProfile({ specialistProfileRequest: { displayName, specialistKind: this.profileForm.controls.specialistKind.value as SpecialistProfileRequestSpecialistKindEnum } })
      : this.api.participantProfile({ participantProfileRequest: { displayName } });
    void this.execute(request, 'Profil zapisany.');
  }

  protected acceptLegal(): void {
    void this.execute(this.api.legal({ legalRequest: { termsAccepted: true, privacyNoticeAcknowledged: true } }), 'Dokumenty potwierdzone.');
  }

  protected saveAvailability(): void {
    const value = this.availabilityForm.getRawValue();
    void this.execute(this.api.availability({ availabilityRequest: { slots: [{ ...value, dayOfWeek: value.dayOfWeek as SlotRequestDayOfWeekEnum }] } }), 'Dostępność zapisana.');
  }

  private async refresh(): Promise<void> {
    await this.execute(this.api.state(), '');
  }

  private async execute(request: Promise<State>, success: string): Promise<void> {
    this.failed.set(false);
    try { this.state.set(await request); this.message.set(success); }
    catch { this.failed.set(true); this.message.set('Operacja nie powiodła się. Sprawdź dane i spróbuj ponownie.'); }
  }
}
