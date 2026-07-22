import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ApiFacade } from '../core/api.facade';

@Component({
  selector: 'app-reminder-preferences-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <section class="panel"><h1>Przypomnienia</h1><p class="muted">Możesz je wyłączyć w każdej chwili. Nie zawierają danych medycznych.</p>
      <form class="form-grid" [formGroup]="form" (ngSubmit)="save()">
        <mat-checkbox class="full" formControlName="remindersEnabled">Włącz przypomnienia</mat-checkbox>
        <mat-form-field><mat-label>Strefa czasowa</mat-label><input matInput formControlName="timeZone"></mat-form-field>
        <mat-form-field><mat-label>Najwcześniej</mat-label><input matInput type="time" formControlName="preferredWindowStart"></mat-form-field>
        <mat-form-field><mat-label>Najpóźniej</mat-label><input matInput type="time" formControlName="preferredWindowEnd"></mat-form-field>
        <mat-form-field><mat-label>Maks. tygodniowo</mat-label><input matInput type="number" min="1" max="7" formControlName="maxMessagesPerWeek"></mat-form-field>
        <mat-checkbox formControlName="muted">Wycisz tymczasowo</mat-checkbox><mat-checkbox formControlName="gentleReturnConsent">Zgadzam się na łagodny powrót po przerwie</mat-checkbox>
        <div class="full"><button mat-flat-button type="submit" [disabled]="form.invalid">Zapisz ustawienia</button></div>
      </form><p class="status" aria-live="polite" [class.error]="failed()">{{ message() }}</p>
    </section>`, changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReminderPreferencesPage {
  private readonly api = inject(ApiFacade).reminders;
  protected readonly message = signal('Ładowanie…'); protected readonly failed = signal(false);
  protected readonly form = new FormGroup({ timeZone: new FormControl(Intl.DateTimeFormat().resolvedOptions().timeZone, { nonNullable: true }), preferredWindowStart: new FormControl('09:00', { nonNullable: true }), preferredWindowEnd: new FormControl('18:00', { nonNullable: true }), maxMessagesPerWeek: new FormControl(3, { nonNullable: true, validators: [Validators.min(1), Validators.max(7)] }), remindersEnabled: new FormControl(true, { nonNullable: true }), muted: new FormControl(false, { nonNullable: true }), gentleReturnConsent: new FormControl(false, { nonNullable: true }) });
  constructor() { void this.load(); }
  protected async save(): Promise<void> { try { const v = this.form.getRawValue(); await this.api.save({ preferenceCommand: { ...v, channel: 'IN_APP', quietHoursStart: '22:00', quietHoursEnd: '07:00' } }); this.message.set('Ustawienia zapisane.'); this.failed.set(false); } catch { this.failed.set(true); this.message.set('Nie udało się zapisać ustawień. Sprawdź dane i spróbuj ponownie.'); } }
  private async load(): Promise<void> { try { const v = await this.api.get(); if (v) this.form.patchValue(v); this.message.set(''); } catch { this.failed.set(true); this.message.set('Nie udało się pobrać ustawień.'); } }
}
