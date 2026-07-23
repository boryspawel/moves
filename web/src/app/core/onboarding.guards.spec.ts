import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { AuthService } from './auth.service';
import { OnboardingStateService } from './onboarding-state.service';
import { rootLandingGuard } from './onboarding.guards';

describe('rootLandingGuard', () => {
  async function resolve(authenticated: boolean, specialist: boolean, stage = 'READY'): Promise<string> {
    await TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { authenticated: () => authenticated, hasRole: vi.fn(() => specialist) } },
        { provide: OnboardingStateService, useValue: { get: vi.fn().mockResolvedValue({ stage }) } }
      ]
    }).compileComponents();

    const result = await TestBed.runInInjectionContext(() => rootLandingGuard({} as never, {} as never));
    return TestBed.inject(Router).serializeUrl(result as ReturnType<Router['createUrlTree']>);
  }

  it('sends a ready specialist to today', async () => {
    expect(await resolve(true, true)).toBe('/specialist/today');
  });

  it('sends an unauthenticated visitor to login', async () => {
    expect(await resolve(false, false)).toBe('/login');
  });

  it('sends a specialist with incomplete onboarding to onboarding', async () => {
    expect(await resolve(true, true, 'PROFILE_REQUIRED')).toBe('/onboarding');
  });

  it('preserves onboarding as the landing page for non-specialists', async () => {
    expect(await resolve(true, false)).toBe('/onboarding');
  });
});
