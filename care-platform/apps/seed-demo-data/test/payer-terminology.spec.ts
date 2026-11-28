import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  prepareFormularyValueSets,
  seedPasDemoValueSets,
  seedTerminology,
  waitForFragmentLookups,
  type SeedCodeSystem,
} from '../src/support/payer-terminology';

vi.mock('node:timers/promises', () => ({
  setTimeout: vi.fn().mockResolvedValue(undefined),
}));

const base = 'http://localhost:8081/fhir';
const fragment: SeedCodeSystem = {
  resourceType: 'CodeSystem',
  id: 'demo',
  url: 'http://example.org/terms',
  content: 'fragment',
  concept: [{ code: 'A', display: 'Testing, "one"' }],
};
const batch = (entry: unknown[]) => ({
  resourceType: 'Bundle',
  type: 'batch-response',
  entry,
});
const completed = {
  response: {
    status: '200 OK',
    outcome: {
      resourceType: 'OperationOutcome',
      issue: [
        { severity: 'information', diagnostics: 'Terminology Import Report' },
      ],
    },
  },
};
const ok = (data: unknown = {}) => new Response(JSON.stringify(data));

describe('PAS demo ValueSets', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('installs the limited expansions once and preserves installed canonicals', async () => {
    const resources = new Map<string, Record<string, unknown>>([
      [
        'https://valueset.x12.org/x217/005010/request/2010A/NM1/1/01/00/98',
        { id: 'licensed', version: 'official' },
      ],
    ]);
    const put = vi.fn<(value: Record<string, unknown>) => void>();
    vi.stubGlobal('fetch', (url: string, init?: RequestInit) => {
      if (init?.method === 'PUT') {
        const resource = JSON.parse(String(init.body));
        put(resource);
        resources.set(resource.url, resource);
        return Promise.resolve(ok());
      }
      const canonical = new URL(url).searchParams.get('url') ?? '';
      const resource = resources.get(canonical);
      return Promise.resolve(ok({ entry: resource ? [{ resource }] : [] }));
    });

    await seedPasDemoValueSets(base);
    await seedPasDemoValueSets(base);

    expect(put).toHaveBeenCalledTimes(5);
    expect(put).toHaveBeenCalledWith(
      expect.objectContaining({
        id: 'demo-pas-request-type',
        version: 'praxis-demo',
        expansion: expect.objectContaining({
          parameter: [{ name: 'limitedExpansion', valueBoolean: true }],
        }),
      }),
    );
    expect(
      resources.get(
        'https://valueset.x12.org/x217/005010/request/2010A/NM1/1/01/00/98',
      ),
    ).toEqual({ id: 'licensed', version: 'official' });
  });
});

