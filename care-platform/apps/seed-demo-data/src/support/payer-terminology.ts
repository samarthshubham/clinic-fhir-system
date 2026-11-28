import { createHash } from 'node:crypto';
import { readdir, readFile } from 'node:fs/promises';
import { setTimeout as delay } from 'node:timers/promises';
import type { FhirResource } from './api-client';
import { repoPath } from './repo-paths';

interface SeedCodeSystem extends FhirResource {
  id: string;
  url: string;
  version?: string;
  concept: {
    code: string;
    display?: string;
    property?: { code: string; valueCode?: string; [key: string]: unknown }[];
  }[];
}

const icd10cm = 'http://hl7.org/fhir/sid/icd-10-cm';
const sourceHash =
  'http://praxishealthplan.example/fhir/CodeSystem/seed-fragment-digest';
const snapshotHash =
  'http://praxishealthplan.example/fhir/CodeSystem/seed-snapshot-digest';
const xml = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const csv = (s: string) => '"' + s.replace(/"/g, '""') + '"';

function importFile(fragment: SeedCodeSystem) {
  if (fragment.concept.some((concept) => concept.property?.length)) {
    // The concepts.csv format does not carry properties used by ValueSet filters.
    return { name: 'codesystem.json', data: JSON.stringify(fragment) };
  }
  if (fragment.url === icd10cm) {
    const diagnoses = fragment.concept
      .map(
        (c) =>
          '<diag><name>' +
          xml(c.code) +
          '</name><desc>' +
          xml(c.display ?? c.code) +
          '</desc></diag>',
      )
      .join('');
    return {
      name: 'icd10cm.xml',
      data:
        '<ICD10CM.tabular><chapter><section>' +
        diagnoses +
        '</section></chapter></ICD10CM.tabular>',
    };
  }
  return {
    name: 'concepts.csv',
    data:
      [
        'CODE,DISPLAY',
        ...fragment.concept.map((c) =>
          [c.code, c.display ?? c.code].map(csv).join(','),
        ),
      ].join('\n') + '\n',
  };
}

async function checked(url: string, init?: RequestInit) {
  const response = await fetch(url, init);
  if (!response.ok)
    throw new Error(
      'Terminology ' +
        response.status +
        ': ' +
        (await response.text()).slice(0, 1500),
    );
  return response;
}

interface OperationOutcome {
  resourceType?: string;
  issue?: {
    severity: string;
    diagnostics?: string;
    details?: { text?: string };
  }[];
}

interface BatchResult {
  resourceType?: string;
  type?: string;
  entry?: {
    response?: { status?: string; outcome?: OperationOutcome };
    resource?: OperationOutcome;
  }[];
}

function checkBatchResult(result: BatchResult) {
  if (
    result.resourceType !== 'Bundle' ||
    result.type !== 'batch-response' ||
    !result.entry?.length
  )
    throw new Error('Missing terminology batch-response entries');
  for (const entry of result.entry) {
    const status = entry.response?.status ?? '';
    const issues = [entry.response?.outcome, entry.resource]
      .filter((resource) => resource?.resourceType === 'OperationOutcome')
      .flatMap((outcome) => outcome?.issue ?? []);
    const error = issues.find(
      (issue) => issue.severity === 'fatal' || issue.severity === 'error',
    );
    // A cancelled HAPI job also returns 200, with a warning instead of a completion report.
    const completed = issues.some((issue) => issue.severity === 'information');
    if (!/^2\d\d(?:\s|$)/.test(status) || error || !completed)
      throw new Error(
        `Terminology entry ${status}: ${error?.diagnostics ?? error?.details?.text ?? 'import failed'}`,
      );
  }
}

async function importSnapshot(
  base: string,
  system: string,
  version: string,
  file: { name: string; data: string },
) {
  const jsonHeaders = { 'Content-Type': 'application/fhir+json' };
  const op = base + '/CodeSystem/$hapi.fhir.upload-terminology.';
  const result = await checked(op + 'create-job', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({
      resourceType: 'Parameters',
      parameter: [
        { name: 'system', valueUri: system },
        { name: 'version', valueCode: version },
        { name: 'mode', valueCode: 'SNAPSHOT' },
      ],
    }),
  });
  const output = (await result.json()) as {
    parameter?: { name: string; valueCode?: string }[];
  };
  const job = output.parameter?.find(
    (p) => p.name === 'jobInstanceId',
  )?.valueCode;
  if (!job) throw new Error('HAPI returned no terminology job id');
  const query = '?jobInstanceId=' + encodeURIComponent(job);
  await checked(op + 'attach-file' + query + '&filename=' + file.name, {
    method: 'POST',
    headers: { 'Content-Type': 'application/octet-stream' },
    body: file.data,
  });
  await checked(op + 'start-job' + query, {
    method: 'POST',
    headers: { Prefer: 'respond-async' },
  });
  await pollTerminologyJob(
    op + 'poll-for-status' + query,
    `importing ${system}|${version} (job ${job})`,
  );
}

