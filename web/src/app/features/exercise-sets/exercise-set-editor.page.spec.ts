import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { convertToParamMap } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../../core/api.facade';
import { ExerciseSetEditorPage } from './exercise-set-editor.page';

describe('ExerciseSetEditorPage ordering', () => {
  it('renders anatomy channels, evidence, patterns and the clinical limitation without mapping diagnostics', async () => {
    const anatomy = vi
      .fn()
      .mockResolvedValue({
        analyzedLockVersion: 4,
        completeness: 'PARTIAL',
        mappingCompleteness: 'PARTIAL',
        visualMappingCompleteness: 'PARTIAL',
        visualMappingVersion: '1',
        visualRegionExposures: [
          {
            visualRegionCode: 'ANATOMY_VISUAL_MAP_V1:FRONT:THIGH',
            view: 'FRONT',
            layer: 'MUSCLE',
            laterality: 'RIGHT',
            channel: 'DYN_EXU',
            rawValue: 2,
            unit: 'j.',
            shareWithinChannel: 50,
            concentrationBand: 'SIGNIFICANT',
            completeness: 'COMPLETE',
            mappingVersion: 1,
            sourceStructures: [],
            breakdowns: [],
          },
        ],
        unmappedStructures: [
          {
            anatomicalStructureId: 'structure-1',
            anatomicalStructureCode: 'KNEE_EXTENSORS',
            anatomicalStructureType: 'MUSCLE_GROUP',
          },
        ],
        policyVersion: 'exercise-set-anatomy-policy-v1',
        channels: [
          {
            loadChannel: 'MUSCULAR',
            structureExposures: [
              {
                anatomicalStructureId: 'knee-extensors',
                coefficientLow: 1,
                coefficientHigh: 2,
                breakdowns: [
                  {
                    contributionId: 'contribution',
                    role: 'PRIMARY',
                    coefficientLow: 1,
                    coefficientHigh: 2,
                    confidenceClass: 'HIGH',
                    evidenceGrade: 'A',
                    evidence: [{ id: 'evidence', citation: 'Źródło' }],
                  },
                ],
              },
            ],
          },
        ],
        movementPatterns: [{ pattern: 'SQUAT', itemIds: ['item'] }],
        findings: [{ code: 'ANATOMY_SNAPSHOT_INCOMPLETE', message: 'Brak snapshotu' }],
        missing: [{ itemId: 'item-2', code: 'PUBLISHED_EXERCISE_SNAPSHOT_UNAVAILABLE' }],
      });
    const activeVisualRegions = vi
      .fn()
      .mockResolvedValue([
        {
          id: 'region-1',
          code: 'KNEE_FRONT',
          displayName: 'Kolano z przodu',
          layerName: 'MUSCLES',
          viewName: 'FRONT',
        },
      ]);
    const version = {
      id: 'version',
      exerciseSetId: 'set',
      status: 'DRAFT' as const,
      lockVersion: 4,
      items: [],
    };
    await TestBed.configureTestingModule({
      imports: [ExerciseSetEditorPage],
      providers: [
        provideRouter([]),
        {
          provide: ApiFacade,
          useValue: {
            exerciseSets: { version: vi.fn().mockResolvedValue(version), anatomy },
            anatomyReference: { activeVisualRegions },
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ exerciseSetId: 'set', versionId: 'version' }),
            },
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseSetEditorPage);
    fixture.detectChanges();
    await fixture.whenStable();
    await fixture.componentInstance.retryAnatomy();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(anatomy).toHaveBeenCalledWith({ setId: 'set', versionId: 'version' }, expect.anything());
    expect(activeVisualRegions).not.toHaveBeenCalled();
    expect(text).toContain('Ekspozycja i wzorce');
    expect(text).toContain('MUSCULAR');
    expect(text).toContain('SQUAT');
    expect(text).toContain('Źródło');
    expect(text).not.toContain('Kompletność mapowania');
    expect(text).not.toContain('Polityka techniczna');
    expect(text).not.toContain('Wersja mapowania:');
    expect(text).toContain(
      'Ekspozycja anatomiczna jest opisem jakościowym i nie stanowi oceny klinicznej ani pomiaru siły.',
    );
  });

  it('summarizes mobility duration when repetitions are absent', async () => {
    await TestBed.configureTestingModule({
      imports: [ExerciseSetEditorPage],
      providers: [
        provideRouter([]),
        {
          provide: ApiFacade,
          useValue: {
            exerciseSets: { version: vi.fn().mockResolvedValue({ status: 'DRAFT', items: [] }) },
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ exerciseSetId: 'set', versionId: 'version' }),
            },
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseSetEditorPage);

    expect(fixture.componentInstance.doseSummary({ type: 'MOBILITY', durationSeconds: 30 })).toBe(
      '30 s',
    );
    expect(fixture.componentInstance.doseSummary({ type: 'MOBILITY', reps: 8 })).toBe('8 powt.');
  });

  it('uses one-based targets and disables first/last keyboard movement controls', async () => {
    const version = {
      id: 'version',
      exerciseSetId: 'set',
      status: 'DRAFT' as const,
      lockVersion: 4,
      items: [
        {
          id: 'first',
          exerciseVersionId: 'exercise-1',
          phase: 'MAIN' as const,
          position: 1,
          snapshot: { canonicalName: 'Pierwsze' },
        },
        {
          id: 'middle',
          exerciseVersionId: 'exercise-2',
          phase: 'MAIN' as const,
          position: 2,
          snapshot: { canonicalName: 'Środkowe' },
        },
        {
          id: 'last',
          exerciseVersionId: 'exercise-3',
          phase: 'MAIN' as const,
          position: 3,
          snapshot: { canonicalName: 'Ostatnie' },
        },
      ],
    };
    const move = vi.fn().mockResolvedValue(version);
    await TestBed.configureTestingModule({
      imports: [ExerciseSetEditorPage],
      providers: [
        provideRouter([]),
        {
          provide: ApiFacade,
          useValue: { exerciseSets: { version: vi.fn().mockResolvedValue(version), move } },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ exerciseSetId: 'set', versionId: 'version' }),
            },
          },
        },
      ],
    }).compileComponents();
    const fixture: ComponentFixture<ExerciseSetEditorPage> =
      TestBed.createComponent(ExerciseSetEditorPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[];
    const up = buttons.filter((button) => button.textContent?.trim() === 'W górę');
    const down = buttons.filter((button) => button.textContent?.trim() === 'W dół');
    expect(up[0].disabled).toBe(true);
    expect(down[2].disabled).toBe(true);
    up[1].click();
    await fixture.whenStable();
    expect(move).toHaveBeenCalledWith({
      setId: 'set',
      versionId: 'version',
      moveRequest: { itemId: 'middle', targetPosition: 1, expectedVersion: 4 },
    });
  });

  it('uses each returned lock token for the next mutation without reloading or incrementing locally', async () => {
    const initial = {
      id: 'version',
      exerciseSetId: 'set',
      status: 'DRAFT' as const,
      lockVersion: 4,
      items: [
        {
          id: 'first',
          exerciseVersionId: 'exercise-1',
          phase: 'MAIN' as const,
          position: 1,
          snapshot: { canonicalName: 'Pierwsze' },
        },
        {
          id: 'middle',
          exerciseVersionId: 'exercise-2',
          phase: 'MAIN' as const,
          position: 2,
          snapshot: { canonicalName: 'Środkowe' },
        },
      ],
    };
    const afterFirstMove = {
      ...initial,
      lockVersion: 11,
      items: [
        { ...initial.items[1], position: 1 },
        { ...initial.items[0], position: 2 },
      ],
    };
    const afterSecondMove = { ...afterFirstMove, lockVersion: 18, items: initial.items };
    const version = vi.fn().mockResolvedValue(initial);
    const move = vi
      .fn()
      .mockResolvedValueOnce(afterFirstMove)
      .mockResolvedValueOnce(afterSecondMove);
    await TestBed.configureTestingModule({
      imports: [ExerciseSetEditorPage],
      providers: [
        provideRouter([]),
        { provide: ApiFacade, useValue: { exerciseSets: { version, move } } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ exerciseSetId: 'set', versionId: 'version' }),
            },
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseSetEditorPage);
    fixture.detectChanges();
    await fixture.whenStable();

    await fixture.componentInstance.move(initial.items[1], -1);
    await fixture.componentInstance.move(afterFirstMove.items[0], 1);

    expect(move).toHaveBeenNthCalledWith(1, {
      setId: 'set',
      versionId: 'version',
      moveRequest: { itemId: 'middle', targetPosition: 1, expectedVersion: 4 },
    });
    expect(move).toHaveBeenNthCalledWith(2, {
      setId: 'set',
      versionId: 'version',
      moveRequest: { itemId: 'middle', targetPosition: 2, expectedVersion: 11 },
    });
    expect(version).toHaveBeenCalledTimes(1);
  });

  it('renders neutral Polish suggestions without policy metadata, technical codes, or blocked status', async () => {
    const analysis = {
      status: 'SUGGESTIONS_AVAILABLE' as const,
      analyzedLockVersion: 4,
      policyVersion: 'SET-05',
      analyzedAt: new Date('2026-01-02T10:00:00Z'),
      metrics: { estimatedSeconds: 90, timeConfidence: 'PARTIAL' as const },
      findings: [
        {
          code: 'TITLE_REQUIRED',
          severity: 'BLOCKING' as const,
          explanation: 'Structural completeness is required before publication.',
          blocking: true,
          field: 'title',
        },
        {
          code: 'EQUIPMENT_TRANSITIONS',
          severity: 'WARNING' as const,
          explanation: 'Review this deterministic structural signal.',
        },
        {
          code: 'TIME_ESTIMATE_UNAVAILABLE',
          severity: 'SUGGESTION' as const,
          explanation: 'Consider simplifying the sequence.',
        },
      ],
    };
    const published = {
      id: 'version',
      exerciseSetId: 'set',
      status: 'PUBLISHED' as const,
      lockVersion: 4,
      items: [],
      analysis,
    };
    await TestBed.configureTestingModule({
      imports: [ExerciseSetEditorPage],
      providers: [
        provideRouter([]),
        {
          provide: ApiFacade,
          useValue: { exerciseSets: { version: vi.fn().mockResolvedValue(published) } },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ exerciseSetId: 'set', versionId: 'version' }),
            },
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseSetEditorPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Sugestie do zestawu');
    expect(text).toContain('Zestaw nie ma jeszcze tytułu.');
    expect(text).toContain('W zestawie często zmienia się wymagany sprzęt.');
    expect(text).toContain('Szacowany czas wykonania jest niedostępny.');
    expect(text).not.toContain('Blokery');
    expect(text).not.toContain('Zablokowany');
    expect(text).not.toContain('Polityka techniczna');
    expect(text).not.toContain('TITLE_REQUIRED');
    expect(text).not.toContain('Structural completeness');
  });

  it('marks analysis stale after a mutation and only accepts analysis for the current lock version', async () => {
    const initial = {
      id: 'version',
      exerciseSetId: 'set',
      status: 'DRAFT' as const,
      lockVersion: 4,
      items: [
        { id: 'item', exerciseVersionId: 'exercise', phase: 'MAIN' as const, position: 1 },
        { id: 'item-2', exerciseVersionId: 'exercise-2', phase: 'MAIN' as const, position: 2 },
      ],
    };
    const updated = { ...initial, lockVersion: 5 };
    let resolveAnalysis: ((value: unknown) => void) | undefined;
    const analysis = vi.fn().mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveAnalysis = resolve;
        }),
    );
    const move = vi.fn().mockResolvedValue(updated);
    await TestBed.configureTestingModule({
      imports: [ExerciseSetEditorPage],
      providers: [
        provideRouter([]),
        {
          provide: ApiFacade,
          useValue: {
            exerciseSets: { version: vi.fn().mockResolvedValue(initial), move, analysis },
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ exerciseSetId: 'set', versionId: 'version' }),
            },
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseSetEditorPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.componentInstance.move(initial.items[0], 0 + 0); // no mutation at boundary-free zero
    await fixture.componentInstance.move(initial.items[0], 1);
    expect(fixture.componentInstance.analysisStale()).toBe(true);
    void fixture.componentInstance.retryAnalysis();
    resolveAnalysis?.({ analyzedLockVersion: 4, status: 'VALID' });
    await new Promise((resolve) => setTimeout(resolve));
    expect(fixture.componentInstance.analysis()).toBeUndefined();
  });

  it('debounces reanalysis after a saved metadata mutation', async () => {
    vi.useFakeTimers();
    const initial = {
      id: 'version',
      exerciseSetId: 'set',
      status: 'DRAFT' as const,
      lockVersion: 4,
      title: 'A',
      profile: 'FULL_SELF_GUIDED' as const,
      items: [],
    };
    const metadata = vi.fn().mockResolvedValue({ ...initial, lockVersion: 5 });
    const analysis = vi.fn().mockResolvedValue({ analyzedLockVersion: 5, status: 'VALID' });
    await TestBed.configureTestingModule({
      imports: [ExerciseSetEditorPage],
      providers: [
        provideRouter([]),
        {
          provide: ApiFacade,
          useValue: {
            exerciseSets: { version: vi.fn().mockResolvedValue(initial), metadata, analysis },
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ exerciseSetId: 'set', versionId: 'version' }),
            },
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseSetEditorPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.componentInstance.title.setValue('B');
    await vi.advanceTimersByTimeAsync(500);
    expect(metadata).toHaveBeenCalledTimes(1);
    expect(analysis).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(350);
    expect(analysis).toHaveBeenCalledTimes(2);
    vi.useRealTimers();
  });

  it('navigates findings by focusing an item, phase, or preparation picker without mutating', async () => {
    const draft = {
      id: 'version',
      exerciseSetId: 'set',
      status: 'DRAFT' as const,
      lockVersion: 4,
      items: [],
    };
    const api = {
      version: vi.fn().mockResolvedValue(draft),
      analysis: vi.fn().mockResolvedValue({ analyzedLockVersion: 4, status: 'VALID' }),
    };
    await TestBed.configureTestingModule({
      imports: [ExerciseSetEditorPage],
      providers: [
        provideRouter([]),
        { provide: ApiFacade, useValue: { exerciseSets: api } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ exerciseSetId: 'set', versionId: 'version' }),
            },
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseSetEditorPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const item = document.createElement('button');
    item.id = 'item-item-1';
    document.body.append(item);
    const focus = vi.spyOn(item, 'focus');
    fixture.componentInstance.navigateFinding({ itemIds: ['item-1'] });
    expect(focus).toHaveBeenCalled();
    item.remove();
    const phase = fixture.nativeElement.querySelector('#phase-PREPARATION') as HTMLElement;
    expect(phase).toBeTruthy();
    const phaseFocus = vi.spyOn(phase, 'focus');
    fixture.componentInstance.navigateFinding({ phase: 'PREPARATION' });
    expect(phaseFocus).toHaveBeenCalled();
    phase.remove();
    fixture.componentInstance.navigateFinding({ phase: 'PREPARATION' });
    expect(fixture.componentInstance.addingPhase()).toBe('PREPARATION');
  });

  it('allows publication despite historical BLOCKED findings and missing title, profile, and items', async () => {
    const blocking = {
      status: 'BLOCKED' as const,
      analyzedLockVersion: 4,
      findings: [{ severity: 'BLOCKING' as const, blocking: true, explanation: 'Napraw dane' }],
    };
    const draft = {
      id: 'version',
      exerciseSetId: 'set',
      status: 'DRAFT' as const,
      lockVersion: 4,
      items: [],
      analysis: blocking,
    };
    await TestBed.configureTestingModule({
      imports: [ExerciseSetEditorPage],
      providers: [
        provideRouter([]),
        {
          provide: ApiFacade,
          useValue: {
            exerciseSets: {
              version: vi.fn().mockResolvedValue(draft),
              analysis: vi.fn().mockResolvedValue(blocking),
            },
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ exerciseSetId: 'set', versionId: 'version' }),
            },
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseSetEditorPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.componentInstance.analysis.set(blocking);
    fixture.componentInstance.analysisStale.set(false);
    fixture.detectChanges();
    expect(fixture.componentInstance.canPublish()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Zestaw bez nazwy');
    expect(fixture.nativeElement.textContent).toContain('Nie określono');
    expect(fixture.nativeElement.textContent).not.toContain('Zablokowany');
  });

  it('serializes metadata saves so a late response cannot regress the next lock or form values', async () => {
    const initial = {
      id: 'version',
      exerciseSetId: 'set',
      status: 'DRAFT' as const,
      lockVersion: 4,
      title: 'A',
      profile: 'FULL_SELF_GUIDED' as const,
      items: [],
    };
    let resolveFirst: ((value: unknown) => void) | undefined;
    let resolveSecond: ((value: unknown) => void) | undefined;
    const metadata = vi
      .fn()
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirst = resolve;
          }),
      )
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveSecond = resolve;
          }),
      );
    await TestBed.configureTestingModule({
      imports: [ExerciseSetEditorPage],
      providers: [
        provideRouter([]),
        {
          provide: ApiFacade,
          useValue: {
            exerciseSets: {
              version: vi.fn().mockResolvedValue(initial),
              metadata,
              analysis: vi.fn(),
            },
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ exerciseSetId: 'set', versionId: 'version' }),
            },
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseSetEditorPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.componentInstance.saveMetadata();
    fixture.componentInstance.title.setValue('B');
    fixture.componentInstance.saveMetadata();
    resolveFirst?.({ ...initial, lockVersion: 5, title: 'A' });
    await new Promise((resolve) => setTimeout(resolve));
    expect(metadata).toHaveBeenNthCalledWith(2, {
      setId: 'set',
      versionId: 'version',
      metadataRequest: expect.objectContaining({ expectedVersion: 5, title: 'B' }),
    });
    resolveSecond?.({ ...initial, lockVersion: 6, title: 'B' });
    await new Promise((resolve) => setTimeout(resolve));
    expect(fixture.componentInstance.version()?.lockVersion).toBe(6);
  });

  it('handles publish technical errors without blind stale-lock retries', async () => {
    const draft = {
      id: 'version',
      exerciseSetId: 'set',
      status: 'DRAFT' as const,
      lockVersion: 4,
      title: 'T',
      profile: 'FULL_SELF_GUIDED' as const,
      items: [{ id: 'item' }],
    };
    const publishVersion = vi
      .fn()
      .mockRejectedValueOnce(new Response('', { status: 409 }))
      .mockRejectedValueOnce(new Response('', { status: 400 }))
      .mockRejectedValueOnce(new Error('offline'));
    const analysis = vi
      .fn()
      .mockResolvedValue({
        analyzedLockVersion: 4,
        status: 'BLOCKED',
        findings: [{ severity: 'BLOCKING', blocking: true }],
      });
    await TestBed.configureTestingModule({
      imports: [ExerciseSetEditorPage],
      providers: [
        provideRouter([]),
        {
          provide: ApiFacade,
          useValue: {
            exerciseSets: { version: vi.fn().mockResolvedValue(draft), publishVersion, analysis },
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ exerciseSetId: 'set', versionId: 'version' }),
            },
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseSetEditorPage);
    fixture.detectChanges();
    await fixture.whenStable();
    await fixture.componentInstance.publish();
    expect(fixture.componentInstance.conflict()).toBe(true);
    expect(analysis).not.toHaveBeenCalled();
    fixture.componentInstance.conflict.set(false);
    await fixture.componentInstance.publish();
    expect(analysis).toHaveBeenCalledTimes(1);
    const priorAnalysis = fixture.componentInstance.analysis();
    await fixture.componentInstance.publish();
    expect(fixture.componentInstance.saveState()).toBe('Nie udało się zapisać');
    expect(fixture.componentInstance.analysis()).toBe(priorAnalysis);
  });

  it('rejects a stale visual exposure response after the draft lock changes', async () => {
    const initial = {
      id: 'version',
      exerciseSetId: 'set',
      status: 'DRAFT' as const,
      lockVersion: 4,
      items: [{ id: 'item', position: 1 }],
    };
    const updated = { ...initial, lockVersion: 5 };
    let resolve!: (value: any) => void;
    const anatomy = vi.fn().mockImplementation(
      () =>
        new Promise((value) => {
          resolve = value;
        }),
    );
    const move = vi.fn().mockResolvedValue(updated);
    await TestBed.configureTestingModule({
      imports: [ExerciseSetEditorPage],
      providers: [
        provideRouter([]),
        {
          provide: ApiFacade,
          useValue: {
            exerciseSets: { version: vi.fn().mockResolvedValue(initial), anatomy, move },
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ exerciseSetId: 'set', versionId: 'version' }),
            },
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseSetEditorPage);
    fixture.detectChanges();
    await fixture.whenStable();
    void fixture.componentInstance.retryAnatomy();
    (fixture.componentInstance as any).version.set(updated);
    resolve({
      analyzedLockVersion: 4,
      visualMappingVersion: '1',
      visualMappingCompleteness: 'COMPLETE',
      visualRegionExposures: [{ visualRegionCode: 'ANATOMY_VISUAL_MAP_V1:FRONT:THIGH' }],
    });
    await new Promise((done) => setTimeout(done));
    expect(fixture.componentInstance.anatomy()).toBeUndefined();
  });

  it('does not expose the persisted visual mapping version for a published historical read', async () => {
    const published = {
      id: 'version',
      exerciseSetId: 'set',
      status: 'PUBLISHED' as const,
      lockVersion: 4,
      items: [],
    };
    const visual = {
      analyzedLockVersion: 4,
      completeness: 'COMPLETE',
      visualMappingVersion: 'historic-7',
      visualMappingCompleteness: 'COMPLETE',
      visualRegionExposures: [],
    };
    await TestBed.configureTestingModule({
      imports: [ExerciseSetEditorPage],
      providers: [
        provideRouter([]),
        {
          provide: ApiFacade,
          useValue: {
            exerciseSets: {
              version: vi.fn().mockResolvedValue(published),
              anatomy: vi.fn().mockResolvedValue(visual),
            },
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ exerciseSetId: 'set', versionId: 'version' }),
            },
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseSetEditorPage);
    fixture.detectChanges();
    await fixture.whenStable();
    await fixture.componentInstance.retryAnatomy();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Wersja mapowania: historic-7');
  });
});
