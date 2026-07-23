import {TestBed} from '@angular/core/testing';
import {RouterTestingModule} from '@angular/router/testing';
import {describe, expect, it, vi} from 'vitest';
import {ExerciseImportApi} from '../core/exercise-import.api';
import {ExerciseReviewPage} from './exercise-review.page';

describe('ExerciseReviewPage', () => {
  it('shows a Polish queue row and opens its editorial detail', async () => {
    const api = {reviewQueue: vi.fn().mockResolvedValue({content:[{exerciseVersionId:'version', canonicalName:'Przysiad', versionStatus:'DRAFT', versionNumber:1, completedReviewAreas:[], missingReviewAreas:['CONTENT'], errorCount:0, blockerCount:0, updatedAt:'2026-01-01T00:00:00Z'}], totalPages:1, totalElements:1})};
    await TestBed.configureTestingModule({imports:[ExerciseReviewPage, RouterTestingModule], providers:[{provide:ExerciseImportApi,useValue:api}]}).compileComponents();
    const fixture=TestBed.createComponent(ExerciseReviewPage); fixture.detectChanges(); await new Promise(resolve=>setTimeout(resolve)); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Przysiad'); expect(fixture.nativeElement.textContent).toContain('Szkic'); expect(fixture.nativeElement.textContent).toContain('Brakuje: treść'); expect(fixture.nativeElement.querySelector('a')?.getAttribute('href')).toContain('/admin/exercise-review/version');
  });

  it('delegates the action-needed filter to the paginated API', async () => {
    const api = {reviewQueue: vi.fn().mockResolvedValue({content:[], totalPages:0, totalElements:0})};
    await TestBed.configureTestingModule({imports:[ExerciseReviewPage, RouterTestingModule], providers:[{provide:ExerciseImportApi,useValue:api}]}).compileComponents();
    const fixture=TestBed.createComponent(ExerciseReviewPage); fixture.detectChanges(); await new Promise(resolve=>setTimeout(resolve));
    const page = fixture.componentInstance as unknown as {filters: {set(value: Record<string,string>): void}; load(): void};
    page.filters.set({action:'true'}); page.load(); await new Promise(resolve=>setTimeout(resolve));
    expect(api.reviewQueue).toHaveBeenLastCalledWith(expect.objectContaining({actionNeeded:'true', page:0, size:25}));
  });
});
