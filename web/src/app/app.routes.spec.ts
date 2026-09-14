import { describe, expect, it } from 'vitest';
import { routes } from './app.routes';

describe('application routes', () => {
  it('keeps the legacy plan bookmark as a specialist read-only route', () => {
    expect(routes.find(route => route.path === 'plan')).toMatchObject({
      path: 'plan',
    });
    expect(routes.find(route => route.path === 'plan')?.loadComponent).toBeDefined();
  });
});
