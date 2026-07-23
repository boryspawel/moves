import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';
import { rootLandingGuard } from './onboarding.guards';
import { OnboardingStateService } from './onboarding-state.service';

const keycloakClient = vi.hoisted(() => ({
  init: vi.fn(),
  tokenParsed: undefined as unknown
}));

vi.mock('keycloak-js', () => ({
  default: function KeycloakMock() {
    return keycloakClient;
  }
}));

describe('AuthService', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    keycloakClient.init.mockReset();
    keycloakClient.tokenParsed = undefined;
  });

  it('recognizes a role assigned only to the configured Keycloak client', () => {
    keycloakClient.tokenParsed = {
      resource_access: {
        [environment.keycloak.clientId]: { roles: ['SPECIALIST'] }
      }
    };

    const auth = TestBed.inject(AuthService);

    expect(auth.hasRole('SPECIALIST')).toBe(true);
  });

  it('does not recognize roles when the token has no role claims', () => {
    keycloakClient.tokenParsed = {};

    const auth = TestBed.inject(AuthService);

    expect(auth.hasRole('SPECIALIST')).toBe(false);
  });

  it('sends a ready specialist with only a client role to today', async () => {
    keycloakClient.tokenParsed = {
      resource_access: {
        [environment.keycloak.clientId]: { roles: ['SPECIALIST'] }
      }
    };
    keycloakClient.init.mockResolvedValue(true);
    await TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: OnboardingStateService, useValue: { get: vi.fn().mockResolvedValue({ stage: 'READY' }) } }
      ]
    }).compileComponents();

    await TestBed.inject(AuthService).initialize();
    const result = await TestBed.runInInjectionContext(() => rootLandingGuard({} as never, {} as never));

    expect(TestBed.inject(Router).serializeUrl(result as ReturnType<Router['createUrlTree']>)).toBe('/specialist/today');
  });
});
