import {TestBed} from '@angular/core/testing';
import {RouterTestingModule} from '@angular/router/testing';
import {describe, expect, it, vi} from 'vitest';
import {ExerciseImportApi} from '../core/exercise-import.api';
import {ExerciseImportPage} from './exercise-import.page';

const api = {
  sources: vi.fn(),
  createSource: vi.fn(),
  upload: vi.fn(),
  batch: vi.fn(),
  issues: vi.fn(),
  downloadIssues: vi.fn()
};

describe('ExerciseImportPage', () => {
  it('uses the starter source automatically and provides a keyboard-accessible JSONL picker', async () => {
    api.sources.mockResolvedValue([{
      id: 'starter-id',
      code: 'MOVES_STARTER_V1',
      displayName: 'Moves starter exercises V1',
      licenseCode: 'license',
      licenseVerified: true
    }]);
    await TestBed.configureTestingModule({
      imports: [ExerciseImportPage, RouterTestingModule],
      providers: [{provide: ExerciseImportApi, useValue: api}]
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseImportPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('h1')?.textContent).toContain('Import ćwiczeń');
    expect([...page.querySelectorAll('button')].find(button => button.textContent?.includes('Wybierz plik JSONL'))).toBeTruthy();
    expect((fixture.componentInstance as any).sourceId()).toBe('starter-id');
    expect(page.textContent).not.toContain('Raw');
    expect(page.textContent).not.toContain('SAME');
  });

  it('rejects an invalid extension before upload', async () => {
    api.sources.mockResolvedValue([{
      id: 'source-id',
      code: 'OTHER',
      displayName: 'Other',
      licenseCode: 'license',
      licenseVerified: true
    }]);
    await TestBed.configureTestingModule({
      imports: [ExerciseImportPage, RouterTestingModule],
      providers: [{provide: ExerciseImportApi, useValue: api}]
    }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseImportPage);
    fixture.detectChanges();
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(input, 'files', {value: [new File(['{}'], 'exercises.json', {type: 'application/json'})]});
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Wybierz plik z rozszerzeniem .jsonl.');
  });
});
