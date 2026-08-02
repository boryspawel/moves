import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { ApiFacade } from '../core/api.facade';
import { SpecialistClientsPage } from './specialist-clients.page';

describe('SpecialistClientsPage', () => {
  it('renders client details and opens the existing workspace route', async () => {
    const specialistClients = { list1: vi.fn().mockResolvedValue([{ participantId: 'participant-1', displayName: 'Anna Kowalska', recordStatus: 'ACTIVE', relationshipContext: 'PATIENT', accessStatus: 'NO_ACCOUNT', attentionItems: [{ id: 'attention-1' }] }]), create1: vi.fn() };
    await TestBed.configureTestingModule({ imports: [SpecialistClientsPage], providers: [provideRouter([]), { provide: ApiFacade, useValue: { specialistClients } }] }).compileComponents();
    const fixture = TestBed.createComponent(SpecialistClientsPage); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Anna Kowalska');
    expect(root.textContent).toContain('Bez konta');
    expect(root.textContent).toContain('Kartoteka nie ma jeszcze konta uczestnika.');
    expect(root.querySelector('a')?.getAttribute('href')).toBe('/specialist/clients/participant-1');
  });

  it('creates a client once and navigates to its workspace', async () => {
    let resolveCreate!: (value: { participantId: string }) => void;
    const specialistClients = { list1: vi.fn().mockResolvedValue([]), create1: vi.fn(() => new Promise<{ participantId: string }>(resolve => { resolveCreate = resolve; })) };
    await TestBed.configureTestingModule({ imports: [SpecialistClientsPage], providers: [provideRouter([]), { provide: ApiFacade, useValue: { specialistClients } }] }).compileComponents();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(SpecialistClientsPage); fixture.detectChanges(); await fixture.whenStable();
    const page = fixture.componentInstance as any;
    page.openCreate(); page.form.controls.displayName.setValue('Nowa kartoteka');
    const first = page.create(); const second = page.create();
    resolveCreate({ participantId: 'participant-2' });
    await Promise.all([first, second]);
    expect(specialistClients.create1).toHaveBeenCalledTimes(1);
    expect(specialistClients.create1).toHaveBeenCalledWith(expect.objectContaining({ clientCommand: expect.objectContaining({ displayName: 'Nowa kartoteka' }) }));
    expect(navigate).toHaveBeenCalledWith(['/specialist/clients', 'participant-2']);
  });

  it('closes the form and navigates after a successful create without reloading the list', async () => {
    const specialistClients = { list1: vi.fn().mockResolvedValueOnce([]).mockRejectedValueOnce(new Error('reload failed')), create1: vi.fn().mockResolvedValue({ participantId: 'participant-2' }) };
    await TestBed.configureTestingModule({ imports: [SpecialistClientsPage], providers: [provideRouter([]), { provide: ApiFacade, useValue: { specialistClients } }] }).compileComponents();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(SpecialistClientsPage); fixture.detectChanges(); await fixture.whenStable();
    const page = fixture.componentInstance as any;
    page.openCreate(); page.form.controls.displayName.setValue('Nowa kartoteka');

    await page.create();

    expect(specialistClients.list1).toHaveBeenCalledTimes(1);
    expect(page.creating()).toBe(false);
    expect(page.createError()).toBe('');
    expect(navigate).toHaveBeenCalledWith(['/specialist/clients', 'participant-2']);
  });

  it('keeps the form available and shows an error when creating a client fails', async () => {
    const specialistClients = { list1: vi.fn().mockResolvedValue([]), create1: vi.fn().mockRejectedValue(new Error('create failed')) };
    await TestBed.configureTestingModule({ imports: [SpecialistClientsPage], providers: [provideRouter([]), { provide: ApiFacade, useValue: { specialistClients } }] }).compileComponents();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(SpecialistClientsPage); fixture.detectChanges(); await fixture.whenStable();
    const page = fixture.componentInstance as any;
    page.openCreate(); page.form.controls.displayName.setValue('Nowa kartoteka');

    await page.create();
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(page.creating()).toBe(true);
    expect(page.saving()).toBe(false);
    expect(page.form.controls.displayName.value).toBe('Nowa kartoteka');
    expect(root.querySelector('[role="dialog"]')).not.toBeNull();
    expect(root.querySelector('.form-error')?.textContent).toContain('Nie udało się dodać klienta. Spróbuj ponownie.');
    expect(navigate).not.toHaveBeenCalled();
  });
});