describe('payer terminology import', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });
  it.each([false, true])(
    'imports the demo vocabulary, then recognizes the same source on reseed (ICD=%s)',
    async (isIcd) => {
      const source = isIcd
        ? { ...fragment, url: 'http://hl7.org/fhir/sid/icd-10-cm' }
        : fragment;
      const f = vi
        .fn()
        .mockResolvedValueOnce(new Response(null, { status: 404 }))
        .mockResolvedValueOnce(ok())
        .mockResolvedValueOnce(
          ok({ parameter: [{ name: 'jobInstanceId', valueCode: 'job-1' }] }),
        )
        .mockResolvedValueOnce(ok())
        .mockResolvedValueOnce(new Response(null, { status: 202 }))
        .mockResolvedValueOnce(ok(batch([completed])))
        .mockResolvedValueOnce(ok());
      vi.stubGlobal('fetch', f);
      await seedTerminology(base, source);
      const expectedId = isIcd ? 'icd-10-cm-praxis-demo' : 'demo';
      expect(f.mock.calls[0][0]).toBe(base + '/CodeSystem/' + expectedId);
      const uploaded = f.mock.calls[3];
      expect(uploaded[0]).toContain(isIcd ? 'icd10cm.xml' : 'concepts.csv');
      expect(uploaded[1].body).toContain(
        isIcd ? '<diag><name>A</name>' : '"Testing, ""one"""',
      );
      const header = JSON.parse(f.mock.calls[6][1].body);
      expect(header).toMatchObject({
        id: expectedId,
        url: source.url,
        content: 'not-present',
      });
      expect(header.concept).toBeUndefined();
      f.mockResolvedValueOnce(ok(header));
      await seedTerminology(base, source);
      expect(f).toHaveBeenCalledTimes(8);
    },
  );
  it('expands the published definitions in dependency order and skips the same release on reseed', async () => {
    const rxnorm = {
      ...fragment,
      concept: ['SCD', 'SCDG', 'DF'].map((code) => ({
        code,
        property: [{ code: 'TTY', valueCode: code }],
      })),
    };
    const definitions: string[] = [];
    let tag: object | undefined;
    let pendingImport = true;
    const f = vi.fn(async (url: string, init?: RequestInit) => {
      const body = init?.body ? JSON.parse(String(init.body)) : {};
      if (url.endsWith('/CodeSystem/demo'))
        return ok({ id: 'demo', meta: { tag: tag ? [tag] : [] } });
      if (url.endsWith('/$reindex-terminology')) {
        if (pendingImport) {
          pendingImport = false;
          return ok({
            parameter: [
              { name: 'success', valueBoolean: false },
              {
                name: 'message',
                valueString:
                  'Other terminology background tasks are currently running.',
              },
            ],
          });
        }
        return ok({ parameter: [{ name: 'success', valueBoolean: true }] });
      }
      if (url.includes('/ValueSet?'))
        return ok({
          entry: [
            {
              resource: {
                id: 'installed',
                compose: { include: [] },
                meta: { tag: [{ system: 'source', code: 'ig' }] },
              },
            },
          ],
        });
      if (url.endsWith('/ValueSet/installed')) {
        expect(body.compose).toEqual({ include: [] });
        expect(body.meta.tag[0]).toEqual({ system: 'source', code: 'ig' });
        return ok();
      }
      if (url.endsWith('/$invalidate-expansion')) {
        definitions.push(body.parameter[0].valueUri.split('/').pop());
        return new Response(null, {
          status: 202,
          headers: {
            'Content-Location': base + '/ValueSet/poll?jobInstanceId=job',
          },
        });
      }
      if (url.includes('poll-for-status')) return ok(batch([completed]));
      if (url.includes('/$expand?'))
        return ok({
          expansion: {
            contains: [
              {
                system: fragment.url,
                code: url.includes('FormGroup') ? 'SCDG' : 'SCD',
              },
            ],
          },
        });
      if (url.endsWith('/$meta-add')) {
        tag = body.parameter[0].valueMeta.tag[0];
        return ok();
      }
      throw new Error('Unexpected ' + url);
    });
    vi.stubGlobal('fetch', f);
    await prepareFormularyValueSets(base, rxnorm);
    expect(definitions).toEqual([
      'NonPackSemanticDrugVS',
      'SemanticDrugVS',
      'SemanticDrugFormGroupVS',
    ]);
    f.mockClear();
    await prepareFormularyValueSets(base, rxnorm);
    expect(f).toHaveBeenCalledTimes(1);
  });

  it('rejects an empty expansion for a known semantic drug', async () => {
    const f = vi
      .fn()
      .mockResolvedValueOnce(ok({ id: 'demo' }))
      .mockResolvedValueOnce(
        ok({ parameter: [{ name: 'success', valueBoolean: true }] }),
      )
      .mockResolvedValueOnce(ok({ entry: [{ resource: { id: 'published' } }] }))
      .mockResolvedValueOnce(ok())
      .mockResolvedValueOnce(
        new Response(null, {
          status: 202,
          headers: { 'Content-Location': '?jobInstanceId=test' },
        }),
      )
      .mockResolvedValueOnce(ok(batch([completed])))
      .mockResolvedValueOnce(ok({ expansion: { total: 0 } }));
    vi.stubGlobal('fetch', f);
    await expect(
      prepareFormularyValueSets(base, {
        ...fragment,
        concept: [
          { code: '197361', property: [{ code: 'TTY', valueCode: 'SCD' }] },
        ],
      }),
    ).rejects.toThrow('Incorrect expansion for NonPackSemanticDrugVS: 197361');
  });

  it('preserves concept properties used by ValueSet filters', async () => {
    const source: SeedCodeSystem = {
      ...fragment,
      concept: [{ code: 'A', property: [{ code: 'TTY', valueCode: 'SCD' }] }],
    };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 404 }))
      .mockResolvedValueOnce(ok())
      .mockResolvedValueOnce(
        ok({ parameter: [{ name: 'jobInstanceId', valueCode: 'job-1' }] }),
      )
      .mockResolvedValueOnce(ok())
      .mockResolvedValueOnce(new Response(null, { status: 202 }))
      .mockResolvedValueOnce(ok(batch([completed])))
      .mockResolvedValueOnce(ok());
    vi.stubGlobal('fetch', fetchMock);

    await seedTerminology(base, source);

    const [uploadUrl, upload] = fetchMock.mock.calls[3];
    expect(uploadUrl).toContain('filename=codesystem.json');
    expect(JSON.parse(upload.body)).toEqual(source);
  });
  it('waits for a slow batch to finish before recording the digest', async () => {
    vi.spyOn(Date, 'now').mockReturnValueOnce(0).mockReturnValue(185_000);
    const f = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 404 }))
      .mockResolvedValueOnce(ok())
      .mockResolvedValueOnce(
        ok({ parameter: [{ name: 'jobInstanceId', valueCode: 'job-1' }] }),
      )
      .mockResolvedValueOnce(ok())
      .mockResolvedValueOnce(new Response(null, { status: 202 }))
      .mockResolvedValueOnce(new Response(null, { status: 202 }))
      .mockResolvedValueOnce(ok(batch([completed])))
      .mockResolvedValueOnce(ok());
    vi.stubGlobal('fetch', f);

    await expect(seedTerminology(base, fragment)).resolves.toBeUndefined();
    expect(f.mock.calls[5][0]).toContain('poll-for-status?jobInstanceId=job-1');
    expect(f.mock.calls[6][0]).toBe(f.mock.calls[5][0]);
    expect(JSON.parse(f.mock.calls[7][1].body).meta.tag).toHaveLength(1);
  });

  it('reports the last job status when the server exceeds the import deadline', async () => {
    vi.spyOn(Date, 'now').mockReturnValueOnce(0).mockReturnValue(300_000);
    const f = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 404 }))
      .mockResolvedValueOnce(ok())
      .mockResolvedValueOnce(
        ok({ parameter: [{ name: 'jobInstanceId', valueCode: 'job-1' }] }),
      )
      .mockResolvedValueOnce(ok())
      .mockResolvedValueOnce(new Response(null, { status: 202 }))
      .mockResolvedValueOnce(
        new Response('Current step: import-concepts', { status: 202 }),
      );
    vi.stubGlobal('fetch', f);

    await expect(seedTerminology(base, fragment)).rejects.toThrow(
      'job job-1). Last response: Current step: import-concepts',
    );
    expect(f).toHaveBeenCalledTimes(6);
  });

  it('requires fragment codes to resolve through the same unversioned lookup as PAS', async () => {
    const response = ok({
      parameter: [{ name: 'version', valueString: 'praxis-demo' }],
    });
    const f = vi.fn().mockResolvedValue(response);
    vi.stubGlobal('fetch', f);
    await waitForFragmentLookups(base, [
      fragment,
      { ...fragment, content: 'complete' },
    ]);
    expect(f).toHaveBeenCalledOnce();
    const url = new URL(f.mock.calls[0][0]);
    expect(url.searchParams.get('code')).toBe('A');
    expect(url.searchParams.has('version')).toBe(false);
  });

  it('stores complete concepts under its published version and tags its existing server ID', async () => {
    const source = {
      ...fragment,
      content: 'complete',
      version: '1.0',
      concept: [{ code: 'A' }, { code: 'B' }],
    };
    const found = () =>
      ok({
        resourceType: 'Parameters',
        parameter: [{ name: 'version', valueString: source.version }],
      });
    const f = vi
      .fn()
      .mockResolvedValueOnce(ok({ entry: [{ resource: { id: '123' } }] }))
      .mockResolvedValueOnce(ok())
      .mockResolvedValueOnce(found())
      .mockResolvedValueOnce(found())
      .mockResolvedValueOnce(ok());
    vi.stubGlobal('fetch', f);
    await seedTerminology(base, source);
    expect(f.mock.calls[1][0]).toBe(base + '/CodeSystem/123');
    expect(f.mock.calls[1][1].method).toBe('PUT');
    expect(JSON.parse(f.mock.calls[1][1].body)).toEqual({
      ...source,
      id: '123',
    });
    expect(f.mock.calls[3][0]).toContain('version=1.0&code=B');
    expect(f.mock.calls[4][0]).toBe(base + '/CodeSystem/123/$meta-add');
    const meta = JSON.parse(f.mock.calls[4][1].body).parameter[0].valueMeta;
    f.mockResolvedValueOnce(ok({ entry: [{ resource: { id: '123', meta } }] }));
    await seedTerminology(base, source);
    expect(f).toHaveBeenCalledTimes(6);
  });
  it('writes an untagged package resource before probing its native lookup', async () => {
    const source = { ...fragment, content: 'complete', version: '1.0' };
    const lookup = (valueString: string) =>
      ok({ parameter: [{ name: 'version', valueString }] });
    const f = vi
      .fn()
      .mockResolvedValueOnce(ok({ entry: [{ resource: source }] }))
      .mockResolvedValueOnce(ok())
      .mockResolvedValueOnce(lookup('DELETED_old'))
      .mockResolvedValueOnce(lookup(source.version))
      .mockResolvedValueOnce(ok());
    vi.stubGlobal('fetch', f);

    await seedTerminology(base, source);
    expect(f.mock.calls[1][1].method).toBe('PUT');
    expect(f.mock.calls[2][0]).toContain('/CodeSystem/$lookup?');
    expect(f.mock.calls[3][0]).toBe(f.mock.calls[2][0]);
    expect(f.mock.calls[4][0]).toBe(base + '/CodeSystem/demo/$meta-add');
  });

  it.each([
    ['500 Internal Server Error', 'error', 'HAPI-0844'],
    ['200 OK', 'error', 'HAPI-0844'],
    ['500 Internal Server Error', 'information', '500 Internal Server Error'],
    ['200 OK', 'warning', 'import failed'],
  ])(
    'rejects an inner failure (%s, %s) even when the poll returns HTTP 200',
    async (status, severity, message) => {
      const outcome = {
        resourceType: 'OperationOutcome',
        issue: [{ severity, diagnostics: 'HAPI-0844' }],
      };
      const f = vi
        .fn()
        .mockResolvedValueOnce(new Response(null, { status: 404 }))
        .mockResolvedValueOnce(ok())
        .mockResolvedValueOnce(
          ok({ parameter: [{ name: 'jobInstanceId', valueCode: 'job-1' }] }),
        )
        .mockResolvedValueOnce(ok())
        .mockResolvedValueOnce(new Response(null, { status: 202 }))
        .mockResolvedValueOnce(
          ok(batch([completed, { response: { status, outcome } }])),
        );
      vi.stubGlobal('fetch', f);
      await expect(seedTerminology(base, fragment)).rejects.toThrow(message);
      expect(f).toHaveBeenCalledTimes(6);
    },
  );
  it('reports a failed import without marking the digest as installed', async () => {
    const f = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 404 }))
      .mockResolvedValueOnce(ok())
      .mockResolvedValueOnce(new Response('Bad system', { status: 422 }));
    vi.stubGlobal('fetch', f);
    await expect(seedTerminology(base, fragment)).rejects.toThrow('Bad system');
    expect(f).toHaveBeenCalledTimes(3);
  });
});
