import { chromium, type Page } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { loginWithOidc, optionalCredentials } from './fixtures';

type Version = {
  id?: string;
  lockVersion?: number;
  status?: string;
  items?: Array<{ id?: string }>;
};
type Anatomy = {
  visualRegionExposures?: Array<{
    visualRegionCode?: string;
    view?: string;
    channel?: string;
    breakdowns?: unknown[];
  }>;
  visualMappingVersion?: string;
};
type CatalogResult = { exerciseVersionId?: string; selectable?: boolean };
type CatalogPage = { results?: CatalogResult[]; nextCursor?: string; hasMore?: boolean };
type ExercisePreview = { exerciseVersionId?: string; anatomy?: Array<{ loadChannel?: string }> };
type Api = <T>(url: string, method?: string, data?: unknown) => Promise<T>;
const authDir = path.join(__dirname, '..', '.auth');

/** Real OIDC setup; credentials come only from the environment. */
export default async function specialistGlobalSetup(): Promise<void> {
  const credentials = optionalCredentials('E2E_SPECIALIST');
  if (!credentials)
    throw new Error(
      'Specialist map E2E requires E2E_SPECIALIST_USERNAME and E2E_SPECIALIST_PASSWORD. Supply a local test account through the environment; no password is stored in this repository.',
    );
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({
      baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4200',
    });
    let authorization = '';
    page.on('request', (request) => {
      if (
        request.url().includes('/api/') &&
        request.headers()['authorization']?.startsWith('Bearer ')
      )
        authorization ||= request.headers()['authorization'];
    });
    await loginWithOidc(page, credentials);
    await page.goto('/onboarding');
    await page.waitForResponse(
      (response) =>
        response.url().includes('/api/v1/onboarding') && response.request().method() === 'GET',
    );
    if (!authorization)
      throw new Error(
        'OIDC login completed but no authenticated API request was observed. Check E2E_BASE_URL, Keycloak redirect URIs and the specialist role.',
      );
    await bootstrapSpecialist(page, authorization);
    const api = authenticatedApi(page, authorization);
    const fixture = await createFixture(api);
    await mkdir(authDir, { recursive: true });
    await page.context().storageState({ path: path.join(authDir, 'specialist.json') });
    await writeFile(
      path.join(authDir, 'specialist-body-map-fixture.json'),
      JSON.stringify(fixture, null, 2),
      'utf8',
    );
  } finally {
    await browser.close();
  }
}

