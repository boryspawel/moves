import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import type { WorklistItemView } from '../api/generated/src';
import { ApiFacade } from '../core/api.facade';

@Component({
  selector: 'app-specialist-alerts-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <section class="panel"><h1>Elementy wymagające uwagi</h1>
      <div class="filters"><mat-form-field><mat-label>Rodzaj</mat-label><mat-select [formControl]="category" (selectionChange)="filter()"><mat-option value="">Wszystkie</mat-option>@for (value of categories; track value) { <mat-option [value]="value">{{ value }}</mat-option> }</mat-select></mat-form-field><mat-form-field><mat-label>Priorytet</mat-label><mat-select [formControl]="priority" (selectionChange)="filter()"><mat-option value="">Wszystkie</mat-option><mat-option value="HIGH">Wysoki</mat-option><mat-option value="MEDIUM">Średni</mat-option><mat-option value="LOW">Niski</mat-option></mat-select></mat-form-field></div>
      <p class="status" aria-live="polite" [class.error]="failed()">{{ message() }}</p>
      <ul class="card-list">@for (item of visible(); track item.id) { <li><h2>{{ item.category }} · {{ item.priority === 'HIGH' ? 'wysoki priorytet' : item.priority === 'MEDIUM' ? 'średni priorytet' : 'niski priorytet' }}</h2><p>{{ item.minimalData }}</p>@if (item.issueText) { <p><strong>Wiadomość uczestnika:</strong> {{ item.issueText }}</p> } @for (reply of item.replies ?? []; track reply.id) { <p><strong>Odpowiedź:</strong> {{ reply.shortText }}</p> }<div class="actions"><button mat-button (click)="act(item, 'ACKNOWLEDGE')">Potwierdź</button><button mat-button (click)="act(item, 'SNOOZE')">Odrocz</button><button mat-button (click)="act(item, 'RESOLVE')">Rozwiąż</button>@if (item.issueText) { <button mat-flat-button (click)="replyTo(item)">Odpowiedz</button> }</div></li> } @empty { <li>Brak elementów wymagających decyzji.</li> }</ul>
    </section>`, changeDetection: ChangeDetectionStrategy.OnPush
})
export class SpecialistAlertsPage {
  private readonly api = inject(ApiFacade).worklist;
  protected readonly items = signal<WorklistItemView[]>([]); protected readonly visible = signal<WorklistItemView[]>([]); protected readonly message = signal(''); protected readonly failed = signal(false);
  protected readonly category = new FormControl('', { nonNullable: true }); protected readonly priority = new FormControl('', { nonNullable: true });
  protected readonly categories = ['ESCALATING_SYMPTOMS', 'TECHNIQUE_UNCERTAINTY', 'REPEATED_BARRIERS', 'FAILED_ATTEMPTS', 'NO_RETURN_AFTER_GAP', 'PLAN_MISMATCH', 'POST_24H_FOLLOW_UP', 'PARTICIPANT_ISSUE'];
  constructor() { void this.load(); }
  protected async load(): Promise<void> { this.failed.set(false); try { this.items.set(await this.api.listWorklist({ actingContext: 'TRAINER', purpose: 'PERFORMANCE_PLANNING' })); this.filter(); this.message.set(`${this.visible().length} elementów wymaga uwagi.`); } catch { this.failed.set(true); this.message.set('Nie udało się pobrać worklisty.'); } }
  protected filter(): void { this.visible.set(this.items().filter(item => (!this.category.value || item.category === this.category.value) && (!this.priority.value || item.priority === this.priority.value))); }
  protected async act(item: WorklistItemView, action: 'ACKNOWLEDGE' | 'SNOOZE' | 'RESOLVE'): Promise<void> { if (!item.id) return; try { await this.api.actOnWorklist({ itemId: item.id, actingContext: 'TRAINER', purpose: 'PERFORMANCE_PLANNING', actionCommand: { action, snoozedUntil: action === 'SNOOZE' ? new Date(Date.now() + 86_400_000) : undefined, usefulnessOutcome: action === 'RESOLVE' ? 'reviewed' : undefined } }); await this.load(); } catch { this.failed.set(true); this.message.set('Nie udało się zapisać decyzji.'); } }
  protected async replyTo(item: WorklistItemView): Promise<void> { if (!item.id) return; const shortText = window.prompt('Krótka odpowiedź dla uczestnika:')?.trim(); if (!shortText) return; try { await this.api.replyToIssue({ itemId: item.id, actingContext: 'TRAINER', purpose: 'PERFORMANCE_PLANNING', replyCommand: { shortText } }); await this.load(); } catch { this.failed.set(true); this.message.set('Nie udało się wysłać odpowiedzi.'); } }
}
