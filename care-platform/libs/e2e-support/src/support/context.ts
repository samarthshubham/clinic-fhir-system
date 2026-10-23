export interface ScenarioContext {
  token: string | undefined;
  ids: Record<string, string>;
  response: Cypress.Response<any> | undefined;
}

export const runId = Date.now().toString(36);

export const ctx: ScenarioContext = { token: undefined, ids: {}, response: undefined };

export function resetContext(): void {
  ctx.token = undefined;
  ctx.ids = {};
  ctx.response = undefined;
}

export function resolveId(key: string): string {
  const id = ctx.ids[key];
  if (!id) throw new Error(`No id remembered as "${key}"`);
  return id;
}

export function resolvePlaceholders(text: string): string {
  return text.replace(/\{([A-Za-z][\w:-]*)\}/g, (_match, key: string) => {
    if (key === 'runId') return runId;
    const [name, amount] = key.split(':');
    if (name === 'daysAgo') return isoDate(-Number(amount));
    if (name === 'daysAhead') return isoDate(Number(amount));
    return resolveId(key);
  });
}

export function isoDate(offsetDays: number): string {
  return new Date(Date.now() + offsetDays * 86_400_000).toISOString().split('T')[0];
}
