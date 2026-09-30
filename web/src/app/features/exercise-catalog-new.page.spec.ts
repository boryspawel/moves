import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';
import { describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { ExerciseCatalogNewPage } from './exercise-catalog-new.page';

describe('ExerciseCatalogNewPage', () => {
  it('shows required errors only after interaction and submits an intentionally incomplete draft through the form', async () => {
    const catalogAdmin = { createEditorialExercise: vi.fn().mockResolvedValue({ versionId: 'draft-1' }) };
    await TestBed.configureTestingModule({ imports: [ExerciseCatalogNewPage, NoopAnimationsModule, RouterTestingModule], providers: [{ provide: ApiFacade, useValue: { catalogAdmin } }] }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseCatalogNewPage); fixture.detectChanges();
    const form = (fixture.nativeElement as HTMLElement).querySelector('form')!;
    const name = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[name="name"]')!;
    const instruction = (fixture.nativeElement as HTMLElement).querySelector<HTMLTextAreaElement>('textarea[name="instruction"]')!;
    expect(fixture.nativeElement.textContent).not.toContain('Nazwa jest wymagana.');
    expect(fixture.nativeElement.textContent).not.toContain('Instrukcja jest wymagana.');

    form.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nazwa jest wymagana.');
    expect(fixture.nativeElement.textContent).toContain('Instrukcja jest wymagana.');

    name.value = 'Nowy przysiad'; name.dispatchEvent(new Event('input'));
    instruction.value = 'Kontrolowany ruch'; instruction.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    fixture.detectChanges();
    form.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(catalogAdmin.createEditorialExercise.mock.calls[0][0].catalogCreateRequest.version).toMatchObject({ movementPatterns: new Set(), requiredEquipment: new Set(), stimulusType: undefined, fatigueProfile: undefined, technicalLevel: undefined, environment: undefined });
  });

  it('creates a complete draft and exposes the required profile fields', async () => {
    const catalogAdmin = { createEditorialExercise: vi.fn().mockResolvedValue({ versionId: 'draft-2' }) };
    await TestBed.configureTestingModule({ imports: [ExerciseCatalogNewPage, RouterTestingModule], providers: [{ provide: ApiFacade, useValue: { catalogAdmin } }] }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseCatalogNewPage); fixture.detectChanges();
    const page = fixture.componentInstance; page.name = 'Nowy przysiad'; page.instruction = 'Kontrolowany ruch'; page.stimulus = 'POWER'; page.environment = 'OUTDOOR'; await page.create();
    expect(catalogAdmin.createEditorialExercise).toHaveBeenCalledWith(expect.objectContaining({ catalogCreateRequest: expect.objectContaining({ canonicalName: 'Nowy przysiad' }) }));
    expect(catalogAdmin.createEditorialExercise.mock.calls[0][0].catalogCreateRequest.version).toMatchObject({stimulusType: 'POWER', environment: 'OUTDOOR'});
    expect(fixture.nativeElement.textContent).toContain('Profil zmęczenia');
    expect(fixture.nativeElement.textContent).toContain('Kontrola motoryczna');
  });
});
