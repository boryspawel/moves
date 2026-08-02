import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

describe('layered anatomy assets', () => {
  for (const view of ['front', 'back'] as const) {
    it(`${view} is a complete neutral base with a semantic exposure overlay`, async () => {
      const source = await readFile(path.join(process.cwd(), `src/assets/anatomy/anatomy-body-${view}-v1.svg`), 'utf8');
      const expectedView = view.toUpperCase();
      expect(source).toContain(`viewBox=`);
      expect(source).toContain(`data-anatomy-view="${expectedView}"`);
      expect(source).toContain(`data-layer="base-silhouette" data-view="${expectedView}"`);
      expect(source).toContain(`data-layer="exposure-overlay" data-view="${expectedView}"`);
      expect(source).toContain('data-anatomy-geometry="base"');
      expect(source).toContain('data-anatomy-geometry="exposure"');
      const overlay = source.match(/<g id="exposure-overlay"[\s\S]*?<\/g>\s*<\/svg>/)?.[0] ?? '';
      expect(overlay).toMatch(/data-laterality="(LEFT|RIGHT|CENTRAL)"/);
      expect(overlay).not.toMatch(/data-laterality="BILATERAL"/);
      expect(source).not.toMatch(/#(?:f0a080|f4a[0-9a-f]{3}|e9967a)/i);
    });
  }
});
