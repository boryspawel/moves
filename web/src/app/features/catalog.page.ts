import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ExercisePickerComponent, ExerciseSelection } from './catalog/exercise-picker.component';

@Component({
  selector: 'app-catalog-page',
  imports: [ExercisePickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './catalog.page.scss',
  template: `<main class="catalog panel"><h1>Katalog ćwiczeń</h1><p>Znajdź opublikowaną wersję ćwiczenia i obejrzyj podgląd bez opuszczania katalogu.</p>
  <app-exercise-picker [selectionEnabled]="true" (selected)="selected($event)" />
  @if (selection(); as choice) {<section class="selection-notice" role="status"><h2>Wybrano ćwiczenie</h2><p>{{ choice.name }} jest gotowe do dodania w kompozytorze zestawu.</p></section>}
  </main>`
})
export class CatalogPage {
  readonly selection = signal<ExerciseSelection | undefined>(undefined);
  constructor(title: Title) { title.setTitle('Katalog ćwiczeń | Moves'); }
  selected(choice: ExerciseSelection) { this.selection.set(choice); }
}
