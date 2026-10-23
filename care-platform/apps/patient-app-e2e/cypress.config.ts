import {
  addCucumberPreprocessorPlugin,
  afterRunHandler,
  afterSpecHandler,
  beforeRunHandler,
  beforeSpecHandler,
} from '@badeball/cypress-cucumber-preprocessor';
import { createEsbuildPlugin } from '@badeball/cypress-cucumber-preprocessor/esbuild';
import createBundler from '@bahmutov/cypress-esbuild-preprocessor';
import { nxE2EPreset } from '@nx/cypress/plugins/cypress-preset';
import { defineConfig } from 'cypress';
import {
  cleanUpAfterSpec,
  e2eTasks,
  sweepBeforeRun,
  waitForUrls,
} from '@care-platform/e2e-support';

export default defineConfig({
  e2e: {
    ...nxE2EPreset(__filename, { cypressDir: 'src' }),
    baseUrl: null,
    specPattern: 'src/features/**/*.feature',
    supportFile: false,
    video: false,
    env: {
      apiUrl: 'http://localhost:9090',
      primaryApp: 'patient',
      clinicianUrl: 'http://localhost:4200',
      patientUrl: 'http://localhost:4300',
    },
    async setupNodeEvents(on, config) {
      await addCucumberPreprocessorPlugin(on, config, {
        omitBeforeRunHandler: true,
        omitAfterRunHandler: true,
        omitBeforeSpecHandler: true,
        omitAfterSpecHandler: true,
      });
      on(
        'file:preprocessor',
        createBundler({ plugins: [createEsbuildPlugin(config)] }),
      );
      on('task', e2eTasks);
      on('before:run', async () => {
        await beforeRunHandler(config);
        await waitForUrls(['http://localhost:4300']);
        await sweepBeforeRun();
      });
      on('after:run', async (results) => {
        await afterRunHandler(config, results);
      });
      on('before:spec', async (spec) => {
        await beforeSpecHandler(config, spec);
      });
      on('after:spec', async (spec, results) => {
        await afterSpecHandler(config, spec, results);
        await cleanUpAfterSpec(results.stats.failures);
      });
      return config;
    },
  },
});
