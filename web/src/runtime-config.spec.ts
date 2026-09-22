import { describe, expect, it } from 'vitest';
import { loadRuntimeConfig } from './runtime-config';

describe('loadRuntimeConfig', () => {
  it('accepts complete public Keycloak configuration', () => {
    expect(loadRuntimeConfig({
      keycloak: { url: 'https://identity.example', realm: 'moves', clientId: 'moves-web' }
    })).toEqual({
      keycloak: { url: 'https://identity.example', realm: 'moves', clientId: 'moves-web' }
    });
  });

  it('rejects incomplete or unsafe configuration', () => {
    expect(() => loadRuntimeConfig({ keycloak: { url: 'javascript:alert(1)', realm: 'moves', clientId: 'moves-web' } }))
      .toThrow('Invalid Keycloak runtime URL');
    expect(() => loadRuntimeConfig({ keycloak: { url: 'https://identity.example', realm: '', clientId: 'moves-web' } }))
      .toThrow('Incomplete Keycloak runtime configuration');
  });
});
