import { ChangeDetectionStrategy, Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { JsonPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ExerciseImportApi, ImportBatch, ImportRecord, ImportSource, RecordDetail, ReviewResult } from '../core/exercise-import.api';

@Component({
  selector: 'app-exercise-import-page',
  imports: [FormsModule, JsonPipe, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <section class="panel import-page">
      <h1>Masowy import ćwiczeń</h1>
      <p class="muted">JSONL trafia do stagingu. Publikacja zawsze wymaga jawnych recenzji.</p>
      <p class="status" aria-live="polite" [class.error]="failed()">{{ message() }}</p>

      <div class="toolbar">
        <mat-form-field><mat-label>Źródło</mat-label><mat-select [(ngModel)]="sourceId">
          @for (source of sources(); track source.id) { <mat-option [value]="source.id">{{ source.displayName }} · {{ source.licenseCode }}</mat-option> }
        </mat-select></mat-form-field>
        <label class="file">Plik JSONL <input type="file" accept=".jsonl,application/x-ndjson" (change)="choose($event)"></label>
        <button mat-flat-button type="button" [disabled]="!sourceId || !file" (click)="upload()">Prześlij</button>
      </div>

      @if (batch(); as current) {
        <mat-card><mat-card-content>
          <h2>Batch {{ current.id }}</h2>
          <p><strong>{{ current.status }}</strong> · rekordy {{ current.totalCount }} · poprawne {{ current.validCount }} · błędne {{ current.invalidCount }} · blokady {{ current.blockedCount }} · szkice {{ current.draftedCount }} · bez zmian {{ current.unchangedCount }}</p>
          <div class="toolbar"><mat-form-field><mat-label>Status rekordu</mat-label><input matInput [(ngModel)]="statusFilter"></mat-form-field><mat-form-field><mat-label>Severity</mat-label><input matInput [(ngModel)]="severityFilter"></mat-form-field><button mat-stroked-button (click)="loadRecords()">Filtruj</button></div>
        </mat-card-content></mat-card>
        <div class="grid">
          <ol class="records" aria-label="Rekordy importu">
            @for (record of records(); track record.id) {
              <li><button type="button" (click)="open(record)">#{{ record.rowNumber }} {{ record.sourceRecordKey || 'bez klucza' }} <strong>{{ record.status }}</strong></button></li>
            }
          </ol>
          @if (detail(); as item) {
            <article>
              <h2>Rekord #{{ item.rowNumber }}</h2>
              <h3>Raw</h3><pre>{{ item.raw | json }}</pre>
              <h3>Normalized</h3><pre>{{ item.normalized | json }}</pre>
              <h3>Problemy</h3><ul>@for (issue of item.issues; track issue.code + issue.jsonPointer) { <li><strong>{{ issue.severity }} {{ issue.code }}</strong> {{ issue.jsonPointer }} — {{ issue.message }}</li> }</ul>
              <h3>Kandydaci</h3><ul>@for (candidate of item.matchCandidates; track candidate.id) { <li>#{{ candidate.rank }} {{ candidate.exerciseId }} ({{ candidate.score }}) <button mat-button (click)="decide(candidate.id, 'SAME')">SAME</button><button mat-button (click)="decide(candidate.id, 'DIFFERENT')">DIFFERENT</button><button mat-button (click)="decide(candidate.id, 'UNSURE')">UNSURE</button><pre>{{ candidate.reasons | json }}</pre></li> }</ul>
              @if (item.status === 'READY_FOR_DRAFT') { <button mat-flat-button (click)="createDraft()">Utwórz szkic</button> }
              @if (item.draftVersionId) { <button mat-stroked-button (click)="loadEditorial(item.draftVersionId)">Pokaż diff i recenzje</button> }
            </article>
          }
        </div>
      }
      @if (review(); as editorial) {
        <mat-card><mat-card-content><h2>Recenzja wersji {{ editorial.exerciseVersionId }}</h2>
          <p>Status: <strong>{{ editorial.status }}</strong></p><p>Niespełnione warunki: {{ editorial.unmetRequirements.join(', ') || 'brak' }}</p>
          <pre>{{ difference() | json }}</pre>
          <div class="toolbar"><mat-form-field><mat-label>Obszar</mat-label><mat-select [(ngModel)]="reviewArea">@for (area of reviewAreas; track area) { <mat-option [value]="area">{{ area }}</mat-option> }</mat-select></mat-form-field><mat-form-field><mat-label>Decyzja</mat-label><mat-select [(ngModel)]="reviewDecision"><mat-option value="APPROVED">APPROVED</mat-option><mat-option value="CHANGES_REQUESTED">CHANGES_REQUESTED</mat-option></mat-select></mat-form-field><button mat-flat-button (click)="submitReview()">Zapisz recenzję</button><button mat-flat-button (click)="publish()">Publikuj</button></div>
        </mat-card-content></mat-card>
      }
    </section>
  `,
  styles: [`
    .toolbar{display:flex;gap:1rem;align-items:center;flex-wrap:wrap}.file{padding:.75rem;border:1px solid var(--mat-sys-outline-variant);border-radius:.5rem}.grid{display:grid;grid-template-columns:minmax(18rem,1fr) 2fr;gap:1rem;margin-top:1rem}.records{margin:0;padding:0;list-style:none}.records button{width:100%;padding:.75rem;text-align:left;background:transparent;border:0;border-bottom:1px solid #ddd;cursor:pointer}article,mat-card{margin-top:1rem}pre{max-height:22rem;overflow:auto;white-space:pre-wrap;background:#f4f4f4;padding:.75rem;border-radius:.4rem}.error{color:#b3261e}@media(max-width:800px){.grid{grid-template-columns:1fr}}
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExerciseImportPage implements OnDestroy {
  private readonly api = inject(ExerciseImportApi); private timer?: number;
  protected readonly sources = signal<ImportSource[]>([]); protected readonly batch = signal<ImportBatch | null>(null); protected readonly records = signal<ImportRecord[]>([]); protected readonly detail = signal<RecordDetail | null>(null); protected readonly review = signal<ReviewResult | null>(null); protected readonly difference = signal<unknown>(null); protected readonly message = signal('Ładowanie źródeł…'); protected readonly failed = signal(false);
  protected sourceId=''; protected file?:File; protected statusFilter=''; protected severityFilter=''; protected reviewArea='CONTENT'; protected reviewDecision='APPROVED'; protected readonly reviewAreas=['CONTENT','TECHNIQUE','ANATOMY_EXPOSURE','LICENSE','MEDIA'];
  constructor(){void this.loadSources();}
  ngOnDestroy():void{if(this.timer)window.clearTimeout(this.timer);}
  protected choose(event:Event):void{this.file=(event.target as HTMLInputElement).files?.[0];}
  protected async upload():Promise<void>{if(!this.file||!this.sourceId)return;await this.run(async()=>{const result=await this.api.upload(this.sourceId,this.file!,false);await this.refresh(result.batchId);});}
  protected async loadRecords():Promise<void>{const current=this.batch();if(!current)return;await this.run(async()=>{this.records.set((await this.api.records(current.id,this.statusFilter,this.severityFilter)).content);});}
  protected async open(record:ImportRecord):Promise<void>{await this.run(async()=>this.detail.set(await this.api.record(record.id)));}
  protected async decide(candidateId:string,decision:string):Promise<void>{const item=this.detail();if(!item)return;await this.run(async()=>{this.detail.set(await this.api.decide(item.id,candidateId,decision));await this.loadRecords();});}
  protected async createDraft():Promise<void>{const item=this.detail();if(!item)return;await this.run(async()=>{const created=await this.api.createDraft(item.id);this.detail.set(await this.api.record(item.id));await this.loadEditorial(created.exerciseVersionId);});}
  protected async loadEditorial(versionId:string):Promise<void>{await this.run(async()=>{this.review.set(await this.api.reviewStatus(versionId));this.difference.set(await this.api.diff(versionId));});}
  protected async submitReview():Promise<void>{const value=this.review();if(!value)return;await this.run(async()=>this.review.set(await this.api.review(value.exerciseVersionId,this.reviewArea,this.reviewDecision,value.version)));}
  protected async publish():Promise<void>{const value=this.review();if(!value)return;await this.run(async()=>this.review.set(await this.api.publish(value.exerciseVersionId,value.version)));}
  private async loadSources():Promise<void>{await this.run(async()=>{const values=await this.api.sources();this.sources.set(values);if(values.length)this.sourceId=values[0].id;this.message.set(`${values.length} źródeł.`);});}
  private async refresh(id:string):Promise<void>{const current=await this.api.batch(id);this.batch.set(current);await this.loadRecords();if(['QUEUED','PROCESSING','RECEIVED'].includes(current.status))this.timer=window.setTimeout(()=>void this.refresh(id),1000);}
  private async run(action:()=>Promise<void>):Promise<void>{this.failed.set(false);try{await action();this.message.set('Operacja zakończona.');}catch(error){this.failed.set(true);this.message.set(error instanceof Error?error.message:'Operacja nie powiodła się.');}}
}
