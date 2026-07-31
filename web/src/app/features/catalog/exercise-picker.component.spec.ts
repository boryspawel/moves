import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';
import { describe, expect, it, vi, afterEach } from 'vitest';
import { ApiFacade } from '../../core/api.facade';
import { ExercisePickerComponent } from './exercise-picker.component';

const result = (id: string, title = 'Przysiad') => ({exerciseId: `exercise-${id}`, exerciseVersionId: id, title, summary: 'Krótki opis', exerciseType: 'STRENGTH', technicalLevel: 'FOUNDATIONAL', movementPatterns: ['SQUAT'], equipment: ['bodyweight'], selectable: true});
const page = (results = [result('v1')], more = false, cursor?: string) => ({results, facets: [{group: 'MOVEMENT_PATTERN', value: 'SQUAT', labelKey: 'SQUAT', count: 1, active: false}], hasMore: more, nextCursor: cursor});

describe('ExercisePickerComponent', () => {
  afterEach(() => vi.useRealTimers());
  async function setup(search = vi.fn().mockResolvedValue(page()), preview = vi.fn().mockResolvedValue({exerciseId:'exercise-v1', exerciseVersionId:'v1', title:'Przysiad', movementPatterns:['SQUAT'], requiredEquipment:['bodyweight']})) {
    await TestBed.configureTestingModule({imports: [ExercisePickerComponent, NoopAnimationsModule, RouterTestingModule], providers: [{provide: ApiFacade, useValue: {catalogSearch: {search, preview}}}]}).compileComponents();
    const fixture = TestBed.createComponent(ExercisePickerComponent); fixture.componentRef.setInput('selectionEnabled', true); fixture.detectChanges(); await Promise.resolve(); await Promise.resolve(); fixture.detectChanges();
    return {fixture, search, preview};
  }

  it('wyszukuje po debounced frazie i zachowuje locale pl-PL', async () => {
    vi.useFakeTimers(); const {fixture, search} = await setup(); const component = fixture.componentInstance;
    component.query.setValue('przysiad'); vi.advanceTimersByTime(250); await Promise.resolve();
    expect(search).toHaveBeenLastCalledWith({searchRequest: expect.objectContaining({query: 'przysiad', locale: 'pl-PL'})});
  });

  it('czyści frazę i wszystkie filtry', async () => {
    const {fixture} = await setup(); const component = fixture.componentInstance;
    component.query.setValue('przysiad', {emitEvent: false}); component.toggleFacet({group:'MOVEMENT_PATTERN', value:'SQUAT'}); await Promise.resolve(); component.reset(); await Promise.resolve();
    expect(component.query.value).toBe(''); expect(component.activeFilterCount()).toBe(0);
  });

  it('wysyła wybrany filtr i pokazuje facetę', async () => {
    const {fixture, search} = await setup(); const component = fixture.componentInstance;
    component.toggleFacet({group:'MOVEMENT_PATTERN', value:'SQUAT'}); await Promise.resolve();
    expect(search).toHaveBeenLastCalledWith({searchRequest: expect.objectContaining({movementPatterns:['SQUAT']})});
    expect(fixture.nativeElement.textContent).toContain('Wzorzec ruchu');
  });

  it('doładowuje kolejną stronę bez duplikatu wersji', async () => {
    const search = vi.fn().mockResolvedValueOnce(page([result('v1')], true, 'cursor-1')).mockResolvedValueOnce(page([result('v1'), result('v2', 'Wykrok')], false));
    const {fixture} = await setup(search); const component = fixture.componentInstance; component.loadMore(); await Promise.resolve(); await Promise.resolve(); fixture.detectChanges();
    expect(component.results().map(item => item.exerciseVersionId)).toEqual(['v1', 'v2']);
  });

  it('pokazuje i zamyka podgląd, przywracając fokus', async () => {
    const {fixture, preview} = await setup(); const component = fixture.componentInstance; const opener = document.createElement('button'); document.body.append(opener); const focus = vi.spyOn(opener, 'focus');
    component.openPreview(component.results()[0]!, opener); await Promise.resolve(); fixture.detectChanges();
    expect(preview).toHaveBeenCalledWith({exerciseVersionId:'v1'}); expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull(); component.closePreview(); await Promise.resolve(); expect(focus).toHaveBeenCalled(); opener.remove();
  });

  it('emituje minimalny kontrakt z dokładnym identyfikatorem wersji', async () => {
    const {fixture} = await setup(); const component = fixture.componentInstance; const selected = vi.fn(); component.selected.subscribe(selected);
    component.select(component.results()[0]!);
    expect(selected).toHaveBeenCalledWith(expect.objectContaining({exerciseId:'exercise-v1', exerciseVersionId:'v1', name:'Przysiad', suggestedDoseType:'STRENGTH'}));
    expect(JSON.stringify(selected.mock.calls)).not.toContain('uuid');
  });

  it('używa semantycznego przycisku dla podglądu', async () => {
    const {fixture} = await setup();
    expect([...fixture.nativeElement.querySelectorAll('button')].map((button: HTMLButtonElement) => button.textContent?.trim())).toEqual(expect.arrayContaining(['Podgląd']));
  });
});
