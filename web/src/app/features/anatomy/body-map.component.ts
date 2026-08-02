import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
  ViewEncapsulation,
  inject,
} from '@angular/core';
import type {
  AnatomyAnalysisView,
  VisualRegionExposure,
  VisualRegionExposureChannelEnum,
  VisualRegionExposureViewEnum,
} from '../../api/generated/src';

type AnatomyView = Extract<VisualRegionExposureViewEnum, 'FRONT' | 'BACK'>;
type AnatomyAsset = { view: AnatomyView; svg: SVGSVGElement; geometryCodes: string[] };

/** Renders one complete silhouette asset and its semantic exposure overlay. */
@Component({
  selector: 'app-body-map',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  styleUrl: './body-map.component.scss',
  template: `<section class="body-map" aria-labelledby="body-map-heading">
    <h3 id="body-map-heading">Zaangażowane obszary ciała</h3>
    @if (loading) { <p role="status">Ładowanie ekspozycji mapy…</p> }
    @else if (error) { <p class="error" role="alert">Nie udało się pobrać ekspozycji mapy.</p> }
    @else if (!analysis?.visualRegionExposures?.length) { <p role="status">Dodaj ćwiczenia, aby zobaczyć, które obszary ciała obejmuje zestaw.</p> }
    @else if (unavailable) { <p role="status">Mapa ekspozycji jest niedostępna.</p> }
    @else if (partial) { <p class="warning" role="status">Część danych ekspozycji może być niedostępna.</p> }
    <div class="map-controls" role="group" aria-label="Widok sylwetki">
      <div class="view-switch">
        <button type="button" [attr.aria-pressed]="view === 'FRONT'" (click)="setView('FRONT')">Przód</button>
        <button type="button" [attr.aria-pressed]="view === 'BACK'" (click)="setView('BACK')">Tył</button>
      </div>
    </div>
    @if (assetError) {
      <p class="error" role="alert">Nie udało się wczytać sylwetki mapy.</p>
      <button type="button" (click)="retryAsset()">Spróbuj ponownie</button>
    } @else {
      <p class="map-hint" aria-live="polite">{{ hoverText || 'Wybierz wyróżniony obszar, aby zobaczyć szczegóły.' }}</p>
    }
    <div #svgHost class="svg-host" aria-label="Interaktywna mapa ciała"></div>
    <ul class="map-legend" aria-label="Legenda poziomów ekspozycji">
      @for (band of legendBands; track band) { <li><span class="legend-swatch" [style.background-color]="bandColor(band)"></span>{{ bandLabel(band) }}</li> }
    </ul>
    @if (activeExposures.length) { <section class="active-regions" aria-label="Aktywne obszary"><h4>Aktywne obszary</h4><ul>
      @for (exposure of activeExposures.slice(0, 4); track exposureKey(exposure)) { <li><button type="button" [class.is-selected]="isSelected(exposure)" (click)="select(exposure, $event.currentTarget)"><span>{{ regionName(exposure) }}</span><small>{{ bandLabel(exposure.concentrationBand) }}</small></button></li> }
    </ul>@if (activeExposures.length > 4) { <a href="#region-list-heading">Pełne zestawienie ({{ activeExposures.length }})</a> }</section> }
    @if (selected) { <section class="region-detail" role="dialog" aria-modal="false" aria-labelledby="region-detail-heading" tabindex="-1" #detail>
      <header><h4 id="region-detail-heading">{{ regionName(selected) }}</h4><button type="button" (click)="closeDetail()">Zamknij szczegóły</button></header>
      <p><strong>{{ bandLabel(selected.concentrationBand) }}</strong> · udział w kanale: {{ roundedShare(selected) }}%</p>
    </section> }
  </section>`,
})
export class BodyMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() analysis?: AnatomyAnalysisView;
  @Input() loading = false;
  @Input() error = false;
  @Input() selectedRegionCode?: string;
  @Output() selectedRegionCodeChange = new EventEmitter<string | undefined>();
  @Output() geometryCodesChange = new EventEmitter<string[]>();
  @ViewChild('svgHost') svgHost?: ElementRef<HTMLElement>;
  @ViewChild('detail') detail?: ElementRef<HTMLElement>;
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly zone = inject(NgZone);
  view: AnatomyView = 'FRONT';
  selected?: VisualRegionExposure;
  hoverText = '';
  assetError = false;
  private assets?: Map<AnatomyView, AnatomyAsset>;
  private assetPromise?: Promise<Map<AnatomyView, AnatomyAsset>>;
  private lastFocus?: { focus: () => void };
  readonly legendBands: VisualRegionExposure['concentrationBand'][] = ['NO_DATA', 'LOW', 'SIGNIFICANT', 'DOMINANT'];

  get unavailable() { return this.analysis?.visualMappingCompleteness === 'UNAVAILABLE' || this.analysis?.visualMappingVersion === 'UNAVAILABLE'; }
  get partial() { return this.analysis?.visualMappingCompleteness === 'PARTIAL' || this.analysis?.completeness === 'PARTIAL'; }
  get visibleExposures() { return (this.analysis?.visualRegionExposures || []).filter((value) => value.view === this.view); }
  get activeExposures() {
    return this.visibleExposures.filter((exposure, index, exposures) =>
      exposures.findIndex((candidate) => this.exposureKey(candidate) === this.exposureKey(exposure)) === index,
    );
  }
  ngOnChanges(changes: SimpleChanges) {
    if (changes['analysis'] || changes['selectedRegionCode']) {
      this.selected = this.analysis?.visualRegionExposures?.find((value) => value.visualRegionCode === this.selectedRegionCode);
      void this.refreshSvg();
    }
  }
  ngAfterViewInit() { void this.refreshSvg(); }
  ngOnDestroy() { this.svgHost?.nativeElement.replaceChildren(); }
  setView(view: AnatomyView) { this.view = view; this.hoverText = ''; void this.refreshSvg(); this.cdr.markForCheck(); }
  retryAsset() { this.assetError = false; this.assets = undefined; this.assetPromise = undefined; void this.refreshSvg(); }
  hasGeometry(code: string) { return Boolean(this.assets && [...this.assets.values()].some((asset) => asset.geometryCodes.includes(code))); }
  exposureKey(value: VisualRegionExposure) { return `${value.visualRegionCode}:${value.laterality}`; }
  isSelected(value: VisualRegionExposure) { return this.selected === value || (this.selected?.visualRegionCode === value.visualRegionCode && this.selected.laterality === value.laterality); }
  regionName(value: VisualRegionExposure) { return value.displayName?.trim() || 'Obszar ciała'; }
  roundedShare(value: VisualRegionExposure) { return Number(value.shareWithinChannel || 0).toFixed(1).replace('.0', ''); }
  bandLabel(value: VisualRegionExposure['concentrationBand']) { return BAND_PRESENTATION[value].label; }
  bandColor(value: VisualRegionExposure['concentrationBand']) { return BAND_PRESENTATION[value].color; }
  select(exposure: VisualRegionExposure, target?: EventTarget | null) {
    this.lastFocus = target && 'focus' in target ? target as { focus: () => void } : undefined;
    this.selected = exposure;
    this.selectedRegionCodeChange.emit(exposure.visualRegionCode);
    this.cdr.markForCheck();
    queueMicrotask(() => this.detail?.nativeElement.focus());
    this.decorateSvg();
  }
  closeDetail() { this.selected = undefined; this.selectedRegionCodeChange.emit(undefined); this.cdr.markForCheck(); this.lastFocus?.focus(); this.decorateSvg(); }
  private async refreshSvg() {
    if (!this.svgHost) return;
    try {
      const assets = await this.loadAssets();
      const asset = assets.get(this.view);
      if (!asset) throw new Error('Missing selected anatomy view');
      this.svgHost.nativeElement.replaceChildren(document.importNode(asset.svg, true));
      this.assetError = false;
      this.decorateSvg();
      this.cdr.markForCheck();
    } catch {
      this.assetError = true;
      this.svgHost.nativeElement.replaceChildren();
      this.cdr.markForCheck();
    }
  }
  private loadAssets() {
    if (this.assets) return Promise.resolve(this.assets);
    if (!this.assetPromise) this.assetPromise = Promise.all((['FRONT', 'BACK'] as const).map((view) => this.fetchAsset(view))).then((assets) => {
      this.assets = new Map(assets.map((asset) => [asset.view, asset]));
      this.geometryCodesChange.emit([...new Set(assets.flatMap((asset) => asset.geometryCodes))]);
      return this.assets;
    });
    return this.assetPromise;
  }
  private async fetchAsset(view: AnatomyView): Promise<AnatomyAsset> {
    const response = await fetch(`/assets/anatomy/anatomy-body-${view.toLowerCase()}-v1.svg`, { credentials: 'same-origin' });
    if (!response.ok) throw new Error(`Unable to load SVG: ${response.status}`);
    const parsed = new DOMParser().parseFromString(await response.text(), 'image/svg+xml');
    const svg = parsed.documentElement as unknown as SVGSVGElement;
    if (svg.nodeName.toLowerCase() !== 'svg' || parsed.querySelector('parsererror') || svg.dataset['anatomyView'] !== view) throw new Error('Invalid anatomy SVG asset');
    svg.querySelectorAll('script, foreignObject').forEach((node) => node.remove());
    svg.querySelectorAll<HTMLElement>('*').forEach((node) => [...node.attributes].forEach((attribute) => {
      if (attribute.name.toLowerCase().startsWith('on') || (attribute.name.endsWith('href') && /^(https?:|javascript:)/i.test(attribute.value))) node.removeAttribute(attribute.name);
    }));
    const base = svg.querySelector<SVGGElement>('g[data-layer="base-silhouette"]');
    const overlay = svg.querySelector<SVGGElement>('g[data-layer="exposure-overlay"]');
    if (!base || !overlay || base.dataset['view'] !== view || overlay.dataset['view'] !== view) throw new Error('Invalid anatomy SVG layers');
    const codes = [...overlay.querySelectorAll<SVGGElement>('g[data-visual-region-code]')].map((group) => group.dataset['visualRegionCode']).filter((code): code is string => Boolean(code));
    return { view, svg, geometryCodes: codes };
  }
  private decorateSvg() {
    const host = this.svgHost?.nativeElement;
    if (!host) return;
    const base = host.querySelector<SVGGElement>('g[data-layer="base-silhouette"]');
    base?.setAttribute('aria-hidden', 'true');
    base?.setAttribute('pointer-events', 'none');
    base?.querySelectorAll<SVGElement>('[data-anatomy-geometry="base"]').forEach((element) => {
      element.style.setProperty('fill', BAND_PRESENTATION.NO_DATA.color, 'important');
      element.style.setProperty('pointer-events', 'none', 'important');
    });
    host.querySelectorAll<SVGGElement>('g[data-layer="exposure-overlay"] > g[data-visual-region-code]').forEach((group) => {
      const code = group.dataset['visualRegionCode'];
      if (!code) return;
      const exposure = this.exposureForGeometry(code, group.dataset['laterality']);
      const interactiveGeometry = group.querySelectorAll<SVGElement>('[data-anatomy-geometry="exposure"]');
      group.classList.toggle('map-region', Boolean(exposure));
      group.classList.toggle('is-selected', Boolean(exposure && this.isSelected(exposure)));
      interactiveGeometry.forEach((element) => element.style.setProperty('fill', exposure ? this.bandColor(exposure.concentrationBand) : 'transparent', 'important'));
      if (!exposure) {
        group.setAttribute('aria-hidden', 'true');
        group.setAttribute('pointer-events', 'none');
        group.removeAttribute('role'); group.removeAttribute('tabindex'); group.removeAttribute('aria-label'); group.removeAttribute('aria-pressed');
        group.onclick = null; group.onkeydown = null; group.onmouseenter = null; group.onfocus = null;
        return;
      }
      group.removeAttribute('aria-hidden');
      group.removeAttribute('pointer-events');
      group.setAttribute('role', 'button');
      group.setAttribute('tabindex', '0');
      group.setAttribute('aria-pressed', String(this.selected?.visualRegionCode === code));
      group.setAttribute('aria-label', `${this.regionName(exposure)}: ${this.bandLabel(exposure.concentrationBand)}`);
      const updateHover = () => this.zone.run(() => { this.hoverText = `${this.regionName(exposure)}: ${this.bandLabel(exposure.concentrationBand)}`; this.cdr.markForCheck(); });
      group.onmouseenter = updateHover;
      group.onfocus = updateHover;
      group.onclick = (event) => this.zone.run(() => this.select(exposure, event.currentTarget));
      group.onkeydown = (event) => { if (event.key === 'Enter' || event.key === ' ') this.zone.run(() => { event.preventDefault(); this.select(exposure, event.currentTarget); }); };
    });
  }
  private exposureForGeometry(code: string, geometryLaterality?: string) {
    const candidates = this.visibleExposures.filter((exposure) => exposure.visualRegionCode === code);
    if (!geometryLaterality) return this.lastExposure(candidates, 'CENTRAL');
    if (geometryLaterality === 'CENTRAL') return this.lastExposure(candidates, 'CENTRAL');
    if (geometryLaterality !== 'LEFT' && geometryLaterality !== 'RIGHT') return undefined;
    return this.lastExposure(candidates, geometryLaterality) ?? this.lastExposure(candidates, 'BILATERAL');
  }
  private lastExposure(candidates: VisualRegionExposure[], laterality: VisualRegionExposure['laterality']) {
    for (let index = candidates.length - 1; index >= 0; index -= 1) {
      if (candidates[index].laterality === laterality) return candidates[index];
    }
    return undefined;
  }
}

const BAND_PRESENTATION: Record<VisualRegionExposure['concentrationBand'], { label: string; color: string }> = {
  NO_DATA: { label: 'Brak danych', color: '#e4e7eb' }, LOW: { label: 'Niski', color: '#a8c5e5' }, SIGNIFICANT: { label: 'Istotny', color: '#4e91d1' }, DOMINANT: { label: 'Dominujący', color: '#1261a0' },
};