function authenticatedApi(page: Page, authorization: string): Api {
  return async <T>(url: string, method = 'GET', data?: unknown): Promise<T> => {
    const response = await page.request.fetch(url, {
      method,
      headers: {
        Authorization: authorization,
        ...(data === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      data,
      failOnStatusCode: false,
    });
    if (!response.ok())
      throw new Error(
        `Body-map fixture API call ${method} ${url} failed: HTTP ${response.status()}.`,
      );
    return (await response.json()) as T;
  };
}

async function bootstrapSpecialist(page: Page, authorization: string): Promise<void> {
  const call = async (url: string, body?: unknown) => {
    const response = await page.request.fetch(url, {
      method: body === undefined ? 'GET' : 'PUT',
      headers: {
        Authorization: authorization,
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      data: body,
      failOnStatusCode: false,
    });
    if (!response.ok())
      throw new Error(
        `Specialist onboarding bootstrap failed for ${url}: HTTP ${response.status()}.`,
      );
    return response;
  };
  for (const [url, body] of [
    ['/api/v1/onboarding/profile-type', { profileType: 'SPECIALIST' }],
    [
      '/api/v1/onboarding/legal-acknowledgements',
      { termsAccepted: true, privacyNoticeAcknowledged: true },
    ],
    [
      '/api/v1/onboarding/specialist-profile',
      {
        displayName: 'E2E body map specialist',
        specialistKind: 'PHYSIOTHERAPIST',
        timeZoneId: 'Europe/Warsaw',
      },
    ],
    [
      '/api/v1/onboarding/availability',
      {
        slots: [
          { dayOfWeek: 'MONDAY', startTime: '09:00', endTime: '10:00', timeZone: 'Europe/Warsaw' },
        ],
      },
    ],
  ] as const)
    await call(url, body);
  const state = await call('/api/v1/onboarding');
  if (((await state.json()) as { stage?: string }).stage !== 'READY')
    throw new Error('Specialist onboarding bootstrap did not reach READY.');
}

async function createFixture(api: Api) {
  const { setId, version: initialVersion } = await createDraft(api);
  const geometry = await api<Array<{ code?: string }>>('/api/v1/anatomy/visual-regions');
  const override = configuredVersionIds();
  if (override.length) {
    const version = await replaceItems(api, setId, initialVersion, override);
    const fixture = await validatedFixture(api, setId, version, geometry);
    if (!fixture)
      throw new Error(
        'Configured E2E_BODY_MAP_EXERCISE_VERSION_IDS did not yield the required FRONT, BACK, DYN_EXU, ISO_SEC, mapped, no-geometry and breakdown visualRegionExposures fixture. Set it to published exercise-version UUIDs that do, then rerun.',
      );
    return publishFixture(api, setId, version, fixture);
  }

  const candidateSets = candidateVersionSets(await catalogPreviews(api));
  let version = initialVersion;
  for (const exerciseVersionIds of candidateSets) {
    version = await replaceItems(api, setId, version, exerciseVersionIds);
    const fixture = await validatedFixture(api, setId, version, geometry);
    if (fixture) return publishFixture(api, setId, version, fixture);
  }
  await replaceItems(api, setId, version, []);
  throw new Error(
    `Catalog auto-selection evaluated ${candidateSets.length} deterministic one- and two-version published combinations but none satisfied the required FRONT, BACK, DYN_EXU, ISO_SEC, mapped, no-geometry and breakdown visual fixture. Publish suitable local catalog exercises, or set E2E_BODY_MAP_EXERCISE_VERSION_IDS to a comma-separated override, then rerun.`,
  );
}

async function createDraft(api: Api): Promise<{ setId: string; version: Version }> {
  const created = await api<{ id?: string; versions?: Version[] }>(
    '/api/v1/specialist/exercise-sets',
    'POST',
    {},
  );
  const setId = created.id;
  let version = created.versions?.[0];
  if (!setId || !version?.id || version.lockVersion == null)
    throw new Error('Fixture create response did not contain a draft version.');
  version = await api<Version>(
    `/api/v1/specialist/exercise-sets/${setId}/versions/${version.id}`,
    'PUT',
    {
      title: 'E2E body map fixture',
      profile: 'HOME',
      tags: ['e2e-body-map'],
      expectedVersion: version.lockVersion,
    },
  );
  return { setId, version };
}

async function replaceItems(
  api: Api,
  setId: string,
  version: Version,
  exerciseVersionIds: string[],
): Promise<Version> {
  for (const item of version.items ?? []) {
    if (!version.id || version.lockVersion == null || !item.id)
      throw new Error('Fixture draft lost an item ID or lock version during candidate evaluation.');
    version = await api<Version>(
      `/api/v1/specialist/exercise-sets/${setId}/versions/${version.id}/items/${item.id}?expectedVersion=${version.lockVersion}`,
      'DELETE',
    );
  }
  for (const exerciseVersionId of exerciseVersionIds) {
    if (!version.id || version.lockVersion == null)
      throw new Error('Fixture draft lost its lock version during candidate evaluation.');
    version = await api<Version>(
      `/api/v1/specialist/exercise-sets/${setId}/versions/${version.id}/items`,
      'POST',
      {
        exerciseVersionId,
        phase: 'MAIN',
        dose: { type: 'STRENGTH', sets: 2, reps: 8 },
        expectedVersion: version.lockVersion,
      },
    );
  }
  return version;
}

async function validatedFixture(
  api: Api,
  setId: string,
  version: Version,
  geometry: Array<{ code?: string }>,
) {
  if (!version.id || version.lockVersion == null)
    throw new Error('Fixture has no analyzable draft version.');
  const anatomy = await api<Anatomy>(
    `/api/v1/specialist/exercise-sets/${setId}/versions/${version.id}/anatomy`,
  );
  const values = anatomy.visualRegionExposures ?? [],
    views = new Set(values.map((value) => value.view)),
    channels = new Set(values.map((value) => value.channel));
  const geometryCodes = new Set(geometry.map((value) => value.code));
  if (
    !views.has('FRONT') ||
    !views.has('BACK') ||
    !channels.has('DYN_EXU') ||
    !channels.has('ISO_SEC') ||
    !values.some((value) => value.visualRegionCode && geometryCodes.has(value.visualRegionCode)) ||
    !values.some((value) => value.visualRegionCode && !geometryCodes.has(value.visualRegionCode)) ||
    !values.some((value) => value.breakdowns?.length) ||
    !anatomy.visualMappingVersion
  )
    return undefined;
  const orderedChannels = [...channels].filter((value): value is string => Boolean(value)).sort();
  const selectedChannel = orderedChannels[1];
  const allBackCount = values.filter((value) => value.view === 'BACK').length;
  const selectedBackCount = values.filter(
    (value) => value.view === 'BACK' && value.channel === selectedChannel,
  ).length;
  if (!selectedChannel || selectedBackCount === 0 || selectedBackCount >= allBackCount)
    return undefined;
  return {
    visualMappingVersion: anatomy.visualMappingVersion,
    selectedChannel,
    allBackCount,
    selectedBackCount,
  };
}

async function publishFixture(
  api: Api,
  setId: string,
  version: Version,
  fixture: {
    visualMappingVersion: string;
    selectedChannel: string;
    allBackCount: number;
    selectedBackCount: number;
  },
) {
  if (!version.id || version.lockVersion == null)
    throw new Error('Fixture has no publishable draft version.');
  const published = await api<Version>(
    `/api/v1/specialist/exercise-sets/${setId}/versions/${version.id}/publish`,
    'POST',
    { expectedVersion: version.lockVersion },
  );
  if (!published.id || published.status !== 'PUBLISHED')
    throw new Error(
      'Body-map fixture could not be published; configure published exercise IDs that satisfy the existing exercise-set publication policy.',
    );
  const draft = await api<Version>(
    `/api/v1/specialist/exercise-sets/${setId}/versions/${published.id}/next-draft`,
    'POST',
    {},
  );
  if (!draft.id || draft.status !== 'DRAFT')
    throw new Error('Body-map fixture could not create a post-publication draft for builder QA.');
  return { setId, draftVersionId: draft.id, publishedVersionId: published.id, ...fixture };
}

function configuredVersionIds(): string[] {
  return [
    ...new Set(
      process.env.E2E_BODY_MAP_EXERCISE_VERSION_IDS?.split(',')
        .map((value) => value.trim())
        .filter(Boolean) ?? [],
    ),
  ];
}

function candidateVersionSets(previews: ExercisePreview[]): string[][] {
  const dynamic = previews
    .filter((preview) => hasChannels(preview, 'DYN_EXU'))
    .map((preview) => preview.exerciseVersionId!);
  const isometric = previews
    .filter((preview) => hasChannels(preview, 'ISO_SEC'))
    .map((preview) => preview.exerciseVersionId!);
  const candidates = new Map<string, string[]>();
  for (const id of dynamic.filter((id) => isometric.includes(id))) candidates.set(id, [id]);
  for (const dynamicId of dynamic)
    for (const isometricId of isometric)
      if (dynamicId !== isometricId) {
        const pair = [dynamicId, isometricId].sort();
        candidates.set(pair.join(','), pair);
      }
  return [...candidates.values()].sort(
    (left, right) => left.length - right.length || left.join(',').localeCompare(right.join(',')),
  );
}

async function catalogPreviews(api: Api): Promise<ExercisePreview[]> {
  const ids = new Set<string>();
  let cursor: string | undefined;
  for (let pageNumber = 0; pageNumber < 100; pageNumber += 1) {
    const page = await api<CatalogPage>('/api/v2/exercises/search', 'POST', {
      locale: 'pl-PL',
      limit: 50,
      sort: 'NAME',
      cursor,
    });
    for (const result of page.results ?? [])
      if (result.selectable && result.exerciseVersionId) ids.add(result.exerciseVersionId);
    if (!page.hasMore) break;
    if (!page.nextCursor)
      throw new Error(
        'Catalog search reported more results without a next cursor; cannot safely auto-select body-map exercises. Set E2E_BODY_MAP_EXERCISE_VERSION_IDS to a local override.',
      );
    cursor = page.nextCursor;
    if (pageNumber === 99)
      throw new Error(
        'Catalog search exceeded 100 pages; set E2E_BODY_MAP_EXERCISE_VERSION_IDS to a bounded local override.',
      );
  }
  return Promise.all(
    [...ids]
      .sort()
      .map(
        async (exerciseVersionId) =>
          await api<ExercisePreview>(`/api/v2/exercises/versions/${exerciseVersionId}/preview`),
      ),
  );
}

function hasChannels(preview: ExercisePreview, ...channels: string[]): boolean {
  const available = new Set(
    preview.anatomy
      ?.map((contribution) => contribution.loadChannel)
      .filter((channel): channel is string => Boolean(channel)),
  );
  return channels.every((channel) => available.has(channel));
}
