import { resolve } from 'node:path';

const REPO_ROOT = resolve(__dirname, '../../../../..');

export function repoPath(...parts: string[]): string {
  return resolve(REPO_ROOT, ...parts);
}
