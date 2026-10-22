import { DEMO_ACCOUNTS } from './demo-accounts.prod';

describe('production demo accounts', () => {
  it('should ship no demo credentials', () => {
    expect(DEMO_ACCOUNTS).toEqual([]);
  });
});
