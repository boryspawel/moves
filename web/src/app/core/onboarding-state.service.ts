import { Injectable, inject } from '@angular/core';
import type { State } from '../api/generated/src';
import { ApiFacade } from './api.facade';

/** A short-lived, in-memory view of the backend onboarding state. */
@Injectable({ providedIn: 'root' })
export class OnboardingStateService {
  private readonly api = inject(ApiFacade).onboarding;
  private cached?: State;
  private pending?: Promise<State>;

  get(force = false): Promise<State> {
    if (!force && this.cached) return Promise.resolve(this.cached);
    if (!force && this.pending) return this.pending;
    const request = this.api.state().then(state => {
      this.cached = state;
      return state;
    }).finally(() => {
      if (this.pending === request) this.pending = undefined;
    });
    this.pending = request;
    return request;
  }

  set(state: State): void { this.cached = state; }
  invalidate(): void { this.cached = undefined; this.pending = undefined; }
}
