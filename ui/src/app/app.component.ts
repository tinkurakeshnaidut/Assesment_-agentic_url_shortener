import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

interface Artifact { name: string; type: string; content: string; }
interface Task {
  id: string; name: string; agent: string; dependsOn: string[]; status: string;
  attempts: number; requiresApproval: boolean; usedFallback: boolean; message: string; artifacts: Artifact[];
}
interface AuditEvent { time: string; taskId: string; type: string; detail: string; }
interface Run {
  id: string; requirement: string; version: number; scenario: string; status: string;
  tasks: { [id: string]: Task }; audit: AuditEvent[];
}
interface Scenario { name: string; requirement: string; hint: string; }

// demo failure injections: task id -> number of forced failures
const INJECTIONS: { [label: string]: { [task: string]: number } } = {
  'None': {},
  'TESTING fails once (retry)': { TESTING: 1 },
  'TESTING fails 3 times (fallback)': { TESTING: 3 },
  'TESTING fails 4 times (rollback + safe stop)': { TESTING: 4 },
  'Policy violation in code (once)': { IMPLEMENTATION_POLICY: 1 }
};

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2>Agentic SDLC - URL Shortener</h2>

    <section>
      <b>URL shortener</b>
      <div class="row">
        URL <input class="wide" [(ngModel)]="longUrl">
        Max uses <input type="number" min="1" [(ngModel)]="maxUses" placeholder="unlimited">
        Expires in days <input type="number" min="1" [(ngModel)]="expiresInDays" placeholder="never">
        <button (click)="shorten()">Shorten</button>
        @if (shortError) { <span class="err">{{ shortError }}</span> }
      </div>
      @if (link) {
        <div>
          Short link: <a [href]="'/r/' + link.code" target="_blank">{{ origin }}/r/{{ link.code }}</a>
          (open it in a new tab; each open counts as one use)
        </div>
        <div>
          Used {{ link.clickCount }}
          @if (link.maxUses) { of {{ link.maxUses }} times } @else { times (no limit) }
          | expires: {{ link.expiresAt ? (link.expiresAt | date:'medium') : 'never' }}
          @if (link.maxUses && link.clickCount >= link.maxUses) { <b class="err"> - used up, the link now returns 410</b> }
        </div>
      }
    </section>

    <section>
      <b>Requirement</b>
      <div>
        @for (s of scenarios; track s.name) {
          <button (click)="requirement = s.requirement" [title]="s.hint">{{ s.name }}</button>
        }
      </div>
      <textarea rows="3" [(ngModel)]="requirement"></textarea>
      <div>
        Inject failure:
        <select [(ngModel)]="injection">
          @for (label of injectionLabels; track label) { <option [value]="label">{{ label }}</option> }
        </select>
        <button (click)="start()">Start run</button>
        @if (error) { <span class="err">{{ error }}</span> }
      </div>
    </section>

    @if (metrics) {
      <section>
        <b>Reliability metrics</b>
        runs {{ metrics.runsStarted }} | success rate {{ metrics.successRatePercent }}% |
        retries {{ metrics.retries }} | fallbacks {{ metrics.fallbacks }} | rollbacks {{ metrics.rollbacks }} |
        replans {{ metrics.replans }} | MTTR {{ metrics.avgMttrMs }} ms | end-to-end {{ metrics.avgEndToEndMs }} ms
      </section>
    }

    @if (run) {
      <section>
        <b>2. Run {{ run.id }}</b> - {{ run.scenario }}, requirement v{{ run.version }},
        status <span class="status {{ run.status }}">{{ run.status }}</span>
        <button (click)="stop()" [disabled]="isFinished()">Safe stop</button>

        <table>
          <tr><th>Task</th><th>Depends on</th><th>Status</th><th>Attempts</th><th>Action</th></tr>
          @for (t of tasks(); track t.id) {
            <tr [class.selected]="selected?.id === t.id" (click)="selected = t">
              <td>{{ t.id }}@if (t.requiresApproval) { <span title="human approval">*</span> }</td>
              <td>{{ t.dependsOn.join(', ') || '-' }}</td>
              <td><span class="status {{ t.status }}">{{ t.status }}</span>{{ t.usedFallback ? ' (fallback)' : '' }}</td>
              <td>{{ t.attempts }}</td>
              <td>
                @if (t.status === 'WAITING_APPROVAL') {
                  <button (click)="decide(t, true)">Approve</button>
                  <button (click)="decide(t, false)">Reject</button>
                }
              </td>
            </tr>
          }
        </table>
        <small>* needs human approval. Click a row to see its artifacts.</small>
      </section>

      <section>
        <b>3. Change requirement (re-plan)</b>
        <textarea rows="2" [(ngModel)]="newRequirement"></textarea>
        <button (click)="replan()">Re-plan</button>
      </section>

      @if (selected) {
        <section>
          <b>Artifacts of {{ selected.id }}</b>
          @for (a of selected.artifacts; track a.name) {
            <details>
              <summary>{{ a.name }} ({{ a.type }})</summary>
              <pre>{{ a.content }}</pre>
            </details>
          }
          @if (selected.artifacts.length === 0) { <div>no artifacts yet</div> }
        </section>
      }

      <section>
        <b>Audit trail</b>
        <pre class="audit">@for (e of run.audit; track $index) {{{ e.time | date:'HH:mm:ss.SSS' }}  {{ e.taskId }}  {{ e.type }}  {{ e.detail }}
}</pre>
      </section>
    }
  `,
  styles: [`
    :host { display: block; max-width: 980px; margin: 16px auto; font-family: system-ui, sans-serif; font-size: 14px; }
    section { border: 1px solid #ccc; border-radius: 6px; padding: 10px; margin-bottom: 10px; }
    textarea { width: 100%; box-sizing: border-box; margin: 6px 0; }
    button { margin: 2px 4px 2px 0; }
    table { width: 100%; border-collapse: collapse; margin-top: 8px; }
    th, td { border-bottom: 1px solid #eee; padding: 4px; text-align: left; }
    tr.selected { background: #eef4ff; }
    pre { background: #f6f6f6; padding: 8px; overflow: auto; max-height: 260px; }
    .audit { max-height: 220px; font-size: 12px; }
    .err { color: #b00020; margin-left: 8px; }
    .row input { margin-right: 8px; }
    .row input[type=number] { width: 90px; }
    .wide { width: 280px; }
    .status { padding: 1px 6px; border-radius: 8px; background: #eee; }
    .DONE, .COMPLETED { background: #c8f0cf; }
    .RUNNING { background: #cfe3ff; }
    .WAITING_APPROVAL, .AWAITING_APPROVAL { background: #ffe9a8; }
    .ROLLED_BACK, .FAILED, .REJECTED, .STOPPED { background: #ffc9c9; }
  `]
})
export class AppComponent implements OnInit, OnDestroy {
  scenarios: Scenario[] = [];
  requirement = '';
  newRequirement = '';
  injectionLabels = Object.keys(INJECTIONS);
  injection = this.injectionLabels[0];
  run?: Run;
  selected?: Task;
  metrics: any;
  error = '';
  longUrl = 'https://example.com';
  maxUses: number | null = 3;
  expiresInDays: number | null = null;
  link: any;
  shortError = '';
  origin = window.location.origin;
  private timer?: any;

  constructor(private http: HttpClient) { }

  ngOnInit() {
    this.http.get<Scenario[]>('/api/scenarios').subscribe(s => {
      this.scenarios = s;
      this.requirement = s[0]?.requirement ?? '';
    });
    this.timer = setInterval(() => this.refresh(), 1000);
  }

  ngOnDestroy() { clearInterval(this.timer); }

  shorten() {
    this.shortError = '';
    this.http.post('/api/urls', { url: this.longUrl, maxUses: this.maxUses, expiresInDays: this.expiresInDays })
      .subscribe({
        next: l => this.link = l,
        error: e => this.shortError = e.error?.error ?? 'request failed'
      });
  }

  tasks(): Task[] { return this.run ? Object.values(this.run.tasks) : []; }

  isFinished(): boolean {
    return !this.run || ['COMPLETED', 'FAILED', 'STOPPED'].includes(this.run.status);
  }

  start() {
    this.error = '';
    this.http.post<Run>('/api/runs', { requirement: this.requirement, failureInjection: INJECTIONS[this.injection] })
      .subscribe({
        next: r => { this.run = r; this.newRequirement = r.requirement; this.selected = undefined; },
        error: e => this.error = e.error?.error ?? 'request failed'
      });
  }

  decide(task: Task, approve: boolean) {
    this.post(`/api/runs/${this.run!.id}/tasks/${task.id}/decision`, { approve, reviewer: 'ui-user', comment: '' });
  }

  replan() { this.post(`/api/runs/${this.run!.id}/replan`, { requirement: this.newRequirement }); }

  stop() { this.post(`/api/runs/${this.run!.id}/stop`, {}); }

  private post(url: string, body: object) {
    this.error = '';
    this.http.post(url, body).subscribe({
      next: () => this.refresh(),
      error: e => this.error = e.error?.error ?? 'request failed'
    });
  }

  private refresh() {
    this.http.get('/api/metrics').subscribe(m => this.metrics = m);
    if (this.link) {
      this.http.get(`/api/urls/${this.link.code}/stats`).subscribe(l => this.link = l);
    }
    if (!this.run) { return; }
    this.http.get<Run>(`/api/runs/${this.run.id}`).subscribe(r => {
      this.run = r;
      if (this.selected) { this.selected = r.tasks[this.selected.id]; }
    });
  }
}
