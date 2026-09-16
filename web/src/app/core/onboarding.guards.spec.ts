import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { AuthService } from './auth.service';
import { OnboardingStateService } from './onboarding-state.service';
import { rootLandingGuard } from './onboarding.guards';

describe('rootLandingGuard', () => {
  async function resolve(authenticated: boolean, roles: string[] = [], stage = 'READY'): Promise<string> {
    await TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { authenticated: () => authenticated, hasRole: vi.fn((role: string) => roles.includes(role)) } },
        { provide: OnboardingStateService, useValue: { get: vi.fn().mockResolvedValue({ stage }) } }
      ]
    }).compileComponents();

    const result = await TestBed.runInInjectionContext(() => rootLandingGuard({} as never, {} as never));
    return TestBed.inject(Router).serializeUrl(result as ReturnType<Router['createUrlTree']>);
  }

  it('sends a ready specialist to today', async () => {
    expect(await resolve(true, ['SPECIALIST'])).toBe('/specialist/today');
  });

  it('sends a ready participant to sessions', async () => {
    expect(await resolve(true, ['PARTICIPANT'])).toBe('/sessions');
  });

  it('keeps specialist landing precedence for dual-role accounts', async () => {
    expect(await resolve(true, ['PARTICIPANT', 'SPECIALIST'])).toBe('/specialist/today');
  });

  it('sends an unauthenticated visitor to login', async () => {
    expect(await resolve(false)).toBe('/login');
  });

  it('sends a specialist with incomplete onboarding to onboarding', async () => {
    expect(await resolve(true, ['SPECIALIST'], 'PROFILE_REQUIRED')).toBe('/onboarding');
  });

  it('denies a ready account without a recognized role', async () => {
    expect(await resolve(true)).toBe('/onboarding');
  });
});
