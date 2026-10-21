import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['test/**/*.spec.ts'],
    environment: 'node',
    coverage: {
      provider: 'v8',
      include: ['src/support/**/*.ts'],
      reportsDirectory: '../../coverage/seed-demo-data',
      reporter: [['lcov', { projectRoot: '../..' }], 'text-summary'],
      thresholds: { lines: 70 },
    },
  },
});