async function pollTerminologyJob(url: string, operation: string) {
  const until = Date.now() + 300_000;
  for (;;) {
    const status = await checked(url);
    if (status.status !== 202) {
      checkBatchResult((await status.json()) as BatchResult);
      return;
    }
    if (Date.now() >= until)
      throw new Error(
        `Timed out ${operation}. Last response: ${(await status.text()).slice(0, 1500)}`,
      );
    await delay(1000);
  }
}

async function hasConcepts(
  base: string,
  system: SeedCodeSystem,
  version: string,
  versioned: boolean,
) {
  for (const { code } of system.concept) {
    const query = new URLSearchParams({ system: system.url });
    if (versioned) query.set('version', version);
    query.set('code', code);
    const response = await fetch(`${base}/CodeSystem/$lookup?${query}`);
    if (response.status === 404) return false;
    if (!response.ok)
      throw new Error(
        `Terminology lookup failed: ${response.status} ${await response.text()}`,
      );
    const result = (await response.json()) as {
      parameter?: { name: string; valueString?: string }[];
    };
    const resolved = result.parameter?.find(
      (p) => p.name === 'version',
    )?.valueString;
    // During reindexing HAPI can cache a reference to its retired version.
    if (resolved?.startsWith('DELETED_')) return false;
    if (resolved !== version)
      throw new Error(
        `Terminology lookup resolved ${system.url}|${version}#${code} to ${resolved ?? 'no version'}`,
      );
  }
  return true;
}

async function waitForConcepts(
  base: string,
  source: SeedCodeSystem,
  version: string,
  versioned: boolean,
) {
  // HAPI can retain a pre-import lookup miss for ten minutes.
  const until = Date.now() + 610_000;
  while (!(await hasConcepts(base, source, version, versioned))) {
    if (Date.now() >= until)
      throw new Error(
        `Missing concepts after CodeSystem update: ${source.url}|${version}`,
      );
    await delay(5000);
  }
}

export async function waitForFragmentLookups(
  base: string,
  systems: SeedCodeSystem[],
) {
  // PAS bindings use the default native version. HAPI caches that lookup separately.
  await Promise.all(
    systems
      .filter((system) => system.content === 'fragment')
      .map((system) =>
        waitForConcepts(base, system, system.version ?? 'praxis-demo', false),
      ),
  );
}

type TaggedResource = {
  id: string;
  meta?: { tag?: { system?: string; code?: string }[] };
};

function installed(
  resource: TaggedResource | undefined,
  digest: string,
  system: string,
) {
  return resource?.meta?.tag?.some(
    (tag) => tag.system === system && tag.code === digest,
  );
}

async function seedComplete(
  base: string,
  source: SeedCodeSystem,
  version: string,
  digest: string,
) {
  const query = new URLSearchParams({ url: source.url, version });
  const search = await checked(`${base}/CodeSystem?${query}`);
  const found = (await search.json()) as {
    entry?: { resource: TaggedResource }[];
  };
  if ((found.entry?.length ?? 0) > 1)
    throw new Error(`Duplicate CodeSystem: ${source.url}|${version}`);
  const existing = found.entry?.[0]?.resource;
  if (installed(existing, digest, snapshotHash)) return;
  const id = existing?.id ?? source.id;
  const url = base + '/CodeSystem/' + id;
  // On cold HAPI a pre-update lookup miss can be cached for ten minutes.
  await checked(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/fhir+json' },
    body: JSON.stringify({ ...source, id }),
  });
  await waitForConcepts(base, source, version, true);
  await addTag(url, snapshotHash, digest);
}

