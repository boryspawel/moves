import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { CreatePlanCommandSessionKindEnum } from '../api/generated/src';
import { ApiFacade } from '../core/api.facade';

@Component({
  selector: 'app-plan-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <section class="panel">
      <h1>Przypisz prosty plan</h1>
      <p class="muted">Backend sprawdzi aktywną relację z uczestnikiem i dokładną opublikowaną wersję ćwiczenia.</p>
      <form class="form-grid" [formGroup]="form" (ngSubmit)="create()">
        <mat-form-field><mat-label>ID konta uczestnika</mat-label><input matInput formControlName="participantAccountId"></mat-form-field>
        <mat-form-field><mat-label>ID wersji ćwiczenia</mat-label><input matInput formControlName="exerciseVersionId"></mat-form-field>
        <mat-form-field><mat-label>Cel</mat-label><input matInput formControlName="goalName"></mat-form-field>
        <mat-form-field><mat-label>Nazwa planu</mat-label><input matInput formControlName="planName"></mat-form-field>
        <mat-form-field><mat-label>Cykl</mat-label><input matInput formControlName="cycleName"></mat-form-field>
        <mat-form-field><mat-label>Mikrocykl</mat-label><input matInput formControlName="microcycleName"></mat-form-field>
        <mat-form-field><mat-label>Sesja</mat-label><input matInput formControlName="sessionTitle"></mat-form-field>
        <mat-form-field><mat-label>Rodzaj sesji</mat-label><mat-select formControlName="sessionKind"><mat-option value="SELF_GUIDED">Samodzielna</mat-option><mat-option value="OFFLINE_APPOINTMENT">Spotkanie offline</mat-option></mat-select></mat-form-field>
        <mat-form-field><mat-label>Serie</mat-label><input matInput type="number" min="1" formControlName="targetSets"></mat-form-field>
        <mat-form-field><mat-label>Powtórzenia</mat-label><input matInput type="number" min="1" formControlName="targetRepetitions"></mat-form-field>
        <div class="full"><button mat-flat-button type="submit" [disabled]="form.invalid">Przypisz plan</button></div>
      </form>
      <p class="status" aria-live="polite" [class.error]="failed()">{{ message() }}</p>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PlanPage {
  private readonly api = inject(ApiFacade).planning;
  protected readonly message = signal('');
  protected readonly failed = signal(false);
  protected readonly form = new FormGroup({
    participantAccountId: new FormControl('', { nonNullable: true, validators: Validators.required }),
    exerciseVersionId: new FormControl('', { nonNullable: true, validators: Validators.required }),
    goalName: new FormControl('Regularny ruch', { nonNullable: true, validators: Validators.required }),
    planName: new FormControl('Plan podstawowy', { nonNullable: true, validators: Validators.required }),
    cycleName: new FormControl('Cykl 1', { nonNullable: true, validators: Validators.required }),
    microcycleName: new FormControl('Tydzień 1', { nonNullable: true, validators: Validators.required }),
    sessionTitle: new FormControl('Sesja podstawowa', { nonNullable: true, validators: Validators.required }),
    sessionKind: new FormControl('SELF_GUIDED', { nonNullable: true }),
    targetSets: new FormControl(3, { nonNullable: true, validators: Validators.min(1) }),
    targetRepetitions: new FormControl(8, { nonNullable: true, validators: Validators.min(1) })
  });
  protected async create(): Promise<void> {
    const value = this.form.getRawValue();
    this.failed.set(false);
    try {
      const result = await this.api.createLegacyTrainingPlan({ createPlanCommand: {
        participantAccountId: value.participantAccountId, goalName: value.goalName, planName: value.planName,
        cycleName: value.cycleName, microcycleName: value.microcycleName, sessionTitle: value.sessionTitle,
        sessionKind: value.sessionKind as CreatePlanCommandSessionKindEnum,
        prescriptions: [{ exerciseVersionId: value.exerciseVersionId, targetSets: value.targetSets, targetRepetitions: value.targetRepetitions }]
      }});
      this.message.set(`Przypisano sesję ${result.session?.id ?? ''}.`);
    } catch { this.failed.set(true); this.message.set('Nie udało się przypisać planu. Sprawdź relację i wersję ćwiczenia.'); }
  }
}
