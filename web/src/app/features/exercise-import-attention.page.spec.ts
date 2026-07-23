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
});
