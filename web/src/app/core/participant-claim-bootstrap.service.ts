import { Injectable, signal } from '@angular/core';
import { environment } from '../../environments/environment';

export function participantAccessBaseUrl(apiBaseUrl: string): string {
  return apiBaseUrl.endsWith('/api') ? apiBaseUrl : `${apiBaseUrl.replace(/\/$/, '')}/api`;
}

/** Consumes an emailed fragment before Keycloak can process any URL state. */
@Injectable({ providedIn: 'root' })
export class ParticipantClaimBootstrapService {
  readonly failure = signal('');
  async consumeInvitationFragment(): Promise<void> {
    const hash = window.location.hash;
    const parameters = new URLSearchParams(hash.startsWith('#') ? hash.slice(1) : hash);
    const token = parameters.get('token');
    if (!token || window.location.pathname !== '/participant/claim') return;
    window.history.replaceState(window.history.state, '', `${window.location.pathname}${window.location.search}`);
    try {
      const response = await fetch(`${participantAccessBaseUrl(environment.apiBaseUrl)}/v1/participant-access/context`, {
        method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token })
      });
      if (!response.ok) this.failure.set(String((await response.json().catch(() => ({}))).code ?? 'contextinvalid').toLowerCase());
    } catch {
      this.failure.set('contextinvalid');
    } finally {
      // token is intentionally scoped to this call only.
    }
  }
}
