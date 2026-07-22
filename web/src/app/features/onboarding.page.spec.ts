import { TestBed } from '@angular/core/testing';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { OnboardingPage } from './onboarding.page';

const api = {
  state: vi.fn(), selectProfileType: vi.fn(), participantProfile: vi.fn(), specialistProfile: vi.fn(), availability: vi.fn()
};

async function settle(fixture: ReturnType<typeof TestBed.createComponent>): Promise<void> {
  await fixture.whenStable();
  for (let turn = 0; turn < 4; turn++) await Promise.resolve();
  fixture.detectChanges();
}

describe('OnboardingPage', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    api.state.mockResolvedValue({ stage: 'PROFILE_TYPE_REQUIRED', missingSteps: ['PROFILE_TYPE'] });
    await TestBed.configureTestingModule({ imports: [OnboardingPage], providers: [{ provide: ApiFacade, useValue: { onboarding: api } }] }).compileComponents();
  });

  it('shows only loading content until the initial state resolves', () => {
    api.state.mockReturnValue(new Promise(() => undefined));
    const fixture = TestBed.createComponent(OnboardingPage); fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Ładujemy dane onboardingu');
    expect(page.querySelector('form')).toBeNull();
  });

  it('shows a safe error and retry without forms when initial state loading fails', async () => {
    api.state.mockRejectedValueOnce(new Error('backend body'));
    const fixture = TestBed.createComponent(OnboardingPage); fixture.detectChanges(); await settle(fixture);
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Nie udało się wczytać danych onboardingu');
    expect(page.textContent).not.toContain('backend body');
    expect(page.querySelector('form')).toBeNull();
    (page.querySelector('button') as HTMLButtonElement).click(); await settle(fixture);
    expect(api.state).toHaveBeenCalledTimes(2);
  });

  it('retains the active form and input after a stage action fails', async () => {
    api.state.mockResolvedValue({ stage: 'PROFILE_REQUIRED', profileType: 'PARTICIPANT' });
    api.participantProfile.mockRejectedValueOnce(new Error('bad request'));
    const fixture = TestBed.createComponent(OnboardingPage); fixture.detectChanges(); await settle(fixture);
    const instance = fixture.componentInstance as any;
    instance.profileForm.controls.displayName.setValue('Ada');
    instance.saveProfile(); await settle(fixture);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nie udało się zapisać tego kroku');
    expect(instance.profileForm.controls.displayName.value).toBe('Ada');
    expect(instance.state().stage).toBe('PROFILE_REQUIRED');
  });

  it('blocks legal acknowledgement when approved public document content is absent', async () => {
    api.state.mockResolvedValue({ stage: 'LEGAL_REQUIRED', missingSteps: ['LEGAL_DOCUMENTS'] });
    const fixture = TestBed.createComponent(OnboardingPage); fixture.detectChanges(); await settle(fixture);
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Nie możemy bezpiecznie pokazać dokumentów do akceptacji');
    expect(page.querySelector('button')).toBeNull();
  });

  it('prevents duplicate submission and sends a detected participant time zone when present', async () => {
    let resolve!: (state: object) => void;
    api.state.mockResolvedValue({ stage: 'PROFILE_REQUIRED', profileType: 'PARTICIPANT' });
    api.participantProfile.mockReturnValue(new Promise(release => { resolve = release; }));
    const fixture = TestBed.createComponent(OnboardingPage); fixture.detectChanges(); await settle(fixture);
    const instance = fixture.componentInstance as any;
    instance.profileForm.controls.displayName.setValue('Ada');
    instance.profileForm.controls.timeZoneId.setValue('Europe/Warsaw');
    instance.saveProfile(); instance.saveProfile();
    expect(api.participantProfile).toHaveBeenCalledTimes(1);
    expect(api.participantProfile).toHaveBeenCalledWith({ participantProfileRequest: { displayName: 'Ada', timeZoneId: 'Europe/Warsaw' } });
    resolve({ stage: 'AVAILABILITY_REQUIRED' }); await settle(fixture);
  });

  it('renders the ready completion state', async () => {
    api.state.mockResolvedValue({ stage: 'READY', missingSteps: [] });
    const fixture = TestBed.createComponent(OnboardingPage); fixture.detectChanges(); await settle(fixture);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Onboarding ukończony');
  });
});
