import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { ExerciseImportApi } from '../core/exercise-import.api';
import { ExerciseImportPage } from './exercise-import.page';

const api = {
  sources: vi.fn().mockResolvedValue([]),
  createSource: vi.fn(),
  upload: vi.fn(), batch: vi.fn(), records: vi.fn(), record: vi.fn(), decide: vi.fn(),
  createDraft: vi.fn(), review: vi.fn(), reviewStatus: vi.fn(), diff: vi.fn(), publish: vi.fn()
};

describe('ExerciseImportPage', () => {
  it('creates the starter source and enables upload after a file is selected', async () => {
    const source = { id: 'starter-id', code: 'MOVES_STARTER_V1', displayName: 'Moves starter exercises V1', licenseCode: 'MOVES-INTERNAL-AUTHORING-1.0', licenseVerified: true };
    api.sources.mockResolvedValueOnce([]).mockResolvedValueOnce([source]);
    api.createSource.mockResolvedValueOnce(source);
    await TestBed.configureTestingModule({
      imports: [ExerciseImportPage], providers: [{ provide: ExerciseImportApi, useValue: api }]
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseImportPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const create = [...page.querySelectorAll('button')].find(button => button.textContent?.includes('Utwórz źródło')) as HTMLButtonElement;
    create.click();
    await fixture.whenStable();
    fixture.detectChanges();
    const instance = fixture.componentInstance as any;
    const input = page.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(input, 'files', { value: [new File(['{}'], 'exercises.jsonl', { type: 'application/x-ndjson' })] });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    const upload = [...page.querySelectorAll('button')].find(button => button.textContent?.includes('Prześlij')) as HTMLButtonElement;
    expect(api.createSource).toHaveBeenCalledWith({ code: 'MOVES_STARTER_V1', displayName: 'Moves starter exercises V1', defaultLocale: 'pl-PL', licenseCode: 'MOVES-INTERNAL-AUTHORING-1.0', licenseVerified: true });
    expect(instance.sourceId()).toBe('starter-id');
    expect(upload.disabled).toBe(false);
  });

  it('enables upload only after a source and JSONL file are selected', async () => {
    await TestBed.configureTestingModule({
      imports: [ExerciseImportPage], providers: [{ provide: ExerciseImportApi, useValue: api }]
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseImportPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const upload = [...page.querySelectorAll('button')].find(button => button.textContent?.includes('Prześlij')) as HTMLButtonElement;
    const input = page.querySelector('input[type="file"]') as HTMLInputElement;
    const instance = fixture.componentInstance as any;
    expect(upload.disabled).toBe(true);

    instance.sourceId.set('source-id');
    fixture.detectChanges();
    expect(upload.disabled).toBe(true);

    Object.defineProperty(input, 'files', { value: [new File(['{}'], 'exercises.jsonl', { type: 'application/x-ndjson' })] });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(upload.disabled).toBe(false);

    instance.sourceId.set('');
    fixture.detectChanges();
    expect(upload.disabled).toBe(true);
  });
});
