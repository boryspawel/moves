import {TestBed} from '@angular/core/testing';
import {RouterTestingModule} from '@angular/router/testing';
import {describe, expect, it, vi} from 'vitest';
import {ExerciseImportApi} from '../core/exercise-import.api';
import {ExerciseReviewPage} from './exercise-review.page';

describe('ExerciseReviewPage', () => {
  it('presents review requirements with Polish labels', async () => {
    const api = {
      reviewQueue: vi.fn().mockResolvedValue([{
        exerciseVersionId: 'version',
        exerciseName: 'Przysiad',
        status: 'DRAFT',
        unmetRequirements: ['REVIEW_CONTENT_REQUIRED', 'TWO_INDEPENDENT_REVIEWERS_REQUIRED']
      }])
    };
    await TestBed.configureTestingModule({
      imports: [ExerciseReviewPage, RouterTestingModule],
      providers: [{provide: ExerciseImportApi, useValue: api}]
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseReviewPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Treść');
    expect(fixture.nativeElement.textContent).toContain('Dwóch niezależnych recenzentów');
    expect(fixture.nativeElement.querySelector('a')?.getAttribute('href')).toContain('/admin/exercise-review/version');
    expect(fixture.nativeElement.textContent).not.toContain('/catalog');
  });
});
