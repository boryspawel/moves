import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { ParticipantExerciseSetDetailPage } from './participant-exercise-set-detail.page';
import { ParticipantExerciseSetLibraryPage } from './participant-exercise-set-library.page';

describe('Participant exercise-set library', () => {
  it('renders only participant-library entries as exact-version links', async () => {
    await TestBed.configureTestingModule({ imports: [ParticipantExerciseSetLibraryPage], providers: [provideRouter([]), { provide: ApiFacade, useValue: { participantExerciseSets: { list3: vi.fn().mockResolvedValue([{ versionId: 'version-2', title: 'Mobilność', versionNumber: 2, profile: 'MOBILITY' }]) } } }] }).compileComponents();
    const fixture = TestBed.createComponent(ParticipantExerciseSetLibraryPage); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('a')?.getAttribute('href')).toBe('/my-exercise-sets/version-2');
  });

  it('loads the route version and offers the next-phase planner deep link without specialist instructions', async () => {
    const version1 = vi.fn().mockResolvedValue({ id: 'version-2', title: 'Mobilność', versionNumber: 2, profile: 'MOBILITY', items: [{ id: 'item-1', snapshot: { canonicalName: 'Skłon' }, dose: { type: 'MOBILITY', durationSeconds: 30 }, participantInstruction: 'Oddychaj spokojnie' }] });
    await TestBed.configureTestingModule({ imports: [ParticipantExerciseSetDetailPage], providers: [provideRouter([]), { provide: ApiFacade, useValue: { participantExerciseSets: { version1 } } }, { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ versionId: 'version-2' }) } } }] }).compileComponents();
    const fixture = TestBed.createComponent(ParticipantExerciseSetDetailPage); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    expect(version1).toHaveBeenCalledWith({ versionId: 'version-2' });
    expect(fixture.nativeElement.textContent).toContain('Oddychaj spokojnie');
    expect(fixture.nativeElement.textContent).not.toContain('specialistInstruction');
    expect(fixture.nativeElement.querySelector('a[href^="/my-plans/new"]')?.getAttribute('href')).toContain('exerciseSetVersionId=version-2');
  });
});
