import { describe, expect, it } from 'vitest';
import { routes } from './app.routes';

describe('application routes', () => {
  it('redirects the legacy plan bookmark to exercise sets', () => {
    expect(routes.find(route => route.path === 'plan')).toMatchObject({
      path: 'plan',
      pathMatch: 'full',
      redirectTo: 'exercise-sets',
    });
  });
});
