import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { describe, expect, it, vi } from 'vitest';
import { ExerciseImportApi } from '../core/exercise-import.api';
import { ExerciseReviewDetailPage } from './exercise-review-detail.page';

const detail = (overrides: Record<string, unknown> = {}) => ({
  editor: {
    version: {
      exerciseId: 'e',
      exerciseVersionId: 'version',
      canonicalName: 'Przysiad',
      versionNumber: 1,
      status: 'DRAFT',
      instruction: 'Stań stabilnie',
      movementPatterns: ['SQUAT'],
      stimulusType: 'STRENGTH',
      fatigueProfile: 'LOW',
      technicalLevel: 'BEGINNER',
      environment: 'GYM',
      requiredEquipment: [],
    },
    loadCharacteristics: [],
    evidence: [],
    contributions: [],
  },
  anatomyContributions: [
    {
      code: 'MUSCLE:GLUTEUS_MAXIMUS',
      displayName: 'Mięsień pośladkowy wielki',
      structureType: 'MUSCLE',
      role: 'PRIMARY',
      loadChannel: 'DYN_EXU',
      contributionBand: 'MODERATE',
      confidenceClass: 'HIGH',
      evidenceGrade: 'A',
    },
  ],
  importProblems: [],
  review: {
    version: 1,
    reviews: [],
    unmetRequirements: ['REVIEW_CONTENT_REQUIRED'],
    requiredAreas: ['CONTENT'],
  },
  readyToPublish: false,
  actionNeeded: true,
  ...overrides,
});

async function render(api: Record<string, unknown>) {
  await TestBed.configureTestingModule({
    imports: [ExerciseReviewDetailPage, RouterTestingModule],
    providers: [
      { provide: ExerciseImportApi, useValue: api },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'version' } } } },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(ExerciseReviewDetailPage);
  fixture.detectChanges();
  await new Promise((resolve) => setTimeout(resolve));
  fixture.detectChanges();
  return fixture;
}

describe('ExerciseReviewDetailPage', () => {
  it('uses only top-level readiness for publishing and renders mapped anatomy without identifiers', async () => {
    const api = {
      editorialDetail: vi.fn().mockResolvedValue(detail({ readyToPublish: true })),
      review: vi.fn(),
      publish: vi.fn(),
      updateEditorialDraft: vi.fn(),
    };
    const fixture = await render(api);
    expect(fixture.nativeElement.querySelector('button[mat-flat-button]')?.textContent).toContain(
      'Opublikuj',
    );
    expect(fixture.nativeElement.textContent).toContain('Mięsień pośladkowy wielki');
    expect(fixture.nativeElement.textContent).not.toContain('MUSCLE:GLUTEUS_MAXIMUS');
  });

  it('keeps a per-area decision form disabled while submitting and retains it after an error', async () => {
    let reject!: (error: Error) => void;
    const api = {
      editorialDetail: vi.fn().mockResolvedValue(detail()),
      review: vi.fn().mockReturnValue(new Promise((_, fail) => (reject = fail))),
      publish: vi.fn(),
      updateEditorialDraft: vi.fn(),
    };
    const fixture = await render(api);
    const form = fixture.nativeElement.querySelector('.review-area form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.review-area button')?.disabled).toBe(true);
    reject(new Error('Błąd zapisu'));
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Błąd zapisu');
    expect(fixture.nativeElement.querySelector('.review-area form')).toBeTruthy();
  });

  it('restores scroll after a successful per-area decision refresh', async () => {
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    const api = {
      editorialDetail: vi.fn().mockResolvedValue(detail()),
      review: vi.fn().mockResolvedValue({ ...detail().review, version: 2 }),
      publish: vi.fn(),
      updateEditorialDraft: vi.fn(),
    };
    const fixture = await render(api);
    const scrollY = vi.spyOn(window, 'scrollY', 'get').mockReturnValue(240);
    const frameCallbacks: FrameRequestCallback[] = [];
    const requestAnimationFrame = vi.fn((callback: FrameRequestCallback) => {
      frameCallbacks.push(callback);
      return 1;
    });
    vi.stubGlobal('requestAnimationFrame', requestAnimationFrame);
    try {
      (fixture.nativeElement.querySelector('.review-area form') as HTMLFormElement).dispatchEvent(
        new Event('submit'),
      );
      await new Promise((resolve) => setTimeout(resolve));
      fixture.detectChanges();
      expect(api.review).toHaveBeenCalledWith('version', 'CONTENT', 'APPROVED', '', 1);
      expect(requestAnimationFrame).toHaveBeenCalled();
      expect(scrollTo).not.toHaveBeenCalled();
      frameCallbacks.forEach((callback) => callback(0));
      expect(scrollTo).toHaveBeenCalledOnce();
      expect(scrollTo).toHaveBeenCalledWith({ top: 240 });
    } finally {
      scrollY.mockRestore();
      scrollTo.mockRestore();
      vi.unstubAllGlobals();
    }
  });

  it('makes published versions read-only and links to the public catalog', async () => {
    const api = {
      editorialDetail: vi.fn().mockResolvedValue(
        detail({
          editor: {
            ...detail().editor,
            version: { ...detail().editor.version, status: 'PUBLISHED' },
          },
        }),
      ),
      review: vi.fn(),
      publish: vi.fn(),
      updateEditorialDraft: vi.fn(),
    };
    const fixture = await render(api);
    expect(fixture.nativeElement.textContent).toContain('wyłącznie do odczytu');
    expect(fixture.nativeElement.querySelector('.review-area form')).toBeNull();
    expect(
      fixture.nativeElement.querySelector('a[mat-flat-button]')?.getAttribute('href'),
    ).toContain('/catalog/version');
  });
});
