import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
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
