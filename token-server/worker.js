/**
 * LanPTT token server - runs on Cloudflare Workers (free tier).
 *
 * Mints short-lived LiveKit join tokens so the API secret never ships in the app.
 * Zero npm dependencies: JWT is signed by hand with the Web Crypto API.
 *
 * Endpoint: POST /token  { "room": "office", "identity": "andre" }
 *   -> { "token": "eyJ...", "url": "wss://xxx.livekit.cloud" }
 */

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
};

const ID_RE = /^[a-zA-Z0-9 _-]{1,64}$/;

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json', ...CORS },
  });
}

function b64url(bytes) {
  let s = '';
  const arr = new Uint8Array(bytes);
  for (let i = 0; i < arr.length; i++) s += String.fromCharCode(arr[i]);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function hs256(payload, secret) {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign'],
  );
  const header = b64url(enc.encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' })));
  const body = b64url(enc.encode(JSON.stringify(payload)));
  const sig = await crypto.subtle.sign('HMAC', key, enc.encode(`${header}.${body}`));
  return `${header}.${body}.${b64url(sig)}`;
}

export default {
  async fetch(request, env) {
    if (request.method === 'OPTIONS') return new Response(null, { headers: CORS });

    const url = new URL(request.url);
    if (url.pathname !== '/token' || request.method !== 'POST') {
      return json({ error: 'Use POST /token with JSON {room, identity}' }, 404);
    }

    let params;
    try {
      params = await request.json();
    } catch {
      return json({ error: 'Invalid JSON body' }, 400);
    }

    const room = String(params.room || '').trim();
    const identity = String(params.identity || '').trim();
    if (!room || !identity) return json({ error: 'room and identity are required' }, 400);
    if (!ID_RE.test(room) || !ID_RE.test(identity)) {
      return json({ error: 'room/identity: letters, numbers, space, _ or -, max 64 chars' }, 400);
    }
    if (!env.LIVEKIT_API_KEY || !env.LIVEKIT_API_SECRET || !env.LIVEKIT_URL) {
      return json({ error: 'Worker misconfigured: set LIVEKIT_URL/KEY/SECRET' }, 500);
    }

    // TODO(private comms): check a shared passcode or per-employee allowlist here
    // before minting, e.g. `if (params.passcode !== env.TEAM_PASSCODE) return 403`.

    const now = Math.floor(Date.now() / 1000);
    const token = await hs256(
      {
        iss: env.LIVEKIT_API_KEY,
        sub: identity,
        name: identity,
        nbf: now - 5,
        exp: now + 6 * 3600, // 6 hours, like the official server SDK default
        video: {
          room,
          roomJoin: true,
          canPublish: true,
          canSubscribe: true,
          canUpdateOwnMetadata: true,
        },
      },
      env.LIVEKIT_API_SECRET,
    );

    return json({ token, url: env.LIVEKIT_URL });
  },
};
