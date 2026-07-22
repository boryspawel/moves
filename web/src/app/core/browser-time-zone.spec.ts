import { afterEach, describe, expect, it, vi } from 'vitest';
import { detectedBrowserTimeZone } from './browser-time-zone';

describe('detectedBrowserTimeZone', () => {
  afterEach(() => vi.restoreAllMocks());

  it('returns the browser-provided IANA time zone', () => {
    vi.spyOn(Intl, 'DateTimeFormat').mockReturnValue({ resolvedOptions: () => ({ timeZone: 'Europe/Warsaw' }) } as Intl.DateTimeFormat);
    expect(detectedBrowserTimeZone()).toBe('Europe/Warsaw');
  });

  it('returns undefined when the browser does not provide a time zone', () => {
    vi.spyOn(Intl, 'DateTimeFormat').mockReturnValue({ resolvedOptions: () => ({}) } as Intl.DateTimeFormat);
    expect(detectedBrowserTimeZone()).toBeUndefined();
  });
});
