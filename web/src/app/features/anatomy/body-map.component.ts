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

/** Accessible renderer for the deliberately partial, generated SVG geometry. */
@Component({
  selector: 'app-body-map',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  styleUrl: './body-map.component.scss',
  template: `<section class="body-map" aria-labelledby="body-map-heading">
    <h3 id="body-map-heading">Mapa ekspozycji</h3>
    <p class="partial-notice">Mapa pomaga przejrzeć rozkład ekspozycji w zestawie.</p>
    @if (loading) {
      <p role="status">Ładowanie ekspozycji mapy…</p>
    } @else if (error) {
      <p class="error" role="alert">Nie udało się pobrać ekspozycji mapy.</p>
    } @else if (!analysis || unavailable) {
      <p role="status">Mapa ekspozycji jest niedostępna.</p>
    } @else {
      @if (partial) {
        <p class="warning" role="status">Część danych ekspozycji może być niedostępna.</p>
      }
      <div class="map-controls">
        <fieldset>
          <legend>Widok sylwetki</legend>
          <button type="button" [attr.aria-pressed]="view === 'FRONT'" (click)="setView('FRONT')">
            Przód</button
          ><button type="button" [attr.aria-pressed]="view === 'BACK'" (click)="setView('BACK')">
            Tył
          </button>
        </fieldset>
        <label
          >Kanał
          <select [value]="channel" (change)="setChannel($any($event.target).value)">
            <option value="">Wszystkie kanały</option>
            @for (value of channels; track value) {
              <option [value]="value">{{ value }}</option>
            }
          </select></label
        >
      </div>
      <p class="map-hint" aria-live="polite">
        {{ hoverText || 'Wybierz region na mapie lub z listy.' }}
      </p>
      <div #svgHost class="svg-host" aria-label="Interaktywna mapa ciała"></div>
      <h4>Pełne zestawienie regionów</h4>
      <ul class="region-list">
        @for (exposure of visibleExposures; track exposureKey(exposure)) {
          <li>
            <button type="button" (click)="select(exposure, $event.currentTarget)">
              <strong>{{ exposure.visualRegionCode }}</strong> —
              {{ bandLabel(exposure.concentrationBand) }} · {{ exposure.rawValue }}
              {{ exposure.unit }} · {{ exposure.shareWithinChannel }}%
            </button>
            @if (!hasGeometry(exposure.visualRegionCode)) {
              <span class="no-geometry">Brak geometrii w mapie V1</span>
            }
          </li>
        }
      </ul>
    }
    @if (selected) {
      <section
        class="region-detail"
        role="dialog"
        aria-modal="false"
        aria-labelledby="region-detail-heading"
        tabindex="-1"
        #detail
      >
        <header>
          <h4 id="region-detail-heading">{{ selected.visualRegionCode }}</h4>
          <button type="button" (click)="closeDetail()">Zamknij szczegóły</button>
        </header>
        <p>
          <strong>{{ bandLabel(selected.concentrationBand) }}</strong> · {{ selected.rawValue }}
          {{ selected.unit }} · udział w kanale: {{ selected.shareWithinChannel }}%
        </p>
        <p>
          Widok: {{ selected.view }} · strona: {{ selected.laterality }} · warstwa:
          {{ selected.layer }}
        </p>
        <h5>Struktury źródłowe</h5>
        <ul>
          @for (structure of selected.sourceStructures; track structure.anatomicalStructureId) {
            <li>
              {{
                structure.anatomicalStructureCode || structure.anatomicalStructureId || 'Brak kodu'
              }}
              @if (structure.anatomicalStructureType) {
                · {{ structure.anatomicalStructureType }}
              }
            </li>
          }
        </ul>
        <h5>Ćwiczenia, dawkowanie i rozbicie</h5>
        <ul>
          @for (item of selected.breakdowns; track item.contributionId) {
            <li>
              {{ item.itemId || 'Brak pozycji' }} ·
              {{ item.exerciseVersionId || 'Brak ćwiczenia' }} · {{ item.role || 'Brak roli' }} ·
              {{ item.rawValue ?? 'Brak wartości' }}
              @if (item.evidence?.length) {
                <ul>
                  @for (evidence of item.evidence; track evidence.id) {
                    <li>{{ evidence.citation || evidence.sourceUri || 'Brak opisu dowodu' }}</li>
                  }
                </ul>
              }
            </li>
          }
        </ul>
      </section>
    }
  </section>`,
})
export class BodyMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() analysis?: AnatomyAnalysisView;
  @Input() loading = false;
  @Input() error = false;
  @Input() selectedRegionCode?: string;
  @Output() selectedRegionCodeChange = new EventEmitter<string | undefined>();
  @ViewChild('svgHost') svgHost?: ElementRef<HTMLElement>;
  @ViewChild('detail') detail?: ElementRef<HTMLElement>;
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly zone = inject(NgZone);
  view: VisualRegionExposureViewEnum = 'FRONT';
  channel = '';
  selected?: VisualRegionExposure;
  hoverText = '';
  private geometryCodes = new Set<string>();
  private lastFocus?: { focus: () => void };
  private loaded = false;
  get unavailable() {
    return (
      this.analysis?.visualMappingCompleteness === 'UNAVAILABLE' ||
      this.analysis?.visualMappingVersion === 'UNAVAILABLE'
    );
  }
  get partial() {
    return (
      this.analysis?.visualMappingCompleteness === 'PARTIAL' ||
      this.analysis?.completeness === 'PARTIAL'
    );
  }
  get channels(): VisualRegionExposureChannelEnum[] {
    return [...new Set((this.analysis?.visualRegionExposures || []).map((value) => value.channel))];
  }
  get visibleExposures() {
    return (this.analysis?.visualRegionExposures || []).filter(
      (value) => (!this.channel || value.channel === this.channel) && value.view === this.view,
    );
  }
  ngOnChanges(changes: SimpleChanges) {
    if (changes['analysis']) {
      this.selected = this.analysis?.visualRegionExposures.find(
        (value) => value.visualRegionCode === this.selectedRegionCode,
      );
      this.refreshSvg();
    }
  }
  ngOnDestroy() {
    this.svgHost?.nativeElement.replaceChildren();
  }
  ngAfterViewInit() {
    void this.refreshSvg();
  }
  setView(view: VisualRegionExposureViewEnum) {
    this.view = view;
    this.refreshSvg();
    this.cdr.markForCheck();
  }
  setChannel(channel: string) {
    this.channel = channel;
    this.refreshSvg();
    this.cdr.markForCheck();
  }
  exposureKey(value: VisualRegionExposure) {
    return `${value.visualRegionCode}:${value.channel}:${value.laterality}:${value.view}:${value.layer}`;
  }
  hasGeometry(code: string) {
    return this.geometryCodes.has(code);
  }
  bandLabel(value: VisualRegionExposure['concentrationBand']) {
    return (
      {
        NO_DATA: 'Brak danych',
        LOW: 'Niski',
        SIGNIFICANT: 'Istotny',
        DOMINANT: 'Dominujący',
      } as const
    )[value];
  }
  select(exposure: VisualRegionExposure, target?: EventTarget | null) {
    this.lastFocus = target && 'focus' in target ? (target as { focus: () => void }) : undefined;
    this.selected = exposure;
    this.selectedRegionCodeChange.emit(exposure.visualRegionCode);
    this.cdr.markForCheck();
    queueMicrotask(() => this.detail?.nativeElement.focus());
    this.refreshSvg();
  }
  closeDetail() {
    this.selected = undefined;
    this.selectedRegionCodeChange.emit(undefined);
    this.cdr.markForCheck();
    this.lastFocus?.focus();
    this.refreshSvg();
  }
  private setHover(text: string) {
    this.hoverText = text;
    this.cdr.markForCheck();
  }
  private async refreshSvg() {
    if (!this.svgHost || this.loaded) {
      this.decorateSvg();
      return;
    }
    try {
      const response = await fetch('/assets/anatomy/anatomy-body-partial-v1.svg', {
        credentials: 'same-origin',
      });
      if (!response.ok) return;
      const parsed = new DOMParser().parseFromString(await response.text(), 'image/svg+xml');
      const svg = parsed.documentElement;
      if (svg.nodeName.toLowerCase() !== 'svg' || parsed.querySelector('parsererror')) return;
      svg.querySelectorAll('script, foreignObject').forEach((node) => node.remove());
      svg.querySelectorAll<HTMLElement>('*').forEach((node) =>
        [...node.attributes].forEach((attribute) => {
          if (
            attribute.name.toLowerCase().startsWith('on') ||
            (attribute.name.endsWith('href') && /^(https?:|javascript:)/i.test(attribute.value))
          )
            node.removeAttribute(attribute.name);
        }),
      );
      this.svgHost.nativeElement.replaceChildren(document.importNode(svg, true));
      this.loaded = true;
      this.decorateSvg();
      this.cdr.markForCheck();
    } catch {
      /* textual fallback remains available */
    }
  }
  private decorateSvg() {
    const host = this.svgHost?.nativeElement;
    if (!host) return;
    const byCode = new Map(
      this.visibleExposures.map((exposure) => [exposure.visualRegionCode, exposure]),
    );
    host.querySelectorAll<SVGGElement>('g[data-visual-region-code]').forEach((group) => {
      const code = group.dataset['visualRegionCode'];
      if (!code) return;
      this.geometryCodes.add(code);
      const exposure = byCode.get(code);
      const isView = group.dataset['view'] === this.view;
      group.classList.toggle('map-region', Boolean(exposure) && isView);
      group.classList.toggle('is-selected', this.selected?.visualRegionCode === code);
      group.setAttribute('aria-hidden', String(!isView));
      if (!exposure || !isView) {
        group.removeAttribute('role');
        group.removeAttribute('tabindex');
        group.removeAttribute('aria-label');
        group.removeAttribute('aria-pressed');
        return;
      }
      group.setAttribute('role', 'button');
      group.setAttribute('tabindex', '0');
      group.setAttribute('aria-pressed', String(this.selected?.visualRegionCode === code));
      group.setAttribute(
        'aria-label',
        `${code}: ${this.bandLabel(exposure.concentrationBand)}, ${exposure.rawValue} ${exposure.unit}`,
      );
      group.onmouseenter = () =>
        this.zone.run(() =>
          this.setHover(`${code}: ${this.bandLabel(exposure.concentrationBand)}`),
        );
      group.onfocus = () =>
        this.zone.run(() =>
          this.setHover(`${code}: ${this.bandLabel(exposure.concentrationBand)}`),
        );
      group.onclick = (event) => this.zone.run(() => this.select(exposure, event.currentTarget));
      group.onkeydown = (event) => {
        if (event.key === 'Enter' || event.key === ' ')
          this.zone.run(() => {
            event.preventDefault();
            this.select(exposure, event.currentTarget);
          });
      };
    });
  }
}
