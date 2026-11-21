// Firebase ID tokens, not custom passwords. Keys are fetched only from Google's fixed endpoint.
const JWKS = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
let cached;
function decode(value) {
  if (!/^[\w-]+$/.test(value)) throw Error('invalid token');
  const padded = value.replace(/-/g, '+').replace(/_/g, '/');
  return Uint8Array.from(atob(padded), c => c.charCodeAt(0));
}
export async function verifyToken(token, project, {fetcher = fetch, now = Date.now(), keys} = {}) {
  if (!project || typeof token !== 'string' || token.length > 8192) throw Error('invalid token');
  const parts = token.split('.');
  if (parts.length !== 3) throw Error('invalid token');
  const header = JSON.parse(new TextDecoder().decode(decode(parts[0])));
  const claims = JSON.parse(new TextDecoder().decode(decode(parts[1])));
  const time = Math.floor(now / 1000);
  if (header.alg !== 'RS256' || typeof header.kid !== 'string' || header.kid.length > 128 || header.jku || header.jwk || header.x5u || header.crit ||
      claims.aud !== project || claims.iss !== `https://securetoken.google.com/${project}` ||
      typeof claims.sub !== 'string' || !claims.sub.length || claims.sub.length > 128 ||
      !Number.isInteger(claims.exp) || claims.exp <= time || !Number.isInteger(claims.iat) || claims.iat > time ||
      !Number.isInteger(claims.auth_time) || claims.auth_time > time || claims.auth_time < 0 ||
      claims.exp <= claims.iat || claims.exp - claims.iat > 3700 ||
      claims.firebase?.sign_in_provider !== 'password') throw Error('invalid token');
  if (!keys) {
    if (!cached || cached.expires <= now) {
      const response = await fetcher(JWKS, {signal: AbortSignal.timeout(8000), redirect: 'error'});
      if (!response.ok) throw Error('auth unavailable');
      const data = await response.json();
      if (!Array.isArray(data.keys) || data.keys.length > 10) throw Error('auth unavailable');
      const maxAge = Number(response.headers.get('cache-control')?.match(/max-age=(\d+)/)?.[1] || 300);
      cached = {keys: data.keys, expires: now + Math.min(maxAge, 3600) * 1000};
    }
    keys = cached.keys;
  }
  const jwk = keys.find(key => key.kid === header.kid && key.kty === 'RSA' && key.alg === 'RS256');
  if (!jwk) { cached = undefined; throw Error('invalid token'); }
  const key = await crypto.subtle.importKey('jwk', jwk, {name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256'}, false, ['verify']);
  const valid = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, decode(parts[2]), new TextEncoder().encode(parts[0] + '.' + parts[1]));
  if (!valid) throw Error('invalid token');
  if (claims.email_verified !== true) throw Error('email unverified');
  return claims;
}

export async function activeUser(token, claims, apiKey, fetcher = fetch) {
  // Lookup rejects deleted/disabled users and revoked sessions; do not cache positive results.
  const response = await fetcher('https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=' + encodeURIComponent(apiKey), {
    method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({idToken: token}),
    signal: AbortSignal.timeout(8000), redirect: 'error',
  });
  if (!response.ok) throw Error(response.status >= 500 || response.status === 429 ? 'auth unavailable' : 'invalid session');
  const user = (await response.json()).users?.[0];
  if (!user || user.localId !== claims.sub || user.disabled || user.emailVerified !== true ||
      Number(user.validSince || 0) > claims.auth_time) throw Error('invalid session');
  return user;
}

export async function digest(value) {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return Array.from(new Uint8Array(hash), b => b.toString(16).padStart(2, '0')).join('');
}
