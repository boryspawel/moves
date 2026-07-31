import '@angular/compiler';
import { ɵresolveComponentResources } from '@angular/core';
import { getTestBed, ɵgetCleanupHook as getCleanupHook } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { afterEach, beforeEach } from 'vitest';

getTestBed().initTestEnvironment(BrowserTestingModule, platformBrowserTesting());

const componentTemplates = import.meta.glob('./**/*.html', {
  eager: true,
  query: '?raw',
  import: 'default',
}) as Record<string, string>;

beforeEach(getCleanupHook(false));
beforeEach(async () => {
  await ɵresolveComponentResources(url => {
    if (url.endsWith('.css') || url.endsWith('.scss')) {
      return Promise.resolve('');
    }

    const template = Object.entries(componentTemplates)
      .find(([path]) => path.endsWith(url.replace(/^\.\//, '/')))?.[1];

    return template === undefined
      ? Promise.reject(new Error(`Nie znaleziono szablonu komponentu: ${url}`))
      : Promise.resolve(template);
  });
});
afterEach(getCleanupHook(true));
