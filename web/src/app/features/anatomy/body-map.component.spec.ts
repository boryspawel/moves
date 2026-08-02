import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { BodyMapComponent } from './body-map.component';

const exposure = (overrides: Record<string, unknown> = {}) => ({
  visualRegionCode: 'ANATOMY_VISUAL_MAP_V1:FRONT:THIGH', displayName: 'Prawe udo', view: 'FRONT', layer: 'MUSCLE', laterality: 'RIGHT', channel: 'DYN_EXU', shareWithinChannel: 40, concentrationBand: 'SIGNIFICANT', ...overrides,
}) as any;
const analysis = (exposures = [exposure()]) => ({ visualMappingVersion: '1', visualMappingCompleteness: 'COMPLETE', visualRegionExposures: exposures, completeness: 'COMPLETE' }) as any;
const asset = (view: 'FRONT' | 'BACK') => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 200" data-anatomy-view="${view}">
  <g data-layer="base-silhouette" data-view="${view}"><path data-anatomy-geometry="base" fill="#f0a080"/></g>
  <g data-layer="exposure-overlay" data-view="${view}">
    <g data-visual-region-code="ANATOMY_VISUAL_MAP_V1:${view}:THIGH" data-laterality="LEFT"><path data-anatomy-geometry="exposure" fill="#f0a080"/></g>
    <g data-visual-region-code="ANATOMY_VISUAL_MAP_V1:${view}:THIGH" data-laterality="RIGHT"><path data-anatomy-geometry="exposure" fill="#f0a080"/></g>
    <g data-visual-region-code="ANATOMY_VISUAL_MAP_V1:${view}:THORACIC" data-laterality="CENTRAL"><path data-anatomy-geometry="exposure" fill="#f0a080"/></g>
  </g>
