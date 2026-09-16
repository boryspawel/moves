import { TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { ExerciseCatalogNewPage } from './exercise-catalog-new.page';

describe('ExerciseCatalogNewPage', () => {
  it('creates a complete draft and exposes the required profile fields', async () => {
    const catalogAdmin = { createEditorialExercise: vi.fn().mockResolvedValue({ versionId: 'draft-2' }) };
    await TestBed.configureTestingModule({ imports: [ExerciseCatalogNewPage, RouterTestingModule], providers: [{ provide: ApiFacade, useValue: { catalogAdmin } }] }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseCatalogNewPage); fixture.detectChanges();
    const page = fixture.componentInstance; page.name = 'Nowy przysiad'; page.instruction = 'Kontrolowany ruch'; page.stimulus = 'POWER'; page.environment = 'OUTDOOR'; await page.create();
    expect(catalogAdmin.createEditorialExercise).toHaveBeenCalledWith(expect.objectContaining({ catalogCreateRequest: expect.objectContaining({ canonicalName: 'Nowy przysiad' }) }));
    expect(catalogAdmin.createEditorialExercise.mock.calls[0][0].catalogCreateRequest.version).toMatchObject({stimulusType: 'POWER', environment: 'OUTDOOR'});
    expect(fixture.nativeElement.textContent).toContain('Profil zmęczenia');
  });
});
