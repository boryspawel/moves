import {TestBed} from '@angular/core/testing';
import {ActivatedRoute} from '@angular/router';
import {RouterTestingModule} from '@angular/router/testing';
import {describe, expect, it, vi} from 'vitest';
import {ExerciseImportApi} from '../core/exercise-import.api';
import {ExerciseImportAttentionPage} from './exercise-import-attention.page';

describe('ExerciseImportAttentionPage', () => {
  it('uses Polish editorial actions for actual match candidates', async () => {
    const api = {
      records: vi.fn().mockImplementation((_id: string, status: string) => Promise.resolve({
        content: status === 'MATCH_CANDIDATES' ? [{
          id: 'record',
          rowNumber: 4,
          sourceRecordKey: 'new-key',
          status
        }] : []
      })),
      record: vi.fn().mockResolvedValue({
        id: 'record',
        status: 'MATCH_CANDIDATES',
        issues: [],
        matchCandidates: [{
          id: 'candidate',
          exerciseId: 'exercise',
          exerciseName: 'Przysiad',
          reasons: ['podobna nazwa']
        }]
      })
    };
    await TestBed.configureTestingModule({
      imports: [ExerciseImportAttentionPage, RouterTestingModule],
      providers: [{provide: ExerciseImportApi, useValue: api}, {
        provide: ActivatedRoute,
        useValue: {snapshot: {paramMap: {get: () => 'batch'}}}
      }]
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseImportAttentionPage);
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Przysiad');
    expect(fixture.nativeElement.textContent).toContain('To samo ćwiczenie');
    expect(fixture.nativeElement.textContent).toContain('Nowe ćwiczenie');
    expect(fixture.nativeElement.textContent).toContain('Nie potrafię rozstrzygnąć');
  });

  it('uses the server choice to approve a mapping and reloads an auto-drafted record out of attention', async () => {
    let drafted = false;
    const api = {
      records: vi.fn().mockImplementation((_id: string, status: string) => Promise.resolve({content: status === 'BLOCKED_BY_MAPPING' && !drafted ? [{id: 'record', rowNumber: 2, status}] : []})),
      record: vi.fn().mockImplementation(() => Promise.resolve({id: 'record', status: drafted ? 'DRAFTED' : 'BLOCKED_BY_MAPPING', issues: drafted ? [] : [{severity: 'BLOCKER'}], matchCandidates: [], mappingProposals: drafted ? [] : [{id: 'mapping', dictionaryType: 'EQUIPMENT', rawValue: 'mat', proposedCanonicalValue: 'MAT', canonicalChoices: [{value: 'MAT', displayName: 'Mata'}]}], draftVersionId: drafted ? 'draft-1' : undefined})),
      decideMapping: vi.fn().mockImplementation(() => { drafted = true; return Promise.resolve({}); })
    };
    await TestBed.configureTestingModule({imports: [ExerciseImportAttentionPage, RouterTestingModule], providers: [{provide: ExerciseImportApi, useValue: api}, {provide: ActivatedRoute, useValue: {snapshot: {paramMap: {get: () => 'batch'}}}}]}).compileComponents();
    const fixture = TestBed.createComponent(ExerciseImportAttentionPage); fixture.detectChanges(); await new Promise(resolve => setTimeout(resolve)); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Mata');
    await (fixture.componentInstance as any).decideMapping('record', 'mapping', 'APPROVED'); fixture.detectChanges();
    expect(api.decideMapping).toHaveBeenCalledWith('mapping', 'APPROVED', 'MAT');
    expect(fixture.nativeElement.textContent).toContain('Otwórz w katalogu');
  });

  it('shows the explicit license replacement and reupload guidance', async () => {
    const api = { records: vi.fn().mockImplementation((_id: string, status: string) => Promise.resolve({content: status === 'BLOCKED_BY_LICENSE' ? [{id: 'record', rowNumber: 3, status}] : []})), record: vi.fn().mockResolvedValue({id: 'record', status: 'BLOCKED_BY_LICENSE', issues: [{severity: 'BLOCKER'}], matchCandidates: [], licenseRemediation: {message: 'Źródło jest niezmienne po zgłoszeniu.', action: 'CREATE_REPLACEMENT_SOURCE_AND_REUPLOAD'}}) };
    await TestBed.configureTestingModule({imports: [ExerciseImportAttentionPage, RouterTestingModule], providers: [{provide: ExerciseImportApi, useValue: api}, {provide: ActivatedRoute, useValue: {snapshot: {paramMap: {get: () => 'batch'}}}}]}).compileComponents();
    const fixture = TestBed.createComponent(ExerciseImportAttentionPage); fixture.detectChanges(); await new Promise(resolve => setTimeout(resolve)); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Źródło jest niezmienne po zgłoszeniu.');
    expect(fixture.nativeElement.textContent).toContain('Utwórz zastępcze źródło');
  });
});
