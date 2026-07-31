import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import type { Facet, Preview, Result, SearchRequest } from '../../api/generated/src';
import { ApiFacade } from '../../core/api.facade';
import { catalogLabel } from '../catalog-labels';

export interface ExerciseSelection {
  exerciseId: string;
  exerciseVersionId: string;
  name: string;
  suggestedDoseType?: string;
  presentation: { summary?: string; technicalLevel?: string; movementPatterns: string[]; equipment: string[]; mediaReference?: string };
}

type FilterState = Record<string, string[]>;

@Component({
  selector: 'app-exercise-picker',
  imports: [ReactiveFormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './exercise-picker.component.scss',
  template: `<section class="picker" aria-labelledby="exercise-picker-title" [attr.aria-busy]="loading()">
    <header><h2 id="exercise-picker-title">Wyszukaj ćwiczenie</h2><p>Wyniki obejmują tylko opublikowane, możliwe do użycia wersje ćwiczeń.</p></header>
    <div class="search-row"><mat-form-field class="query"><mat-label>Szukaj ćwiczenia</mat-label><input #queryInput matInput type="search" [formControl]="query" placeholder="Szukaj po nazwie, regionie lub sprzęcie" autocomplete="off"><button matSuffix mat-icon-button type="button" aria-label="Wyczyść wyszukiwaną frazę" (click)="clearQuery()" [disabled]="!query.value">×</button></mat-form-field><button mat-stroked-button type="button" (click)="reset()" [disabled]="!hasFilters()">Wyczyść filtry</button></div>
    <p class="sr-only" aria-live="polite">{{ announcement() }}</p>
    @if (facets().length) { <section class="facets" aria-label="Filtry wyników"><h3>Filtry @if (activeFilterCount()) {<span>({{ activeFilterCount() }})</span>}</h3>
      @for (group of facetGroups(); track group.name) {<fieldset><legend>{{ group.label }}</legend><div class="facet-options">@for (facet of group.values; track facet.group + ':' + facet.value) {<mat-checkbox [checked]="facet.active" (change)="toggleFacet(facet)">{{ facetLabel(facet) }} <span class="count">({{ facet.count ?? 0 }})</span></mat-checkbox>}</div></fieldset>}
    </section> }
    @if (loading() && !results().length) {
      <p role="status">Ładowanie ćwiczeń…</p>
    } @else if (error()) {
      <section class="error" role="alert"><p>Nie udało się pobrać ćwiczeń.</p><button mat-stroked-button type="button" (click)="search()">Spróbuj ponownie</button></section>
    } @else if (!results().length) {
      <section class="empty-state"><h3>{{ query.value.trim() ? 'Nie znaleziono ćwiczeń dla tej frazy.' : 'Katalog nie zawiera ćwiczeń spełniających wybrane kryteria.' }}</h3><p>Spróbuj zmienić wyszukiwaną frazę lub filtry.</p>@if (hasFilters()) {<button mat-stroked-button type="button" (click)="reset()">Wyczyść filtry</button>}</section>
    } @else {
      <p class="result-count" aria-live="polite">{{ results().length }}{{ hasMore() ? '+' : '' }} wyników</p>
      <ul class="results" aria-label="Wyniki wyszukiwania">
        @for (result of results(); track result.exerciseVersionId) {
          <li><article><div class="result-copy"><h3>{{ result.title || 'Ćwiczenie bez nazwy' }}</h3>@if (result.summary) {<p>{{ result.summary }}</p>}<p class="meta">{{ resultMeta(result) }}</p></div><div class="actions"><a mat-button [routerLink]="['/catalog', result.exerciseVersionId]">Szczegóły</a><button mat-button type="button" (click)="openPreview(result, $event.currentTarget)">Podgląd</button>@if (selectionEnabled() && result.selectable && result.exerciseId && result.exerciseVersionId) {<button mat-flat-button type="button" (click)="select(result)">Wybierz</button>}</div></article></li>
        }
      </ul>
      @if (hasMore()) {<button class="load-more" mat-stroked-button type="button" (click)="loadMore()" [disabled]="loading()">{{ loading() ? 'Ładowanie…' : 'Pokaż więcej' }}</button>}
    }
    @if (preview(); as item) {<section class="preview-backdrop" (click)="closePreview()"><section #previewDialog class="preview" role="dialog" aria-modal="true" aria-labelledby="preview-title" tabindex="-1" (click)="$event.stopPropagation()"><button class="close" type="button" aria-label="Zamknij podgląd" (click)="closePreview()">×</button><h2 id="preview-title">{{ item.title || 'Podgląd ćwiczenia' }}</h2>@if (item.mediaReference) {<img [src]="item.mediaReference" alt=""/>}@else {<p class="media-empty">Brak materiału wizualnego.</p>}@if (item.instruction) {<p>{{ item.instruction }}</p>}<dl><dt>Poziom</dt><dd>{{ catalogLabel(item.technicalLevel) }}</dd><dt>Wzorce ruchowe</dt><dd>{{ labels(item.movementPatterns) }}</dd><dt>Sprzęt</dt><dd>{{ item.requiredEquipment?.join(', ') || 'Brak' }}</dd><dt>Typ ćwiczenia</dt><dd>{{ catalogLabel(item.exerciseType) }}</dd></dl>@if (selectionEnabled() && previewResult()?.selectable) {<button mat-flat-button type="button" (click)="select(previewResult()!)">Wybierz ćwiczenie</button>}</section></section>}
  </section>`
})
export class ExercisePickerComponent {
  readonly selectionEnabled = input(false);
  readonly selected = output<ExerciseSelection>();
  readonly query = new FormControl('', { nonNullable: true });
  readonly results = signal<Result[]>([]); readonly facets = signal<Facet[]>([]); readonly hasMore = signal(false); readonly cursor = signal<string | undefined>(undefined);
  readonly loading = signal(true); readonly error = signal(false); readonly preview = signal<Preview | undefined>(undefined); readonly previewResult = signal<Result | undefined>(undefined); readonly announcement = signal('Ładowanie wyników.');
  private readonly filters = signal<FilterState>({}); private readonly api = inject(ApiFacade).catalogSearch; private readonly destroyRef = inject(DestroyRef); private readonly previewDialog = viewChild<ElementRef<HTMLElement>>('previewDialog'); private previewOpener?: HTMLElement;
  constructor() { this.query.valueChanges.pipe(debounceTime(250), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.search()); this.search(); }
  readonly catalogLabel = catalogLabel;
  facetGroups() { const grouped = new Map<string, Facet[]>(); for (const facet of this.facets()) { const group = facet.group || 'OTHER'; grouped.set(group, [...(grouped.get(group) || []), facet]); } return [...grouped.entries()].map(([name, values]) => ({name, label: this.groupLabel(name), values})); }
  activeFilterCount() { return Object.values(this.filters()).reduce((total, values) => total + values.length, 0); }
  hasFilters() { return !!this.query.value.trim() || this.activeFilterCount() > 0; }
  clearQuery() { this.query.setValue(''); }
  reset() { this.filters.set({}); this.query.setValue('', {emitEvent: false}); this.search(); }
  toggleFacet(facet: Facet) { const group = facet.group; const value = facet.value; if (!group || !value) return; const current = this.filters(); const values = current[group] || []; this.filters.set({...current, [group]: values.includes(value) ? values.filter(item => item !== value) : [...values, value]}); this.search(); }
  search() { this.request(undefined, false); }
  loadMore() { if (this.cursor() && !this.loading()) this.request(this.cursor(), true); }
  openPreview(result: Result, opener: EventTarget | null) { if (!result.exerciseVersionId) return; this.previewOpener = opener instanceof HTMLElement ? opener : undefined; this.previewResult.set(result); this.api.preview({exerciseVersionId: result.exerciseVersionId}).then(value => { this.preview.set(value); queueMicrotask(() => this.previewDialog()?.nativeElement.focus()); }).catch(() => { this.announcement.set('Nie udało się pobrać podglądu ćwiczenia.'); }); }
  closePreview() { this.preview.set(undefined); this.previewResult.set(undefined); queueMicrotask(() => this.previewOpener?.focus()); }
  select(result: Result) { if (!result.exerciseId || !result.exerciseVersionId) return; this.selected.emit({exerciseId: result.exerciseId, exerciseVersionId: result.exerciseVersionId, name: result.title || 'Ćwiczenie', suggestedDoseType: result.exerciseType, presentation: {summary: result.summary, technicalLevel: result.technicalLevel, movementPatterns: result.movementPatterns || [], equipment: result.equipment || [], mediaReference: result.mediaReference}}); this.closePreview(); }
  labels(values?: string[]) { return values?.length ? values.map(catalogLabel).join(', ') : '—'; }
  resultMeta(result: Result) { return [this.labels(result.movementPatterns), catalogLabel(result.technicalLevel), result.equipment?.join(', ')].filter(Boolean).join(' · '); }
  facetLabel(facet: Facet) { return facet.labelKey ? catalogLabel(facet.labelKey) : catalogLabel(facet.value); }
  private request(cursor: string | undefined, append: boolean) { this.loading.set(true); this.error.set(false); const request = this.requestFor(cursor); this.api.search({searchRequest: request}).then(page => { const incoming = page.results || []; const existing = append ? this.results() : []; const seen = new Set(existing.map(item => item.exerciseVersionId)); this.results.set([...existing, ...incoming.filter(item => !!item.exerciseVersionId && !seen.has(item.exerciseVersionId))]); this.facets.set(page.facets || []); this.cursor.set(page.nextCursor); this.hasMore.set(!!page.hasMore); this.announcement.set(`${this.results().length} wyników wyszukiwania.`); }).catch(() => { this.error.set(true); this.announcement.set('Nie udało się pobrać wyników.'); }).finally(() => this.loading.set(false)); }
  private requestFor(cursor?: string): SearchRequest { const filters = this.filters(); return {query: this.query.value.trim() || undefined, locale: 'pl-PL', movementPatterns: filters['MOVEMENT_PATTERN'], technicalLevels: filters['TECHNICAL_LEVEL'], equipment: filters['EQUIPMENT'], positionCodes: filters['POSITION'], anatomyStructureIds: filters['ANATOMY_STRUCTURE'], anatomyStructureTypes: filters['ANATOMY_STRUCTURE_TYPE'], purposes: filters['PURPOSE'], unilateral: filters['UNILATERAL']?.[0] === 'true' ? true : filters['UNILATERAL']?.[0] === 'false' ? false : undefined, sort: 'RELEVANCE', limit: 20, cursor}; }
  private groupLabel(group: string) { return ({MOVEMENT_PATTERN: 'Wzorzec ruchu', TECHNICAL_LEVEL: 'Poziom', EQUIPMENT: 'Sprzęt', POSITION: 'Pozycja', UNILATERAL: 'Stronność', ANATOMY_STRUCTURE: 'Struktura anatomiczna', ANATOMY_STRUCTURE_TYPE: 'Typ struktury', PURPOSE: 'Zastosowanie'} as Record<string, string>)[group] || 'Pozostałe'; }
}
