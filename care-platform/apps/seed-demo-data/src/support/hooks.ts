import { BeforeAll } from '@cucumber/cucumber';
import { apiClient } from './api-client';

BeforeAll(async () => {
  await apiClient.login();
});