async function addTag(url: string, system: string, code: string) {
  await checked(url + '/$meta-add', {
    method: 'POST',
    headers: { 'Content-Type': 'application/fhir+json' },
    body: JSON.stringify({
      resourceType: 'Parameters',
      parameter: [
        {
          name: 'meta',
          valueMeta: { tag: [{ system, code }] },
        },
      ],
    }),
  });
}

export async function seedTerminology(base: string, fragment: SeedCodeSystem) {
  const { concept, ...metadata } = fragment;
  if (!concept?.length)
    throw new Error('Missing terminology concepts: ' + fragment.id);
  const digest = createHash('sha256')
    .update(JSON.stringify(fragment))
    .digest('hex');
  if (metadata.content === 'complete' && fragment.version) {
    await seedComplete(base, fragment, fragment.version, digest);
    return;
  }
  if (metadata.content !== 'fragment')
    throw new Error(
      'Expected a fragment or versioned complete CodeSystem: ' + fragment.id,
    );
  const version = fragment.version ?? 'praxis-demo';
  // The HAPI ICD-10-CM importer assigns this ID when staging its native index.
  const id = fragment.url === icd10cm ? 'icd-10-cm-' + version : fragment.id;
  const url = base + '/CodeSystem/' + id;
  const existing = await fetch(url);
  if (existing.ok) {
    if (
      installed((await existing.json()) as TaggedResource, digest, sourceHash)
    )
      return;
  } else if (existing.status !== 404)
    throw new Error('Reading ' + url + ' failed: ' + existing.status);
  const header = { ...metadata, id, version, content: 'not-present' };
  const jsonHeaders = { 'Content-Type': 'application/fhir+json' };
  const put = async (body: object) =>
    checked(url, {
      method: 'PUT',
      headers: jsonHeaders,
      body: JSON.stringify(body),
    });
  await put(header);
  await importSnapshot(base, fragment.url, version, importFile(fragment));
  await put({
    ...header,
    meta: { tag: [{ system: sourceHash, code: digest }] },
  });
}
interface PasDemoValueSet extends FhirResource {
  id: string;
  url: string;
}

export async function seedPasDemoValueSets(base: string): Promise<void> {
  const directory = repoPath('terminology/valuesets/pas');
  const files = (await readdir(directory))
    .filter((file) => file.endsWith('.json'))
    .sort();
  for (const file of files) {
    const resource = JSON.parse(
      await readFile(repoPath('terminology/valuesets/pas', file), 'utf8'),
    ) as PasDemoValueSet;
    const query = new URLSearchParams({ url: resource.url, _count: '1' });
    const response = await checked(base + '/ValueSet?' + query);
    const existing = (await response.json()) as { entry?: unknown[] };
    // A licensed terminology installation may already resolve this canonical.
    if (existing.entry?.length) continue;
    await checked(base + '/ValueSet/' + resource.id, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/fhir+json' },
      body: JSON.stringify(resource),
    });
  }
}

const formularyRelease = '2.1.0';
const expansionHash =
  'http://praxishealthplan.example/fhir/CodeSystem/seed-formulary-expansion';

