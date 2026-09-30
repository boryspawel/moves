import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatSelectHarness } from '@angular/material/select/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { describe, expect, it } from 'vitest';
import { ExerciseCatalogChoice, ExerciseCatalogMultiChoiceComponent } from './exercise-catalog-multi-choice.component';

@Component({
  imports: [ExerciseCatalogMultiChoiceComponent],
  template: `<app-exercise-catalog-multi-choice name="equipment" label="Wymagany sprzęt" [options]="options" [selection]="selection" [allowCustom]="true" (selectionChange)="selection = $event" />`,
})
class ExerciseCatalogMultiChoiceHost {
  private readonly knownOptions: ExerciseCatalogChoice[] = [{ value: 'MAT', label: 'Mata' }, { value: 'DUMBBELL', label: 'Hantel' }];
  selection: string[] = [];
  get options() { return [...this.knownOptions, ...this.selection.filter(value => !this.knownOptions.some(option => option.value === value)).map(value => ({ value, label: value }))]; }
}

describe('ExerciseCatalogMultiChoiceComponent', () => {
  async function setup(initialSelection: string[] = []) {
    await TestBed.configureTestingModule({ imports: [ExerciseCatalogMultiChoiceHost, NoopAnimationsModule] }).compileComponents();
    const fixture = TestBed.createComponent(ExerciseCatalogMultiChoiceHost);
    fixture.componentInstance.selection = initialSelection;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, host: fixture.componentInstance, select: await TestbedHarnessEnvironment.loader(fixture).getHarness(MatSelectHarness) };
  }

  it('selects and deselects Material options while displaying their labels in the trigger', async () => {
    const { fixture, host, select } = await setup();

    await select.open();
    await select.clickOptions({ text: 'Mata' });
    fixture.detectChanges();
    expect(host.selection).toEqual(['MAT']);
    expect(await select.getValueText()).toContain('Mata');

    await select.clickOptions({ text: 'Mata' });
    fixture.detectChanges();
    expect(host.selection).toEqual([]);
  });

  it('keeps an existing unknown equipment value selectable and removes it through the Material control', async () => {
    const { fixture, host, select } = await setup(['SANDBAG']);

    await select.open();
    expect(await select.getValueText()).toContain('SANDBAG');
    expect(await (await select.getOptions({ text: 'SANDBAG' }))[0].isSelected()).toBe(true);
    await select.clickOptions({ text: 'SANDBAG' });
    fixture.detectChanges();
    expect(host.selection).toEqual([]);
  });

  it('adds custom equipment from the rendered input and retains it as a selectable label', async () => {
    const { fixture, host, select } = await setup();
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('.custom-equipment input')!;
    input.value = '  Sandbag  ';
    input.dispatchEvent(new Event('input'));
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.custom-equipment button')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(host.selection).toEqual(['Sandbag']);
    await select.open();
    expect(await select.getValueText()).toContain('Sandbag');
    expect(await (await select.getOptions({ text: 'Sandbag' }))[0].isSelected()).toBe(true);
  });
});
