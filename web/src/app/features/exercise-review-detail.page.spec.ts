import {TestBed} from '@angular/core/testing';
import {ActivatedRoute} from '@angular/router';
import {RouterTestingModule} from '@angular/router/testing';
import {describe, expect, it, vi} from 'vitest';
import {ExerciseImportApi} from '../core/exercise-import.api';
import {ExerciseReviewDetailPage} from './exercise-review-detail.page';

describe('ExerciseReviewDetailPage', () => {
  it('renders editorial detail and keeps publication disabled until backend says ready', async () => {
    const api={editorialDetail:vi.fn().mockResolvedValue({editor:{version:{exerciseId:'e',exerciseVersionId:'version',canonicalName:'Przysiad',versionNumber:1,status:'DRAFT',instruction:'Stań stabilnie',movementPatterns:['SQUAT'],stimulusType:'STRENGTH',fatigueProfile:'LOW',technicalLevel:'BEGINNER',environment:'GYM',requiredEquipment:[]},loadCharacteristics:[],evidence:[],contributions:[]},importProblems:[],review:{version:1,unmetRequirements:['REVIEW_CONTENT_REQUIRED'],requiredAreas:['CONTENT','TECHNIQUE','ANATOMY_EXPOSURE','LICENSE'],readyToPublish:false}})};
    await TestBed.configureTestingModule({imports:[ExerciseReviewDetailPage,RouterTestingModule],providers:[{provide:ExerciseImportApi,useValue:api},{provide:ActivatedRoute,useValue:{snapshot:{paramMap:{get:()=> 'version'}}}}]}).compileComponents();
    const fixture=TestBed.createComponent(ExerciseReviewDetailPage);fixture.detectChanges();await new Promise(resolve=>setTimeout(resolve));fixture.detectChanges();
    expect(api.editorialDetail).toHaveBeenCalledWith('version');expect(fixture.nativeElement.textContent).toContain('Przysiad');expect(fixture.nativeElement.textContent).toContain('Wymagana recenzja treści');expect(fixture.nativeElement.querySelector('button[disabled]')?.textContent).toContain('Opublikuj');
  });
});
