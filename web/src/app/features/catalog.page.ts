import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { Title } from '@angular/platform-browser';
import { switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiFacade } from '../core/api.facade';
import { EQUIPMENT_OPTIONS, MOVEMENT_PATTERNS, TECHNICAL_LEVELS, catalogLabel } from './catalog-labels';

interface CatalogState { query: string; movementPattern: string; technicalLevel: string; equipment: string; page: number; size: number; invalid: boolean; }
const asEnum = (value: string | null, values: readonly string[]) => value && values.includes(value) ? value : '';
const asPage = (value: string | null) => value && /^\d+$/.test(value) ? Number(value) : 0;

@Component({
  selector: 'app-catalog-page', imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatPaginatorModule], changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './catalog.page.scss',
  template: `<main class="catalog panel"><h1 tabindex="-1">Katalog ćwiczeń</h1><p>Wyświetlane są wyłącznie opublikowane wersje.</p>
  <form class="filters" (ngSubmit)="apply()" aria-label="Filtry katalogu">
    <mat-form-field><mat-label>Szukaj</mat-label><input matInput type="search" [formControl]="query"></mat-form-field>
    <mat-form-field><mat-label>Wzorzec ruchu</mat-label><mat-select [formControl]="pattern"><mat-option value="">Wszystkie</mat-option>@for (x of patterns; track x) {<mat-option [value]="x">{{label(x)}}</mat-option>}</mat-select></mat-form-field>
    <mat-form-field><mat-label>Poziom techniczny</mat-label><mat-select [formControl]="level"><mat-option value="">Wszystkie</mat-option>@for (x of levels; track x) {<mat-option [value]="x">{{label(x)}}</mat-option>}</mat-select></mat-form-field>
    <mat-form-field><mat-label>Sprzęt</mat-label><mat-select [formControl]="equipment"><mat-option value="">Dowolny</mat-option>@for (x of equipmentOptions; track x) {<mat-option [value]="x">{{x}}</mat-option>}</mat-select></mat-form-field>
    <div class="filter-actions"><button mat-flat-button type="submit">Filtruj</button><button mat-button type="button" (click)="reset()">Wyczyść filtry</button></div>
  </form>
  @if (invalid()) {<p class="notice" aria-live="polite">Nieprawidłowe parametry adresu zostały znormalizowane.</p>}
  @if (loading()) {<p aria-live="polite">Ładowanie wyników…</p>} @else if (error()) {<section class="error" role="alert"><p>Nie udało się pobrać katalogu. Spróbuj ponownie.</p><button mat-button (click)="retry()">Ponów</button></section>} @else {<p aria-live="polite">{{total()}} wyników.</p>
    @if (items().length) {<ul class="card-list">@for (x of items(); track x.versionId) {<li><a [routerLink]="['/catalog', x.versionId]" [queryParams]="returnParams()"><h2>{{x.canonicalName}}</h2><p>{{label(x.primaryMovementPattern)}} · {{label(x.technicalLevel)}} · {{label(x.environment)}}</p><span>Opublikowana wersja {{x.versionNumber}}</span></a></li>}</ul>
    <mat-paginator [length]="total()" [pageIndex]="state().page" [pageSize]="state().size" [pageSizeOptions]="[12, 24, 48]" aria-label="Strony wyników" (page)="paginate($event)"/>} @else {<p>Brak ćwiczeń spełniających kryteria.</p>}}
  </main>`
})
export class CatalogPage {
  private readonly api = inject(ApiFacade).catalog; private readonly route = inject(ActivatedRoute); private readonly router = inject(Router); private readonly destroyRef = inject(DestroyRef);
  readonly query = new FormControl('', { nonNullable: true }); readonly pattern = new FormControl('', { nonNullable: true }); readonly level = new FormControl('', { nonNullable: true }); readonly equipment = new FormControl('', { nonNullable: true });
  readonly patterns = MOVEMENT_PATTERNS; readonly levels = TECHNICAL_LEVELS; readonly equipmentOptions = EQUIPMENT_OPTIONS; readonly items = signal<any[]>([]); readonly total = signal(0); readonly loading = signal(true); readonly error = signal(false); readonly invalid = signal(false); readonly state = signal<CatalogState>({query:'', movementPattern:'', technicalLevel:'', equipment:'', page:0, size:12, invalid:false});
  constructor() { inject(Title).setTitle('Katalog ćwiczeń | Moves'); this.route.queryParamMap.pipe(switchMap(params => { const next = this.read(params); this.state.set(next); this.invalid.set(next.invalid); this.query.setValue(next.query, {emitEvent:false}); this.pattern.setValue(next.movementPattern, {emitEvent:false}); this.level.setValue(next.technicalLevel, {emitEvent:false}); this.equipment.setValue(next.equipment, {emitEvent:false}); this.loading.set(true); this.error.set(false); return this.api.list({query:next.query || undefined, movementPattern:next.movementPattern as any || undefined, technicalLevel:next.technicalLevel as any || undefined, equipment:next.equipment || undefined, page:next.page, size:next.size}); }), takeUntilDestroyed(this.destroyRef)).subscribe({ next: page => { this.items.set(page.content ?? []); this.total.set(page.totalElements ?? 0); this.loading.set(false); }, error: () => { this.error.set(true); this.loading.set(false); }}); }
  label = catalogLabel;
  apply() { this.navigate({...this.state(), query:this.query.value.trim(), movementPattern:this.pattern.value, technicalLevel:this.level.value, equipment:this.equipment.value, page:0}); }
  reset() { this.query.setValue(''); this.pattern.setValue(''); this.level.setValue(''); this.equipment.setValue(''); this.navigate({query:'', movementPattern:'', technicalLevel:'', equipment:'', page:0, size:this.state().size, invalid:false}); }
  paginate(event: PageEvent) { this.navigate({...this.state(), page:event.pageIndex, size:event.pageSize}); }
  retry() { this.navigate({...this.state()}); }
  returnParams() { const s=this.state(); return {query:s.query || null, movementPattern:s.movementPattern || null, technicalLevel:s.technicalLevel || null, equipment:s.equipment || null, page:s.page || null, size:s.size === 12 ? null : s.size}; }
  private navigate(s: CatalogState) { void this.router.navigate([], {relativeTo:this.route, queryParams:this.params(s)}); }
  private params(s: CatalogState) { return {query:s.query || null, movementPattern:s.movementPattern || null, technicalLevel:s.technicalLevel || null, equipment:s.equipment || null, page:s.page || null, size:s.size === 12 ? null : s.size}; }
  private read(p: any): CatalogState { const rawPattern=p.get('movementPattern'), rawLevel=p.get('technicalLevel'), page=asPage(p.get('page')), size=[12,24,48].includes(asPage(p.get('size'))) ? asPage(p.get('size')) : 12; const pattern=asEnum(rawPattern, MOVEMENT_PATTERNS), level=asEnum(rawLevel, TECHNICAL_LEVELS); return {query:(p.get('query') ?? '').slice(0,160), movementPattern:pattern, technicalLevel:level, equipment:(p.get('equipment') ?? '').slice(0,80), page, size, invalid:(!!rawPattern && !pattern) || (!!rawLevel && !level) || (p.get('page') !== null && page === 0 && p.get('page') !== '0')}; }
}
