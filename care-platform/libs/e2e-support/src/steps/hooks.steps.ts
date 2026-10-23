import { Before } from '@badeball/cypress-cucumber-preprocessor';
import { resetContext } from '../support/context';

Before(() => {
  resetContext();
});
