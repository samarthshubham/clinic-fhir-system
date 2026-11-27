import { existsSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { repoPath } from '../src/support/repo-paths';

describe('repoPath', () => {
  it('resolves fixture files from the repository root', () => {
    expect(
      existsSync(
        repoPath('fixtures/davinci/plan-net', 'practitioner-role-aisha.json'),
      ),
    ).toBe(true);
  });
});
