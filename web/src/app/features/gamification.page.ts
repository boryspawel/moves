import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { GamificationProgressView } from '../api/generated/src';
import { ApiFacade } from '../core/api.facade';

@Component({
  selector: 'app-gamification-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule],
  template: `
    <section class="panel">
      <h1>Mój wynik</h1>
      <p class="muted">Gamifikacja jest dobrowolna. Wynik nie zawiera danych medycznych.</p>
      <form class="form-grid" [formGroup]="form" (ngSubmit)="save()">
        <mat-checkbox formControlName="enabled">Włączam gamifikację</mat-checkbox>
        <mat-form-field class="full"><mat-label>Pseudonim</mat-label><input matInput formControlName="pseudonym" maxlength="80"></mat-form-field>
        <mat-checkbox formControlName="rankingVisible" [disabled]="!form.controls.enabled.value">Pokaż mój pseudonim w rankingu</mat-checkbox>
        <div class="full"><button mat-flat-button type="submit" [disabled]="form.invalid">Zapisz ustawienia</button></div>
      </form>
      <p class="status" aria-live="polite" [class.error]="failed()">{{ message() }}</p>
      @if (progress(); as current) {
        <h2>{{ current.points ?? 0 }} pkt</h2>
        <ul class="card-list">
          @for (entry of current.ledger ?? []; track entry.id) {
            <li>{{ entry.points ?? 0 }} pkt · {{ entry.reason ?? '—' }}</li>
          } @empty { <li>Nie masz jeszcze wpisów punktowych.</li> }
        </ul>
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GamificationPage {
  private readonly api = inject(ApiFacade).gamification;
  protected readonly progress = signal<GamificationProgressView | null>(null);
  protected readonly message = signal('Ładowanie…');
  protected readonly failed = signal(false);
  protected readonly form = new FormGroup({
    enabled: new FormControl(false, { nonNullable: true }),
    pseudonym: new FormControl('', { nonNullable: true, validators: Validators.maxLength(80) }),
    rankingVisible: new FormControl(false, { nonNullable: true })
  });

  constructor() { void this.load(); }

  protected async save(): Promise<void> {
    const value = this.form.getRawValue();
    this.failed.set(false);
    try {
      await this.api.profile({ profileCommand: {
        enabled: value.enabled,
        pseudonym: value.pseudonym.trim() || undefined,
        rankingVisible: value.rankingVisible
      }});
      this.message.set('Ustawienia zapisane.');
      await this.load();
    } catch {
      this.failed.set(true);
      this.message.set('Nie udało się zapisać ustawień gamifikacji.');
    }
  }

  private async load(): Promise<void> {
    this.failed.set(false);
    try {
      const progress = await this.api.gamificationProgress();
      this.progress.set(progress);
      this.form.patchValue({
        enabled: progress.profile?.enabled ?? false,
        pseudonym: progress.profile?.pseudonym ?? '',
        rankingVisible: progress.profile?.rankingVisible ?? false
      });
      this.message.set('');
    } catch {
      this.failed.set(true);
      this.message.set('Nie udało się pobrać wyniku gamifikacji.');
    }
  }
}
