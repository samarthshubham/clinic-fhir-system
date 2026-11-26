import { defineConfig } from 'vitest/config';

export default defineConfig({
  resolve: { tsconfigPaths: true },
  test: {
    include: ['test/**/*.spec.ts'],
    environment: 'node',
    coverage: {
      provider: 'v8',
      include: ['src/tasks.ts'],
      reportsDirectory: '../../coverage/e2e-support',
      reporter: [['lcov', { projectRoot: '../..' }], 'text-summary'],
      thresholds: { lines: 90 },
    },
  },
});
