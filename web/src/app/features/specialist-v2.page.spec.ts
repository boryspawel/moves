import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { PlanPage } from './plan.page';
import { SpecialistAlertsPage } from './specialist-alerts.page';

const api = {
  specialistParticipants: { activeParticipants: vi.fn().mockResolvedValue([{ participantAccountId: 'participant-id', label: 'Uczestnik 1' }]) },
  catalog: { list: vi.fn().mockResolvedValue({ content: [{ versionId: 'exercise-id', canonicalName: 'Przysiad' }] }) },
  worklist: { listWorklist: vi.fn().mockResolvedValue([{ id: 'item-id', category: 'PARTICIPANT_ISSUE', priority: 'HIGH', minimalData: 'question', issueText: 'Pomoc' }]), actOnWorklist: vi.fn(), replyToIssue: vi.fn() },
  planningV2: {}, planWorkflow: {}
};

describe('specialist V2 screens', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('loads participant and exercise choices without rendering technical IDs', async () => {
    await TestBed.configureTestingModule({ imports: [PlanPage], providers: [{ provide: ApiFacade, useValue: api }] }).compileComponents();
    const fixture = TestBed.createComponent(PlanPage); fixture.detectChanges(); await fixture.whenStable(); for (let i = 0; i < 3; i++) await Promise.resolve(); fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    const instance = fixture.componentInstance as any;
    expect(api.specialistParticipants.activeParticipants).toHaveBeenCalled();
    expect(api.catalog.list).toHaveBeenCalled();
    expect(instance.participants()).toEqual([{ participantAccountId: 'participant-id', label: 'Uczestnik 1' }]);
    expect(instance.exercises()).toEqual([{ versionId: 'exercise-id', canonicalName: 'Przysiad' }]);
    expect(text).not.toContain('participant-id'); expect(text).not.toContain('exercise-id');
  });

  it('uses the worklist and filters it locally by priority', async () => {
    await TestBed.configureTestingModule({ imports: [SpecialistAlertsPage], providers: [{ provide: ApiFacade, useValue: api }] }).compileComponents();
    const fixture = TestBed.createComponent(SpecialistAlertsPage); fixture.detectChanges(); await fixture.whenStable(); for (let i = 0; i < 3; i++) await Promise.resolve(); fixture.detectChanges();
    const instance = fixture.componentInstance as any;
    expect(api.worklist.listWorklist).toHaveBeenCalledWith({ actingContext: 'TRAINER', purpose: 'PERFORMANCE_PLANNING' });
    instance.priority.setValue('LOW'); instance.filter();
    expect(instance.visible()).toEqual([]);
  });
});
