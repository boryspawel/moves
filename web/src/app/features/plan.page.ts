import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

/** P1 retains this bookmark without retaining manual exercise or goal authoring. */
@Component({
  selector: 'app-plan-page',
  imports: [MatButtonModule, RouterLink],
  template: `
    <section class="panel">
      <h1>Plan treningowy</h1>
      <p class="muted">Ten widok zachowuje dostęp do istniejących planów i ich historii.</p>
      <p>Tworzenie nowego planu zostanie udostępnione w przepływie specjalisty, który łączy cel uczestnika z opublikowaną wersją zestawu ćwiczeń.</p>
      <p>Przygotuj lub otwórz zestaw ćwiczeń, a historię planu sprawdź w kartotece uczestnika.</p>
      <a mat-flat-button routerLink="/exercise-sets">Przejdź do zestawów ćwiczeń</a>
    </section>`,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PlanPage { }
