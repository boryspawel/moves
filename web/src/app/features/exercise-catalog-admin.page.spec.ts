import {TestBed} from '@angular/core/testing';
import {RouterTestingModule} from '@angular/router/testing';
import {describe, expect, it, vi} from 'vitest';
import {ApiFacade} from '../core/api.facade';
import {ExerciseCatalogAdminPage} from './exercise-catalog-admin.page';

describe('ExerciseCatalogAdminPage', () => {
  it('lists the editorial current-version projection and links to the first-class create flow', async () => {
    const catalogAdmin = {
      listEditorialExercises: vi.fn().mockResolvedValue({content: [{versionId: 'draft-1', canonicalName: 'Przysiad', versionNumber: 1, status: 'DRAFT'}]})
    };
    await TestBed.configureTestingModule({imports: [ExerciseCatalogAdminPage, RouterTestingModule], providers: [{provide: ApiFacade, useValue: {catalogAdmin}}]}).compileComponents();
    const fixture = TestBed.createComponent(ExerciseCatalogAdminPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Przysiad');
    expect(fixture.nativeElement.querySelector('a[href="/admin/exercise-catalog/new"]')).not.toBeNull();
  });
});
