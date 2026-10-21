import { IWorldOptions } from '@cucumber/cucumber';
import { describe, expect, it, vi } from 'vitest';

const { setWorldConstructor } = vi.hoisted(() => ({
  setWorldConstructor: vi.fn(),
}));

vi.mock('@cucumber/cucumber', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@cucumber/cucumber')>()),
  setWorldConstructor,
}));

import { SeedWorld } from '../src/support/world';

const worldOptions: IWorldOptions = {
  attach: vi.fn() as unknown as IWorldOptions['attach'],
  log: vi.fn(),
  link: vi.fn(),
  parameters: { env: 'test' },
};

describe('SeedWorld', () => {
  it('registers itself as the Cucumber world', () => {
    expect(setWorldConstructor).toHaveBeenCalledWith(SeedWorld);
  });

  it('starts every scenario with empty state', () => {
    const world = new SeedWorld(worldOptions);
    expect(world.currentPatientId).toBeNull();
    expect(world.currentEncounterId).toBeNull();
    expect(world.currentEncounterDate).toBeNull();
    expect(world.currentConditionId).toBeNull();
    expect(world.currentGoalId).toBeNull();
    expect(world.currentServiceRequestId).toBeNull();
    expect(world.pendingReportBody).toBeNull();
    expect(world.allGoalIds).toEqual([]);
    expect(world.allConditionIds).toEqual([]);
  });

  it('passes Cucumber options to the base world', () => {
    const world = new SeedWorld(worldOptions);
    expect(world.parameters).toEqual({ env: 'test' });
    expect(world.log).toBe(worldOptions.log);
  });

  it('does not share collected ids between scenarios', () => {
    const first = new SeedWorld(worldOptions);
    const second = new SeedWorld(worldOptions);
    first.allGoalIds.push('goal-1');
    first.allConditionIds.push('cond-1');
    first.currentPatientId = 'patient-1';
    expect(second.allGoalIds).toEqual([]);
    expect(second.allConditionIds).toEqual([]);
    expect(second.currentPatientId).toBeNull();
  });
});
