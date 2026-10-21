import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

async function loadHelpersAt(isoTime: string) {
  vi.setSystemTime(new Date(isoTime));
  vi.resetModules();
  return import('../src/support/date-helpers');
}

describe('date-helpers', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('anchors to the time the module is loaded', async () => {
    const { anchor } = await loadHelpersAt('2026-03-15T10:00:00Z');
    vi.setSystemTime(new Date('2030-01-01T00:00:00Z'));
    expect(anchor.toISOString()).toBe('2026-03-15T10:00:00.000Z');
  });

  it('returns past dates as YYYY-MM-DD', async () => {
    const { daysAgo } = await loadHelpersAt('2026-03-15T10:00:00Z');
    expect(daysAgo(0)).toBe('2026-03-15');
    expect(daysAgo(1)).toBe('2026-03-14');
    expect(daysAgo(45)).toBe('2026-01-29');
  });

  it('returns future dates as YYYY-MM-DD', async () => {
    const { daysAhead } = await loadHelpersAt('2026-03-15T10:00:00Z');
    expect(daysAhead(0)).toBe('2026-03-15');
    expect(daysAhead(1)).toBe('2026-03-16');
    expect(daysAhead(17)).toBe('2026-04-01');
  });

  it('crosses month and year boundaries', async () => {
    const { daysAgo, daysAhead } = await loadHelpersAt('2026-01-01T12:00:00Z');
    expect(daysAgo(1)).toBe('2025-12-31');
    expect(daysAhead(31)).toBe('2026-02-01');
  });

  it('stays exact over a full year of offsets', async () => {
    const { daysAgo, daysAhead } = await loadHelpersAt('2026-03-15T12:00:00Z');
    expect(daysAgo(365)).toBe('2025-03-15');
    expect(daysAhead(365)).toBe('2027-03-15');
  });

  it('keeps every offset relative to the same anchor', async () => {
    const { daysAgo, daysAhead } = await loadHelpersAt('2026-03-15T10:00:00Z');
    vi.setSystemTime(new Date('2026-06-01T10:00:00Z'));
    expect(daysAgo(2)).toBe('2026-03-13');
    expect(daysAhead(2)).toBe('2026-03-17');
  });
});
