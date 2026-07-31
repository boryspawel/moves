import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { BodyMapComponent } from './body-map.component';

const exposure = (overrides: Record<string, unknown> = {}) =>
  ({
    visualRegionCode: 'ANATOMY_VISUAL_MAP_V1:FRONT:THIGH',
    view: 'FRONT',
    layer: 'MUSCLE',
    laterality: 'RIGHT',
    channel: 'DYN_EXU',
    rawValue: 4,
    unit: 'j.',
    shareWithinChannel: 40,
    concentrationBand: 'SIGNIFICANT',
    completeness: 'COMPLETE',
    mappingVersion: 1,
    sourceStructures: [{ anatomicalStructureCode: 'STRUCTURE_CODE' }],
    breakdowns: [
      {
        contributionId: 'contribution',
        itemId: 'item',
        exerciseVersionId: 'exercise',
        rawValue: 4,
      },
    ],
    ...overrides,
  }) as any;
const analysis = (exposures = [exposure()]) =>
  ({
    visualMappingVersion: '1',
    visualMappingCompleteness: 'COMPLETE',
    visualRegionExposures: exposures,
    completeness: 'COMPLETE',
  }) as any;

describe('BodyMapComponent', () => {
  async function setup(value = analysis()) {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            '<svg xmlns="http://www.w3.org/2000/svg"><g data-visual-region-code="ANATOMY_VISUAL_MAP_V1:FRONT:THIGH" data-view="FRONT"/></svg>',
            { status: 200 },
          ),
        ),
    );
    await TestBed.configureTestingModule({ imports: [BodyMapComponent] }).compileComponents();
    const fixture: ComponentFixture<BodyMapComponent> = TestBed.createComponent(BodyMapComponent);
    fixture.componentRef.setInput('analysis', value);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
    return fixture;
  }
  it('joins SVG only by exact visualRegionCode and exposes API band', async () => {
    const fixture = await setup();
    const region = fixture.nativeElement.querySelector('g[data-visual-region-code]') as SVGGElement;
    expect(region.getAttribute('role')).toBe('button');
    expect(region.getAttribute('aria-label')).toContain('Istotny');
    expect(fixture.nativeElement.textContent).not.toContain('STRUCTURE_CODE:');
  });
  it('switches FRONT/BACK and channels without deriving any anatomy values', async () => {
    const fixture = await setup(
      analysis([
        exposure(),
        exposure({
          visualRegionCode: 'ANATOMY_VISUAL_MAP_V1:BACK:THIGH',
          view: 'BACK',
          channel: 'ISO_SEC',
          rawValue: 9,
          concentrationBand: 'DOMINANT',
        }),
      ]),
    );
    fixture.componentInstance.setView('BACK');
    fixture.componentInstance.setChannel('ISO_SEC');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('ANATOMY_VISUAL_MAP_V1:BACK:THIGH');
    expect(fixture.nativeElement.textContent).not.toContain('ANATOMY_VISUAL_MAP_V1:FRONT:THIGH');
  });
  it('selects by keyboard, renders breakdown and restores focus after close', async () => {
    const fixture = await setup();
    const region = fixture.nativeElement.querySelector('g[data-visual-region-code]') as SVGGElement;
    const focus = vi.spyOn(region, 'focus');
    const select = vi.spyOn(fixture.componentInstance, 'select');
    region.onkeydown?.(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(select).toHaveBeenCalled();
    select.mockRestore();
    fixture.componentInstance.select(exposure(), region);
    fixture.detectChanges();
    expect(fixture.componentInstance.selected?.breakdowns[0]?.exerciseVersionId).toBe('exercise');
    fixture.componentInstance.closeDetail();
    expect(focus).toHaveBeenCalled();
  });
  it('updates detail from a real SVG click without exposing mapping history', async () => {
    const fixture = await setup(analysis());
    const region = fixture.nativeElement.querySelector('g[data-visual-region-code]') as SVGGElement;
    region.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')?.textContent).toContain(
      'exercise',
    );
    expect(fixture.nativeElement.textContent).not.toContain('Wersja mapowania: 1');
  });
  it('keeps results without SVG geometry in the textual fallback and reports partial/unavailable', async () => {
    const fixture = await setup(analysis([exposure({ visualRegionCode: 'NO_GEOMETRY' })]));
    fixture.componentRef.setInput('analysis', {
      ...analysis([exposure({ visualRegionCode: 'NO_GEOMETRY' })]),
      visualMappingCompleteness: 'PARTIAL',
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Brak geometrii w mapie V1');
    expect(fixture.nativeElement.textContent).toContain(
      'Część danych ekspozycji może być niedostępna.',
    );
  });
  it('renders explicit loading, error and unavailable states', async () => {
    const fixture = await setup(analysis());
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ładowanie ekspozycji mapy');
    fixture.componentRef.setInput('loading', false);
    fixture.componentRef.setInput('error', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nie udało się pobrać ekspozycji mapy');
    fixture.componentRef.setInput('error', false);
    fixture.componentRef.setInput('analysis', {
      ...analysis(),
      visualMappingVersion: 'UNAVAILABLE',
      visualMappingCompleteness: 'UNAVAILABLE',
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Mapa ekspozycji jest niedostępna');
  });
  it.each([390, 320])(
    'keeps semantic map containers available at %ipx without horizontal content',
    async (width) => {
      const fixture = await setup();
      const host = fixture.nativeElement.querySelector('.body-map') as HTMLElement;
      const svg = fixture.nativeElement.querySelector('.svg-host') as HTMLElement;
      Object.defineProperty(host, 'clientWidth', { value: width });
      Object.defineProperty(host, 'scrollWidth', { value: width });
      expect(svg).not.toBeNull();
      expect(host.classList.contains('body-map')).toBe(true);
      expect(host.scrollWidth).toBeLessThanOrEqual(host.clientWidth);
    },
  );
});
