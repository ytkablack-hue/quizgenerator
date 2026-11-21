export const LIMITS = Object.freeze({userDay: 20, userMinute: 3, ipDay: 60, ipMinute: 10, globalDay: 200, authMinute: 30});

/** One globally named SQLite Durable Object: atomic quota reservations survive isolates/restarts.
 * Counts attempts before Gemini; never retries or refunds upstream failures automatically.
 * Only hashes and counters, no documents, email addresses, tokens or raw IPs.
 */
export class AiQuota {
  constructor(state) { this.state = state; }
  async fetch(request) {
    const data = await request.json();
    if (!/^[a-f0-9]{64}$/.test(data.ip || '') || (data.uid && !/^[a-f0-9]{64}$/.test(data.uid))) return new Response(null, {status: 400});
    const now = Date.now(), day = Math.floor(now / 86400000), minute = Math.floor(now / 60000);
    const buckets = data.phase === 'auth'
      ? [[`auth:${minute}:${data.ip}`, LIMITS.authMinute]]
      : [[`user-day:${day}:${data.uid}`, LIMITS.userDay], [`user-minute:${minute}:${data.uid}`, LIMITS.userMinute],
         [`ip-day:${day}:${data.ip}`, LIMITS.ipDay], [`ip-minute:${minute}:${data.ip}`, LIMITS.ipMinute], [`global:${day}`, LIMITS.globalDay]];
    if (data.phase !== 'auth' && !data.uid) return new Response(null, {status: 400});
    if (data.phase !== 'auth' && !/^[a-f0-9-]{36}$/.test(data.requestId || '')) return new Response(null, {status: 400});
    const replay = data.phase === 'auth' ? null : `replay:${day}:${data.uid}:${data.requestId}`;
    const accepted = await this.state.storage.transaction(async tx => {
      const counts = await tx.get([...buckets.map(([key]) => key), ...(replay ? [replay] : [])]);
      if (replay && counts.has(replay)) return 'replay';
      if (buckets.some(([key, limit]) => (counts.get(key) || 0) >= limit)) return false;
      await tx.put(Object.fromEntries(buckets.map(([key]) => [key, (counts.get(key) || 0) + 1])));
      if (replay) await tx.put({[replay]: 1});
      return true;
    });
    if (await this.state.storage.getAlarm() == null) await this.state.storage.setAlarm(now + 60000);
    return new Response(null, {status: accepted === 'replay' ? 409 : accepted ? 204 : 429, headers: {'Retry-After': '60'}});
  }
  async alarm() {
    const day = Math.floor(Date.now() / 86400000), minute = Math.floor(Date.now() / 60000);
    for (const [key] of await this.state.storage.list()) {
      const [kind, stamp] = key.split(':');
      if (Number(stamp) < (kind.includes('minute') || kind === 'auth' ? minute : day)) await this.state.storage.delete(key);
    }
    if ((await this.state.storage.list()).size) await this.state.storage.setAlarm(Date.now() + 60000);
  }
}
