import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { DoseEditorComponent } from './dose-editor.component';

describe('DoseEditorComponent', () => {
  it('does not invent a dose until every selected-dose value is supplied', async () => {
    await TestBed.configureTestingModule({imports: [DoseEditorComponent]}).compileComponents();
    const fixture: ComponentFixture<DoseEditorComponent> = TestBed.createComponent(DoseEditorComponent);
    const component = fixture.componentInstance;
    component.type.setValue('STRENGTH'); component.first.setValue(3); component.second.setValue(8);
    expect(component.value()).toBeUndefined();
    component.third.setValue(90);
    expect(component.value()).toEqual({type: 'STRENGTH', sets: 3, reps: 8, restSeconds: 90});
  });

  it('rejects zero for required dose values while allowing zero rest and transition values', async () => {
    await TestBed.configureTestingModule({imports: [DoseEditorComponent]}).compileComponents();
    const component = TestBed.createComponent(DoseEditorComponent).componentInstance;
    component.type.setValue('STRENGTH'); component.first.setValue(0); component.second.setValue(8); component.third.setValue(0);
    expect(component.value()).toBeUndefined();
    component.first.setValue(3); expect(component.value()).toEqual({type: 'STRENGTH', sets: 3, reps: 8, restSeconds: 0});
    component.type.setValue('AEROBIC'); component.first.setValue(30); component.second.setValue(0); component.third.setValue(0);
    expect(component.value()).toEqual({type: 'AEROBIC', durationSeconds: 30, distanceMeters: 0, rpe: 0});
  });
});
