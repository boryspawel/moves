import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

export interface ImportSource { id: string; code: string; displayName: string; licenseCode: string; licenseVerified: boolean; }
export interface CreateImportSource { code: string; displayName: string; defaultLocale: string; licenseCode: string; licenseVerified: boolean; }
export interface ImportBatch { id: string; status: string; totalCount: number; validCount: number; invalidCount: number; blockedCount: number; draftedCount: number; unchangedCount: number; }
export interface ImportRecord { id: string; rowNumber: number; sourceRecordKey?: string; status: string; draftVersionId?: string; }
export interface ImportIssue { code: string; severity: string; stage: string; jsonPointer: string; message: string; }
export interface MatchCandidate { id: string; exerciseId: string; rank: number; score: number; reasons: unknown; decision?: string; }
export interface RecordDetail extends ImportRecord { raw: unknown; normalized?: unknown; issues: ImportIssue[]; matchCandidates: MatchCandidate[]; normalizedSha256?: string; }
export interface RecordPage { content: ImportRecord[]; totalElements: number; }
export interface ReviewResult { exerciseVersionId: string; status: string; version: number; reviews: unknown[]; unmetRequirements: string[]; }

@Injectable({ providedIn: 'root' })
export class ExerciseImportApi {
  private readonly auth = inject(AuthService);
  private readonly root = environment.apiBaseUrl.endsWith('/api')
    ? `${environment.apiBaseUrl}/v1/admin`
    : `${environment.apiBaseUrl}/api/v1/admin`;

  sources(): Promise<ImportSource[]> { return this.get('/exercise-import/sources'); }
  createSource(source: CreateImportSource): Promise<ImportSource> { return this.json('/exercise-import/sources', source); }
  batch(id: string): Promise<ImportBatch> { return this.get(`/exercise-import/batches/${id}`); }
  records(id: string, status = '', severity = ''): Promise<RecordPage> {
    const query = new URLSearchParams({ page: '0', size: '100' });
    if (status) query.set('status', status);
    if (severity) query.set('severity', severity);
    return this.get(`/exercise-import/batches/${id}/records?${query}`);
  }
  record(id: string): Promise<RecordDetail> { return this.get(`/exercise-import/records/${id}`); }
  async upload(sourceId: string, file: File, forceReprocess: boolean): Promise<{batchId: string}> {
    const form = new FormData(); form.append('file', file);
    return this.request(`/exercise-import/batches?sourceId=${encodeURIComponent(sourceId)}&forceReprocess=${forceReprocess}`, {
      method: 'POST', body: form, headers: { 'Idempotency-Key': crypto.randomUUID() }
    });
  }
  decide(recordId: string, candidateId: string, decision: string): Promise<RecordDetail> {
    return this.json(`/exercise-import/records/${recordId}/match`, { candidateId, decision });
  }
  createDraft(recordId: string): Promise<{exerciseVersionId: string}> {
    return this.json(`/exercise-import/records/${recordId}/create-draft`, {});
  }
  review(versionId: string, area: string, decision: string, expectedVersion?: number): Promise<ReviewResult> {
    return this.json(`/exercise-versions/${versionId}/reviews`, { area, decision, expectedVersion });
  }
  reviewStatus(versionId: string): Promise<ReviewResult> { return this.get(`/exercise-versions/${versionId}/reviews`); }
  diff(versionId: string): Promise<unknown> { return this.get(`/exercise-versions/${versionId}/diff`); }
  publish(versionId: string, expectedVersion?: number): Promise<ReviewResult> {
    return this.json(`/exercise-versions/${versionId}/publish`, { expectedVersion });
  }
  private get<T>(path: string): Promise<T> { return this.request(path, { method: 'GET' }); }
  private json<T>(path: string, body: unknown): Promise<T> {
    return this.request(path, { method: 'POST', body: JSON.stringify(body), headers: { 'Content-Type': 'application/json' } });
  }
  private async request<T>(path: string, init: RequestInit): Promise<T> {
    const token = await this.auth.accessToken();
    const headers = new Headers(init.headers); headers.set('Authorization', `Bearer ${token}`);
    const response = await fetch(`${this.root}${path}`, { ...init, headers });
    if (!response.ok) {
      const problem = await response.json().catch(() => ({ detail: `HTTP ${response.status}` })) as { detail?: string };
      const detail = problem.detail ?? `HTTP ${response.status}`;
      throw new Error(response.status === 409 ? `Konflikt (HTTP 409): ${detail}` : detail);
    }
    return response.json() as Promise<T>;
  }
}
