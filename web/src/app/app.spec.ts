import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { App } from './app';
import shellTemplate from './shell.html?raw';

describe('App', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    });
    TestBed.overrideComponent(App, {
      set: {
        template: shellTemplate,
        templateUrl: undefined,
        styleUrl: undefined,
        styles: [],
      },
    });
    await TestBed.compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the application shell', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('main')).toBeTruthy();
  });

  it('does not expose the legacy new-plan navigation link', () => {
    expect(shellTemplate).not.toContain('Nowy plan');
    expect(shellTemplate).not.toContain('routerLink="/plan"');
  });

  it('exposes exercise sets navigation to specialists', () => {
    expect(shellTemplate).toMatch(/@if \(auth\.hasRole\('SPECIALIST'\)\) \{[\s\S]*routerLink="\/exercise-sets"[\s\S]*>Zestawy<\/a>/);
  });
});
