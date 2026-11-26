import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  seedTerminology,
  waitForFragmentLookups,
  type SeedCodeSystem,
} from '../src/support/payer-terminology';

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

describe('payer terminology import', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
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
  it.each(['1.0', 'DELETED_old'])(
    'verifies installed complete concepts when HAPI resolves the version to %s',
    async (resolved) => {
      const source = { ...fragment, content: 'complete', version: '1.0' };
      const lookup = (valueString: string) =>
        ok({ parameter: [{ name: 'version', valueString }] });
      const f = vi
        .fn()
        .mockResolvedValueOnce(ok({ entry: [{ resource: source }] }))
        .mockResolvedValueOnce(lookup(resolved));
      if (resolved !== source.version) {
        f.mockResolvedValueOnce(ok()).mockResolvedValueOnce(
          lookup(source.version),
        );
      }
      f.mockResolvedValueOnce(ok());
      vi.stubGlobal('fetch', f);
      await seedTerminology(base, source);
      const writes = f.mock.calls.filter(([, init]) => init?.method === 'PUT');
      expect(writes).toHaveLength(resolved === source.version ? 0 : 1);
      expect(f.mock.calls.at(-1)?.[0]).toBe(
        base + '/CodeSystem/demo/$meta-add',
      );
    },
  );
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
