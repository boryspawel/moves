import { TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { ActivatedRoute } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { ExerciseCatalogAdminDetailPage } from './exercise-catalog-admin-detail.page';

describe('ExerciseCatalogAdminDetailPage', () => {
  it('uses the current expectedVersion for draft save and direct publication without review actions', async () => {
    const catalogAdmin = {
      getEditorialExerciseEditor: vi.fn().mockResolvedValue({ version: { exerciseId: 'exercise', versionId: 'version', versionNumber: 1, canonicalName: 'Przysiad', instruction: 'Ruch', movementPatterns: new Set(['SQUAT']), stimulusType: 'STRENGTH', fatigueProfile: 'MODERATE', technicalLevel: 'FOUNDATIONAL', environment: 'ANY', requiredEquipment: new Set() }, evidence: [], contributions: [], loadCharacteristics: [] }),
      listEditorialExerciseVersions: vi.fn().mockResolvedValue([]),
      getEditorialExerciseCapabilities: vi.fn().mockResolvedValue({ expectedVersion: 7, availableActions: ['EDIT', 'PUBLISH'], deleteBlockReason: 'Szkic ma trwałe referencje.', readiness: { unmetRequirements: [] } }),
      updateEditorialExerciseContent: vi.fn().mockResolvedValue({}), publishEditorialExercise: vi.fn().mockResolvedValue({}),
      updateEditorialEvidence: vi.fn().mockResolvedValue({}), updateEditorialContribution: vi.fn().mockResolvedValue({}),
      deleteEditorialInitialDraft: vi.fn().mockResolvedValue({})
    };
    await TestBed.configureTestingModule({ imports: [ExerciseCatalogAdminDetailPage, RouterTestingModule], providers: [{ provide: ApiFacade, useValue: { catalogAdmin, anatomyReferenceAdmin: { listPublishedAnatomicalStructures: vi.fn() } } }, { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'version' } } } }] }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseCatalogAdminDetailPage); fixture.detectChanges(); await new Promise(resolve => setTimeout(resolve)); fixture.detectChanges();
    const page = fixture.componentInstance; await page.save((page.editor()!)); await page.publish();
    expect(catalogAdmin.updateEditorialExerciseContent.mock.calls[0][0].draftUpdateCommand.expectedVersion).toBe(7);
    expect(catalogAdmin.publishEditorialExercise).toHaveBeenCalledWith({ versionId: 'version', publishRequest: { expectedVersion: 7 } });
    expect(fixture.nativeElement.textContent).not.toContain('Przekaż do recenzji');
    expect(fixture.nativeElement.textContent).toContain('Szkic ma trwałe referencje.');
  });

  it('edits existing child metadata with PUT and the current expectedVersion', async () => {
    const catalogAdmin = {
      getEditorialExerciseEditor: vi.fn().mockResolvedValue({ version: { exerciseId: 'exercise', versionId: 'version', canonicalName: 'Przysiad', instruction: 'Ruch', movementPatterns: new Set(['SQUAT']), stimulusType: 'STRENGTH', fatigueProfile: 'MODERATE', technicalLevel: 'FOUNDATIONAL', environment: 'ANY', requiredEquipment: new Set() }, evidence: [], contributions: [], loadCharacteristics: [] }), listEditorialExerciseVersions: vi.fn().mockResolvedValue([]), getEditorialExerciseCapabilities: vi.fn().mockResolvedValue({ expectedVersion: 9, availableActions: ['EDIT'] }), updateEditorialEvidence: vi.fn().mockResolvedValue({}), updateEditorialContribution: vi.fn().mockResolvedValue({})
    };
    await TestBed.configureTestingModule({ imports: [ExerciseCatalogAdminDetailPage, RouterTestingModule], providers: [{ provide: ApiFacade, useValue: { catalogAdmin, anatomyReferenceAdmin: {} } }, { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'version' } } } }] }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseCatalogAdminDetailPage); fixture.detectChanges(); await new Promise(resolve => setTimeout(resolve)); fixture.detectChanges();
    const page = fixture.componentInstance; page.editEvidence({ id: 'evidence', citation: 'Źródło' }); await page.saveEvidence(); page.editContribution({ id: 'contribution', anatomicalStructureId: 'structure', role: 'PRIMARY', loadChannel: 'DYN_EXU', coefficientLow: .2, coefficientHigh: .8, evidence: [{id: 'evidence'}] }); await page.saveContribution();
    expect(catalogAdmin.updateEditorialEvidence.mock.calls[0][0].evidenceUpdateCommand.expectedVersion).toBe(9);
    expect(catalogAdmin.updateEditorialContribution.mock.calls[0][0].contributionUpdateCommand.expectedVersion).toBe(9);
    expect(catalogAdmin.updateEditorialContribution.mock.calls[0][0].contributionUpdateCommand.contribution).toMatchObject({coefficientLow: .2, coefficientHigh: .8, evidenceSourceIds: new Set(['evidence'])});
  });

  it('only offers deletion when the server capability is present and confirms the guarded request', async () => {
    const catalogAdmin = { getEditorialExerciseEditor: vi.fn().mockResolvedValue({ version: { exerciseId: 'exercise', versionId: 'version', canonicalName: 'Przysiad', instruction: 'Ruch', movementPatterns: new Set(), requiredEquipment: new Set() }, evidence: [], contributions: [], loadCharacteristics: [] }), listEditorialExerciseVersions: vi.fn().mockResolvedValue([]), getEditorialExerciseCapabilities: vi.fn().mockResolvedValue({ expectedVersion: 4, availableActions: ['DELETE'] }), deleteEditorialInitialDraft: vi.fn().mockResolvedValue({}) };
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true));
    await TestBed.configureTestingModule({ imports: [ExerciseCatalogAdminDetailPage, RouterTestingModule], providers: [{ provide: ApiFacade, useValue: { catalogAdmin, anatomyReferenceAdmin: {} } }, { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'version' } } } }] }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseCatalogAdminDetailPage); fixture.detectChanges(); await new Promise(resolve => setTimeout(resolve)); fixture.detectChanges(); await fixture.componentInstance.deleteDraft();
    expect(fixture.nativeElement.textContent).toContain('Usuń początkowy szkic');
    expect(catalogAdmin.deleteEditorialInitialDraft).toHaveBeenCalledWith({ versionId: 'version', deleteDraftRequest: { expectedVersion: 4 } }); vi.unstubAllGlobals();
  });

  it('saves typed load rows with all four generated fields', async () => {
    const catalogAdmin = { getEditorialExerciseEditor: vi.fn().mockResolvedValue({ version: { exerciseId: 'exercise', canonicalName: 'Przysiad', instruction: 'Ruch', movementPatterns: new Set(['SQUAT']), stimulusType: 'POWER', fatigueProfile: 'MODERATE', technicalLevel: 'FOUNDATIONAL', environment: 'OUTDOOR', requiredEquipment: new Set() }, evidence: [], contributions: [], loadCharacteristics: [] }), listEditorialExerciseVersions: vi.fn().mockResolvedValue([]), getEditorialExerciseCapabilities: vi.fn().mockResolvedValue({ expectedVersion: 3, availableActions: ['EDIT'] }), replaceEditorialLoadCharacteristics: vi.fn().mockResolvedValue({}) };
    await TestBed.configureTestingModule({ imports: [ExerciseCatalogAdminDetailPage, RouterTestingModule], providers: [{ provide: ApiFacade, useValue: { catalogAdmin, anatomyReferenceAdmin: {} } }, { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'version' } } } }] }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseCatalogAdminDetailPage); fixture.detectChanges(); await new Promise(resolve => setTimeout(resolve));
    const page = fixture.componentInstance; page.loads = [{characteristicType: 'IMPACT', movementPlane: 'TRANSVERSE', contractionType: 'MIXED', rangeOfMotion: 'VARIABLE'}]; await page.saveLoads();
    expect(catalogAdmin.replaceEditorialLoadCharacteristics).toHaveBeenCalledWith({versionId: 'version', expectedVersion: 3, loadCharacteristicCommand: [{characteristicType: 'IMPACT', movementPlane: 'TRANSVERSE', contractionType: 'MIXED', rangeOfMotion: 'VARIABLE'}]});
  });

  it('creates a contribution with its required interval and same-version evidence IDs', async () => {
    const catalogAdmin = { getEditorialExerciseEditor: vi.fn().mockResolvedValue({ version: { exerciseId: 'exercise', canonicalName: 'Przysiad', instruction: 'Ruch', movementPatterns: new Set(['SQUAT']), requiredEquipment: new Set() }, evidence: [{id: 'evidence-a', citation: 'Badanie A'}], contributions: [], loadCharacteristics: [] }), listEditorialExerciseVersions: vi.fn().mockResolvedValue([]), getEditorialExerciseCapabilities: vi.fn().mockResolvedValue({ expectedVersion: 5, availableActions: ['EDIT'], readiness: {unmetRequirements: ['PROFILE_SCHEMA_V2_REQUIRED', 'ALLOCATION_BRANCH_CONFLICT']} }), addEditorialContribution: vi.fn().mockResolvedValue({}) };
    await TestBed.configureTestingModule({ imports: [ExerciseCatalogAdminDetailPage, RouterTestingModule], providers: [{ provide: ApiFacade, useValue: { catalogAdmin, anatomyReferenceAdmin: {} } }, { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'version' } } } }] }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseCatalogAdminDetailPage); fixture.detectChanges(); await new Promise(resolve => setTimeout(resolve)); fixture.detectChanges();
    const page = fixture.componentInstance; page.selectStructure({id: 'structure'}); page.coefficientLow = .25; page.coefficientHigh = .75; page.contributionEvidenceIds = ['evidence-a']; await page.saveContribution();
    expect(catalogAdmin.addEditorialContribution.mock.calls[0][0]).toMatchObject({expectedVersion: 5, contributionCommand: {anatomicalStructureId: 'structure', coefficientLow: .25, coefficientHigh: .75, evidenceSourceIds: new Set(['evidence-a'])}});
    expect(fixture.nativeElement.textContent).toContain('zapisz aktualny profil wersji');
    expect(fixture.nativeElement.textContent).toContain('usuń nakładające się gałęzie alokacji');
  });
});
