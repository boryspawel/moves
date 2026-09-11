import { TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { PlanPage } from './plan.page';
import { SpecialistAlertsPage } from './specialist-alerts.page';

const api = {
  worklist: { listWorklist: vi.fn().mockResolvedValue([{ id: 'item-id', category: 'PARTICIPANT_ISSUE', priority: 'HIGH', minimalData: 'question', issueText: 'Pomoc' }]), actOnWorklist: vi.fn(), replyToIssue: vi.fn() },
  planningV2: {}, planWorkflow: {}
};

describe('specialist V2 screens', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('keeps the plan bookmark read-only without manual authoring controls', async () => {
    await TestBed.configureTestingModule({ imports: [PlanPage, RouterTestingModule] }).compileComponents();
    const fixture = TestBed.createComponent(PlanPage); fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Plan treningowy');
    expect(root.textContent).toContain('historię');
    expect(root.textContent).toContain('Przejdź do zestawów ćwiczeń');
    expect(root.querySelectorAll('input, mat-checkbox, form')).toHaveLength(0);
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
