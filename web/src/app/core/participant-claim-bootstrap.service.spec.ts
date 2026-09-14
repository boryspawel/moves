import { describe, expect, it, vi } from 'vitest';
import { participantAccessBaseUrl, ParticipantClaimBootstrapService } from './participant-claim-bootstrap.service';

describe('ParticipantClaimBootstrapService', () => {
  it('uses an explicit API path for development and production bases', () => {
    expect(participantAccessBaseUrl('http://localhost:8080')).toBe('http://localhost:8080/api');
    expect(participantAccessBaseUrl('/api')).toBe('/api');
  });

  it('posts a fragment token with credentials and removes it before the request', async () => {
    const original = window.history.replaceState; const fetchMock = vi.fn().mockResolvedValue(new Response());
    vi.stubGlobal('fetch', fetchMock); window.history.replaceState = vi.fn();
    Object.defineProperty(window, 'location', { configurable: true, value: new URL('http://localhost/participant/claim#token=ephemeral') });
    await new ParticipantClaimBootstrapService().consumeInvitationFragment();
    expect(window.history.replaceState).toHaveBeenCalledBefore(fetchMock);
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ credentials: 'include', method: 'POST' });
    expect(JSON.parse(fetchMock.mock.calls[0][1].body).token).toBe('ephemeral');
    window.history.replaceState = original;
  });
});