</svg>`;

describe('BodyMapComponent', () => {
  async function setup(value = analysis()) {
    vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(new Response(asset(url.includes('back') ? 'BACK' : 'FRONT'), { status: 200 }))));
    await TestBed.configureTestingModule({ imports: [BodyMapComponent] }).compileComponents();
    const fixture: ComponentFixture<BodyMapComponent> = TestBed.createComponent(BodyMapComponent);
    fixture.componentRef.setInput('analysis', value); fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve)); fixture.detectChanges();
    return fixture;
  }
  it('loads the explicit FRONT and BACK assets, caches both geometry contracts, and renders only FRONT', async () => {
    const fixture = await setup();
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.hasGeometry('ANATOMY_VISUAL_MAP_V1:BACK:THIGH')).toBe(true);
    expect(fixture.nativeElement.querySelectorAll('.svg-host svg')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('svg')?.dataset.anatomyView).toBe('FRONT');
    expect(fixture.nativeElement.querySelectorAll('[data-layer="base-silhouette"]')).toHaveLength(1);
    expect(fixture.nativeElement.querySelectorAll('[data-layer="exposure-overlay"]')).toHaveLength(1);
  });
  it('switches a complete selected view asset without rendering hidden-view exposure', async () => {
    const fixture = await setup(analysis([exposure(), exposure({ visualRegionCode: 'ANATOMY_VISUAL_MAP_V1:BACK:THIGH', displayName: 'Tylne udo', view: 'BACK', concentrationBand: 'DOMINANT' })]));
    fixture.componentInstance.setView('BACK'); await new Promise((resolve) => setTimeout(resolve)); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.svg-host svg')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('svg')?.dataset.anatomyView).toBe('BACK');
    expect(fixture.nativeElement.querySelector('[aria-label^="Tylne udo"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[aria-label^="Prawe udo"]')).toBeNull();
  });
  it('keeps base geometry neutral and noninteractive while coloring only exposed overlay by band', async () => {
    const fixture = await setup();
    const base = fixture.nativeElement.querySelector('[data-anatomy-geometry="base"]') as SVGElement;
    const overlay = fixture.nativeElement.querySelector('[data-laterality="RIGHT"] [data-anatomy-geometry="exposure"]') as SVGElement;
    expect(base.style.getPropertyValue('fill')).toBe('rgb(228, 231, 235)');
    expect(base.style.getPropertyPriority('fill')).toBe('important');
    expect(base.closest('[data-layer]')?.getAttribute('pointer-events')).toBe('none');
    expect(overlay.style.getPropertyValue('fill')).toBe('rgb(78, 145, 209)');
    expect(fixture.nativeElement.textContent).toContain('Istotny');
  });
  it('adds only an outline for selection and supports keyboard selection and focus restoration', async () => {
    const fixture = await setup();
    const group = fixture.nativeElement.querySelector('[data-laterality="RIGHT"]') as SVGGElement;
    const focus = vi.spyOn(group, 'focus');
    group.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true })); fixture.detectChanges();
    expect(group.classList.contains('is-selected')).toBe(true);
    expect((fixture.nativeElement.querySelector('[data-laterality="RIGHT"] [data-anatomy-geometry="exposure"]') as SVGElement).style.getPropertyValue('fill')).toBe('rgb(78, 145, 209)');
    fixture.componentInstance.closeDetail();
    expect(focus).toHaveBeenCalled();
  });
  it('uses the custom accessible segmented control and lightweight selectable list', async () => {
    const fixture = await setup();
    expect(fixture.nativeElement.querySelector('fieldset')).toBeNull();
    expect(fixture.nativeElement.querySelector('.view-switch [aria-pressed="true"]')?.textContent).toContain('Przód');
    const row = fixture.nativeElement.querySelector('.active-regions button') as HTMLButtonElement;
    expect(row.textContent).toContain('Prawe udo'); expect(row.textContent).toContain('Istotny');
  });
  it('matches exposure overlay laterality without code-only leakage', async () => {
    const fixture = await setup(analysis([exposure({ laterality: 'BILATERAL', concentrationBand: 'DOMINANT' })]));
    const fills = (side: string) => (fixture.nativeElement.querySelector(`[data-laterality="${side}"] [data-anatomy-geometry="exposure"]`) as SVGElement).style.getPropertyValue('fill');
    expect(fills('LEFT')).toBe('rgb(18, 97, 160)');
    expect(fills('RIGHT')).toBe('rgb(18, 97, 160)');
    expect(fills('CENTRAL')).toBe('transparent');
    fixture.nativeElement.querySelector('.active-regions button').click(); fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('[data-laterality="LEFT"]') as SVGGElement).classList.contains('is-selected')).toBe(true);
    expect((fixture.nativeElement.querySelector('[data-laterality="RIGHT"]') as SVGGElement).classList.contains('is-selected')).toBe(true);
    fixture.componentRef.setInput('analysis', analysis([exposure({ laterality: 'LEFT', concentrationBand: 'LOW' })])); await new Promise((resolve) => setTimeout(resolve)); fixture.detectChanges();
    expect(fills('LEFT')).toBe('rgb(168, 197, 229)');
    expect(fills('RIGHT')).toBe('transparent');
    fixture.componentRef.setInput('analysis', analysis([exposure({ visualRegionCode: 'ANATOMY_VISUAL_MAP_V1:FRONT:THORACIC', displayName: 'Klatka', laterality: 'CENTRAL', concentrationBand: 'LOW' })])); await new Promise((resolve) => setTimeout(resolve)); fixture.detectChanges();
    expect(fills('CENTRAL')).toBe('rgb(168, 197, 229)');
    expect(fills('LEFT')).toBe('transparent');
  });
  it('rejects assets without the required aligned base and overlay layers', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response('<svg data-anatomy-view="FRONT"/>', { status: 200 }))));
    await TestBed.configureTestingModule({ imports: [BodyMapComponent] }).compileComponents();
    const fixture = TestBed.createComponent(BodyMapComponent); fixture.detectChanges(); await new Promise((resolve) => setTimeout(resolve)); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nie udało się wczytać sylwetki mapy.');
  });
});
