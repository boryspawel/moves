import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  ProfileTypeRequestProfileTypeEnum, SlotRequestDayOfWeekEnum, SpecialistProfileRequestSpecialistKindEnum,
  type State, type StateProfileTypeEnum
} from '../api/generated/src';
import { ApiFacade } from '../core/api.facade';
import { detectedBrowserTimeZone } from '../core/browser-time-zone';
import { OnboardingAvailabilityComponent, OnboardingBasicProfileComponent, OnboardingCompletionComponent, OnboardingLegalBlockerComponent, OnboardingProfileTypeComponent, OnboardingProgressComponent, type ProfileValue, type ProgressStep } from './onboarding-steps';
import { toPresentationStage, type OnboardingPresentationStage } from './onboarding-presentation';

type LoadState = 'loading' | 'load-error' | 'loaded';
type AvailabilitySlotForm = FormGroup<{ dayOfWeek: FormControl<string>; startTime: FormControl<string>; endTime: FormControl<string>; timeZone: FormControl<string> }>;

@Component({
  selector: 'app-onboarding-page',
  imports: [ReactiveFormsModule, MatButtonModule, OnboardingProgressComponent, OnboardingProfileTypeComponent, OnboardingLegalBlockerComponent, OnboardingBasicProfileComponent, OnboardingAvailabilityComponent, OnboardingCompletionComponent],
  template: `
    <section class="onboarding" aria-labelledby="onboarding-title" [attr.aria-busy]="loadState() === 'loading' || submittingStage() !== null">
      <header class="onboarding-header"><p class="eyebrow">moves</p><h1 id="onboarding-title">Skonfiguruj swój profil</h1><p>Krótka konfiguracja pozwala bezpiecznie dopasować kolejne kroki.</p><app-onboarding-progress [steps]="progressSteps()" /></header>
      <p class="status" role="status" aria-live="polite">{{ message() }}</p>
      @if (loadState() === 'loading') { <section class="onboarding-card loading" aria-busy="true">Ładujemy dane onboardingu…</section> }
      @else if (loadState() === 'load-error') { <section class="onboarding-card" role="alert"><h2 tabindex="-1">Nie udało się wczytać danych onboardingu</h2><p>Spróbuj ponownie.</p><button mat-flat-button type="button" (click)="retry()">Spróbuj ponownie</button></section> }
      @else if (presentationStage() === 'profile-type') { <app-onboarding-profile-type [busy]="isSubmitting('profile-type')" (continue)="selectRole($event)" /> }
      @else if (presentationStage() === 'legal') { <app-onboarding-legal-blocker /> }
      @else if (presentationStage() === 'basic-profile') { <app-onboarding-basic-profile [form]="profileForm" [profileType]="state()?.profileType" [busy]="isSubmitting('basic-profile')" (saved)="saveProfile($event)" /> }
      @else if (presentationStage() === 'availability') { <app-onboarding-availability [form]="availabilityForm" [busy]="isSubmitting('availability')" (add)="addSlot()" (remove)="removeSlot($event)" (saved)="saveAvailability()" /> }
      @else if (presentationStage() === 'complete') { <app-onboarding-completion /> }
      @else { <section class="onboarding-card" role="alert">Nie możemy bezpiecznie wyświetlić bieżącego kroku onboardingu.</section> }
    </section>`,
  styles: [`:host { display:block; min-height:100vh; background:radial-gradient(circle at top, #173a43, #10191d 45rem); } .onboarding { width:min(820px, calc(100% - 2rem)); margin:0 auto; padding:clamp(1rem,4vw,4rem) 0; color:var(--ink); } .onboarding-header { text-align:center; margin-bottom:1.5rem; } h1 { font-size:clamp(2rem,6vw,3.25rem); margin:.25rem 0; } h2 { margin:0; } .eyebrow { color:var(--accent); font-weight:800; letter-spacing:.12em; text-transform:uppercase; margin:0; } .onboarding-card { display:block; max-width:680px; margin:0 auto; padding:clamp(1rem,3vw,2rem); border:1px solid var(--line); border-radius:1rem; background:var(--surface); box-shadow:0 1rem 3rem #0004; } .loading { text-align:center; } .status { min-height:1.5rem; max-width:680px; margin:0 auto 1rem; text-align:center; color:var(--muted); } :host :is(button, input, [role="button"]) { min-height:44px; } :host :focus-visible { outline:3px solid var(--accent); outline-offset:3px; } @media (max-width:360px) { .onboarding { width:calc(100% - 1rem); padding-top:1rem; } } @media (prefers-reduced-motion:reduce) { *, *::before, *::after { animation-duration:.01ms !important; transition-duration:.01ms !important; } }`],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OnboardingPage {
  private readonly api = inject(ApiFacade).onboarding;
  protected readonly state = signal<State | null>(null);
  protected readonly loadState = signal<LoadState>('loading');
  protected readonly message = signal('');
  protected readonly submittingStage = signal<OnboardingPresentationStage | null>(null);
  protected readonly presentationStage = computed(() => this.state() ? toPresentationStage(this.state()!) : undefined);
  protected readonly progressSteps = computed<ProgressStep[]>(() => {
    const stage = this.presentationStage();
    const index = stage === 'profile-type' ? 0 : stage === 'legal' ? 1 : stage === 'basic-profile' ? 2 : stage === 'availability' ? 3 : 4;
    return ['Typ profilu', 'Dokumenty', 'Dane profilu', 'Dostępność'].map((label, i) => ({ label, state: stage === 'legal' && i === 1 ? 'blocked' : index > i ? 'complete' : index === i ? 'current' : 'upcoming' }));
  });
  protected readonly profileForm = new FormGroup({ displayName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(80)] }), specialistKind: new FormControl('TRAINER', { nonNullable: true }), timeZoneId: new FormControl(detectedBrowserTimeZone() ?? '', { nonNullable: true }) });
  protected readonly availabilityForm = new FormGroup({ slots: new FormArray<AvailabilitySlotForm>([this.createSlot()]) });

  constructor() { void this.refresh(); }
  protected isSubmitting(stage: OnboardingPresentationStage): boolean { return this.submittingStage() === stage; }
  protected retry(): void { void this.refresh(); }
  protected selectRole(profileType: ProfileTypeRequestProfileTypeEnum): void { void this.submit('profile-type', () => this.api.selectProfileType({ profileTypeRequest: { profileType } }), 'Typ profilu zapisany.'); }
  protected saveProfile(value: ProfileValue): void {
    void this.submit('basic-profile', () => this.state()?.profileType === 'SPECIALIST'
      ? this.api.specialistProfile({ specialistProfileRequest: { displayName: value.displayName, specialistKind: value.specialistKind as SpecialistProfileRequestSpecialistKindEnum } })
      : this.api.participantProfile({ participantProfileRequest: { displayName: value.displayName, ...(value.timeZoneId ? { timeZoneId: value.timeZoneId } : {}) } }), 'Profil zapisany.');
  }
  protected addSlot(): void { this.availabilityForm.controls.slots.push(this.createSlot()); }
  protected removeSlot(index: number): void { if (this.availabilityForm.controls.slots.length > 1) this.availabilityForm.controls.slots.removeAt(index); }
  protected saveAvailability(): void {
    if (this.availabilityForm.invalid) return;
    const slots = this.availabilityForm.getRawValue().slots.map(slot => ({ ...slot, dayOfWeek: slot.dayOfWeek as SlotRequestDayOfWeekEnum }));
    void this.submit('availability', () => this.api.availability({ availabilityRequest: { slots } }), 'Dostępność zapisana.');
  }
  private createSlot(): AvailabilitySlotForm { return new FormGroup({ dayOfWeek: new FormControl<string>(SlotRequestDayOfWeekEnum.Monday, { nonNullable: true }), startTime: new FormControl('09:00', { nonNullable: true, validators: Validators.required }), endTime: new FormControl('10:00', { nonNullable: true, validators: [Validators.required, control => { const parent = control.parent as AvailabilitySlotForm | null; return parent && control.value <= parent.controls.startTime.value ? { endBeforeStart: true } : null; }] }), timeZone: new FormControl(detectedBrowserTimeZone() ?? '', { nonNullable: true, validators: Validators.required }) }); }
  private async refresh(): Promise<void> { this.loadState.set('loading'); try { this.state.set(await this.api.state()); this.loadState.set('loaded'); this.message.set(''); } catch { this.state.set(null); this.loadState.set('load-error'); this.message.set(''); } }
  private async submit(stage: OnboardingPresentationStage, request: () => Promise<State>, success: string): Promise<void> { if (this.isSubmitting(stage)) return; this.submittingStage.set(stage); this.message.set(''); try { this.state.set(await request()); this.message.set(success); } catch { this.message.set('Nie udało się zapisać tego kroku. Sprawdź dane i spróbuj ponownie.'); } finally { this.submittingStage.set(null); } }
}
