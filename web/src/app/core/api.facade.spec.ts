import { describe, expect, it, vi } from 'vitest';
import { ExerciseCatalogControllerApi } from '../api/generated/src';
import { Configuration } from '../api/generated/src/runtime';
import { generatedAuthorizationMiddleware, normalizeGeneratedApiBasePath } from './api.facade';

describe('normalizeGeneratedApiBasePath', () => {
  it('removes the API prefix already present in generated client paths', () => {
    expect(normalizeGeneratedApiBasePath('/api')).toBe('');
  });

  it('keeps an origin base URL unchanged', () => {
    expect(normalizeGeneratedApiBasePath('http://localhost:8080')).toBe('http://localhost:8080');
  });

  it('removes the API prefix from a full base URL', () => {
    expect(normalizeGeneratedApiBasePath('https://moves.example/api')).toBe('https://moves.example');
  });

  it('propagates the authenticated token to generated catalog requests', async () => {
    const fetchApi = vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [], totalElements: 0 }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    const api = new ExerciseCatalogControllerApi(new Configuration({ basePath: '', fetchApi, middleware: [generatedAuthorizationMiddleware(async () => 'demo-token')] }));
    await api.list();
    expect(new Headers(fetchApi.mock.calls[0][1].headers).get('Authorization')).toBe('Bearer demo-token');
  });
});
