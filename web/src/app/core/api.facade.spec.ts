import { describe, expect, it, vi } from 'vitest';
import { AnatomyReferenceControllerApi, ExerciseCatalogControllerApi } from '../api/generated/src';
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
    await api.list3();
    expect(new Headers(fetchApi.mock.calls[0][1].headers).get('Authorization')).toBe('Bearer demo-token');
  });

  it('uses the generated visual-region operation', async () => {
    const fetchApi = vi.fn().mockResolvedValue(new Response(JSON.stringify([{ id: 'region', code: 'KNEE_FRONT', layerName: 'MUSCLES', viewName: 'FRONT' }]), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    const api = new AnatomyReferenceControllerApi(new Configuration({ basePath: '', fetchApi }));
    await expect(api.activeVisualRegions()).resolves.toEqual([{ id: 'region', code: 'KNEE_FRONT', layerName: 'MUSCLES', viewName: 'FRONT' }]);
    expect(fetchApi.mock.calls[0][0]).toContain('/api/v1/anatomy/visual-regions');
  });
});