async function expandFormularyValueSet(
  base: string,
  url: string,
  digest: string,
) {
  const search = new URLSearchParams({ url, version: formularyRelease });
  const bundle = (await (
    await checked(base + '/ValueSet?' + search)
  ).json()) as { entry?: { resource: TaggedResource }[] };
  if (bundle.entry?.length !== 1)
    throw new Error('Expected one installed ValueSet: ' + url);
  const valueSet = bundle.entry[0].resource;
  const jsonHeaders = { 'Content-Type': 'application/fhir+json' };
  // Existing databases installed these definitions before native ValueSet storage was enabled.
  const tags = (valueSet.meta?.tag ?? []).filter(
    (t) => t.system !== expansionHash,
  );
  await checked(base + '/ValueSet/' + encodeURIComponent(valueSet.id), {
    method: 'PUT',
    headers: jsonHeaders,
    body: JSON.stringify({
      ...valueSet,
      meta: {
        ...valueSet.meta,
        tag: [...tags, { system: expansionHash, code: digest }],
      },
    }),
  });
  const job = await checked(base + '/ValueSet/$invalidate-expansion', {
    method: 'POST',
    headers: { ...jsonHeaders, Prefer: 'respond-async' },
    body: JSON.stringify({
      resourceType: 'Parameters',
      parameter: [
        { name: 'url', valueUri: url },
        { name: 'version', valueCode: formularyRelease },
      ],
    }),
  });
  const location = job.headers.get('Content-Location');
  const id =
    location && new URL(location, base).searchParams.get('jobInstanceId');
  if (!id) throw new Error('Missing expansion job ID: ' + url);
  const poll =
    base +
    '/ValueSet/$hapi.fhir.invalidate-expansion.poll-for-status?jobInstanceId=' +
    encodeURIComponent(id);
  await pollTerminologyJob(poll, 'expanding ' + url);
}

async function reindexTerminology(base: string) {
  // HAPI defers a reindex while background terminology imports finish.
  const until = Date.now() + 300_000;
  for (;;) {
    const response = await checked(base + '/$reindex-terminology', {
      method: 'POST',
      headers: { 'Content-Type': 'application/fhir+json' },
      body: JSON.stringify({ resourceType: 'Parameters' }),
    });
    const result = (await response.json()) as {
      parameter?: {
        name: string;
        valueBoolean?: boolean;
        valueString?: string;
      }[];
    };
    if (result.parameter?.some((p) => p.name === 'success' && p.valueBoolean))
      return;
    const message =
      result.parameter?.find((p) => p.name === 'message')?.valueString ??
      'No result';
    if (
      !message.includes('terminology background tasks') ||
      Date.now() >= until
    )
      throw new Error('Terminology reindex failed: ' + message);
    await delay(5000);
  }
}

export async function prepareFormularyValueSets(
  base: string,
  rxnorm: SeedCodeSystem,
) {
  const digest = createHash('sha256')
    .update(formularyRelease + JSON.stringify(rxnorm))
    .digest('hex');
  const resourceUrl = base + '/CodeSystem/' + rxnorm.id;
  const source = (await (await checked(resourceUrl)).json()) as TaggedResource;
  if (installed(source, digest, expansionHash)) return;
  await reindexTerminology(base);
  const termTypes = {
    NonPackSemanticDrugVS: ['SCD', 'SBD'],
    SemanticDrugVS: ['SCD', 'SBD', 'GPCK', 'BPCK'],
    SemanticDrugFormGroupVS: ['SCDG', 'SBDG'],
  };
  for (const [name, types] of Object.entries(termTypes)) {
    const url =
      'http://hl7.org/fhir/us/davinci-drug-formulary/ValueSet/' + name;
    await expandFormularyValueSet(base, url, digest);
    const query = new URLSearchParams({
      url,
      valueSetVersion: formularyRelease,
      count: '1000',
    });
    const expanded = (await (
      await checked(base + '/ValueSet/$expand?' + query)
    ).json()) as {
      expansion?: { contains?: { system: string; code: string }[] };
    };
    const codes = new Set(
      (expanded.expansion?.contains ?? [])
        .filter((c) => c.system === rxnorm.url)
        .map((c) => c.code),
    );
    for (const concept of rxnorm.concept) {
      const belongs =
        concept.property?.some(
          (p) => p.code === 'TTY' && types.includes(p.valueCode ?? ''),
        ) ?? false;
      if (codes.has(concept.code) !== belongs)
        throw new Error(
          'Incorrect expansion for ' + name + ': ' + concept.code,
        );
    }
  }
  await addTag(resourceUrl, expansionHash, digest);
}

export type { SeedCodeSystem };
