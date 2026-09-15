import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { DatePipe } from '@angular/common';

type Outcome = { id?: string; metricCode?: string; unit?: string; baseline?: number; targetValue?: number; targetComparator?: string; progress?: { state?: string; baselineToTargetPercent?: number; latestObservation?: { value?: number; measuredAt?: Date } } };
type Observation = { id?: string; outcomeId?: string; value?: number; unit?: string; measuredAt?: Date };

@Component({
  selector: 'app-goal-outcome-progress', standalone: true, imports: [DatePipe], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section class="outcome-progress" aria-label="Postęp celu">
    @for (outcome of outcomes; track outcome.id) {
      <article><h3>Wynik <small>{{ outcome.unit }}</small></h3>
        <p>Stan: {{ outcome.progress?.state || 'Brak danych' }}@if (outcome.progress?.baselineToTargetPercent != null) { · {{ outcome.progress.baselineToTargetPercent }}% }</p>
        <p>Wartość bazowa: {{ outcome.baseline ?? 'Brak' }} · Cel: {{ outcome.targetValue ?? 'Brak' }} ({{ outcome.targetComparator || 'Brak' }})</p>
        @if (points(outcome).length) {<svg viewBox="0 0 100 60" role="img" [attr.aria-label]="'Historia pomiarów: ' + (outcome.metricCode || 'wynik')"><line x1="5" x2="95" y1="52" y2="52"/><line x1="5" x2="5" y1="5" y2="52"/><line x1="5" x2="95" [attr.y1]="referenceY(outcome.baseline, outcome)" [attr.y2]="referenceY(outcome.baseline, outcome)"/><line x1="5" x2="95" [attr.y1]="referenceY(outcome.targetValue, outcome)" [attr.y2]="referenceY(outcome.targetValue, outcome)"/>@for(point of points(outcome);track point.id){<circle [attr.cx]="point.x" [attr.cy]="point.y" r="2"/>}</svg>
          <ol>@for(point of points(outcome);track point.id){<li>{{ point.measuredAt | date:'mediumDate' }}: {{ point.value }} {{ outcome.unit }}</li>}</ol>
        } @else {<p>Brak pomiarów w wczytanej historii.</p>}
      </article>
    }
    @if (nextCursor) {<button type="button" (click)="older.emit()" [disabled]="loading">Wczytaj starsze pomiary</button>}
  </section>`,
  styles: [`.outcome-progress svg{width:100%;max-width:32rem;height:auto}.outcome-progress line{stroke:#789}.outcome-progress circle{fill:#075985}`]
})
export class GoalOutcomeProgressComponent {
  @Input() outcomes: readonly Outcome[] = [];
  @Input() history: readonly Observation[] = [];
  @Input() nextCursor?: string;
  @Input() loading = false;
  @Output() readonly older = new EventEmitter<void>();
  points(outcome: Outcome) { const values=this.observations(outcome); const extent=this.extent(outcome); return values.map((item,index)=>({...item,x:values.length===1?50:5+90*index/(values.length-1),y:this.y(item.value!,extent)})); }
  referenceY(value: number | undefined, outcome: Outcome) { return value == null ? 52 : this.y(value,this.extent(outcome)); }
  private observations(outcome: Outcome) { return this.history.filter(item=>item.outcomeId===outcome.id&&item.value!=null&&item.measuredAt&&(item.unit==null||item.unit===outcome.unit)).slice().sort((a,b)=>(a.measuredAt?.getTime()??0)-(b.measuredAt?.getTime()??0)); }
  private extent(outcome: Outcome): [number,number] { const values=[...this.observations(outcome).map(item=>item.value!),outcome.baseline,outcome.targetValue].filter((value):value is number=>value!=null&&Number.isFinite(value)); const min=Math.min(...values),max=Math.max(...values); return min===max?[min-1,max+1]:[min,max]; }
  private y(value:number,[min,max]:[number,number]) { return 52-47*((value-min)/(max-min)); }
}
