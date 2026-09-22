export interface RuntimeConfig {
  keycloak: {
    url: string;
    realm: string;
    clientId: string;
  };
}

declare global {
  interface Window {
    __MOVES_RUNTIME_CONFIG__?: RuntimeConfig;
  }
}

export function loadRuntimeConfig(value: unknown): RuntimeConfig {
  if (!value || typeof value !== 'object') throw new Error('Missing runtime configuration');
  const keycloak = (value as { keycloak?: unknown }).keycloak;
  if (!keycloak || typeof keycloak !== 'object') throw new Error('Missing Keycloak runtime configuration');
  const { url, realm, clientId } = keycloak as Partial<RuntimeConfig['keycloak']>;
  if (!url || !realm || !clientId) throw new Error('Incomplete Keycloak runtime configuration');
  try {
    const parsed = new URL(url);
    if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('Unsupported Keycloak URL protocol');
  } catch {
    throw new Error('Invalid Keycloak runtime URL');
  }
  return { keycloak: { url, realm, clientId } };
}
