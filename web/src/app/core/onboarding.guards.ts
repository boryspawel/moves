import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { OnboardingStateService } from './onboarding-state.service';

/** Redirects authenticated users only when the authoritative backend stage is not READY. */
export const completedOnboardingGuard: CanActivateFn = async () => {
  const router = inject(Router);
  try {
    return (await inject(OnboardingStateService).get()).stage === 'READY'
      ? true
      : router.createUrlTree(['/onboarding']);
  } catch {
    // An unavailable state service must not create a redirect loop; auth and role guards remain in charge.
    return true;
  }
};

/** Resolves the post-login root landing without changing non-specialist onboarding behaviour. */
export const rootLandingGuard: CanActivateFn = async () => {
  const router = inject(Router);
  const auth = inject(AuthService);
  if (!auth.authenticated()) return router.createUrlTree(['/login']);
  if (!auth.hasRole('SPECIALIST')) return router.createUrlTree(['/onboarding']);

  try {
    return (await inject(OnboardingStateService).get()).stage === 'READY'
      ? router.createUrlTree(['/specialist/today'])
      : router.createUrlTree(['/onboarding']);
  } catch {
    return router.createUrlTree(['/onboarding']);
  }
};
