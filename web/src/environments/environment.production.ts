import { loadRuntimeConfig } from '../runtime-config';

export const environment = {
  production: true,
  apiBaseUrl: '/api',
  keycloak: loadRuntimeConfig(window.__MOVES_RUNTIME_CONFIG__).keycloak
};
