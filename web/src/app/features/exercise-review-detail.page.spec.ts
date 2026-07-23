import {TestBed} from '@angular/core/testing';
import {ActivatedRoute} from '@angular/router';
import {RouterTestingModule} from '@angular/router/testing';
import {describe, expect, it, vi} from 'vitest';
import {ExerciseImportApi} from '../core/exercise-import.api';
import {ExerciseReviewDetailPage} from './exercise-review-detail.page';

describe('ExerciseReviewDetailPage', () => {
  it('uses the authorized review-status API and exposes no publication action', async () => {
    const api = {
      reviewStatus: vi.fn().mockResolvedValue({
        exerciseVersionId: 'version',
        status: 'DRAFT',
        version: 1,
        reviews: [],
        unmetRequirements: ['REVIEW_CONTENT_REQUIRED']
      })
    };
    await TestBed.configureTestingModule({
      imports: [ExerciseReviewDetailPage, RouterTestingModule],
      providers: [{provide: ExerciseImportApi, useValue: api}, {
        provide: ActivatedRoute,
        useValue: {snapshot: {paramMap: {get: () => 'version'}}}
      }]
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseReviewDetailPage);
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve));
    fixture.detectChanges();
    expect(api.reviewStatus).toHaveBeenCalledWith('version');
    expect(fixture.nativeElement.textContent).toContain('Wymagana recenzja treści');
    expect(fixture.nativeElement.textContent).not.toContain('Publikuj');
  });
});
