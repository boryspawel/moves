import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { PatientTimelineEventPanelComponent, TimelineEventComponent } from './specialist-participant-workspace.page';

describe('PatientTimelineEventPanelComponent', () => {
  it('renders the workspace participant context instead of the timeline actor identifier', async () => {
    await TestBed.configureTestingModule({ imports: [PatientTimelineEventPanelComponent] }).compileComponents();
    const fixture = TestBed.createComponent(PatientTimelineEventPanelComponent);
    fixture.componentInstance.event = { title: 'Wpis', actor: '123e4567-e89b-12d3-a456-426614174000' };
    fixture.componentInstance.participantDisplayName = 'Anna Kowalska';
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Uczestnik');
    expect(text).toContain('Anna Kowalska');
    expect(text).not.toContain('Autor');
    expect(text).not.toContain('123e4567-e89b-12d3-a456-426614174000');
  });

  it('does not render UUID-only timeline metadata and retains human-readable text', async () => {
    const uuid = '123e4567-e89b-12d3-a456-426614174000';
    await TestBed.configureTestingModule({ imports: [PatientTimelineEventPanelComponent, TimelineEventComponent] }).compileComponents();

    const event = { title: uuid, summary: uuid, source: uuid };
    const timelineFixture = TestBed.createComponent(TimelineEventComponent);
    timelineFixture.componentInstance.event = event;
    timelineFixture.detectChanges();
    const panelFixture = TestBed.createComponent(PatientTimelineEventPanelComponent);
    panelFixture.componentInstance.event = event;
    panelFixture.detectChanges();

    for (const text of [(timelineFixture.nativeElement as HTMLElement).textContent ?? '', (panelFixture.nativeElement as HTMLElement).textContent ?? '']) {
      expect(text).not.toContain(uuid);
    }
    expect((timelineFixture.nativeElement as HTMLElement).textContent).toContain('Zdarzenie');
    expect((timelineFixture.nativeElement as HTMLElement).textContent).toContain('Brak krótkiego podsumowania');
    expect((panelFixture.nativeElement as HTMLElement).textContent).toContain('Szczegóły zdarzenia');
    expect((panelFixture.nativeElement as HTMLElement).textContent).toContain('Brak dodatkowego opisu.');

    const readableEvent = { title: 'Rozmowa kontrolna', summary: 'Ustalono dalsze kroki.', source: 'Notatka specjalisty' };
    timelineFixture.componentRef.setInput('event', readableEvent);
    timelineFixture.detectChanges();
    panelFixture.componentRef.setInput('event', readableEvent);
    panelFixture.detectChanges();
    for (const text of [(timelineFixture.nativeElement as HTMLElement).textContent ?? '', (panelFixture.nativeElement as HTMLElement).textContent ?? '']) {
      expect(text).toContain('Rozmowa kontrolna');
      expect(text).toContain('Ustalono dalsze kroki.');
      expect(text).toContain('Notatka specjalisty');
    }
  });
});
