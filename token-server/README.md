# LanPTT token server (Cloudflare Workers, free)

Mints short-lived LiveKit join tokens. The app calls this instead of
holding your LiveKit API secret.

## 1. Create a LiveKit Cloud project (~3 min, free sandbox)

1. Go to **https://cloud.livekit.io** and sign up.
2. **Create project** → pick the region closest to your office.
3. Copy the project **URL** (`wss://xxxx.livekit.cloud`).
4. Go to **Settings → Keys → Create key** → copy the **API Key** and
   **API Secret** (secret is shown once).

## 2. Deploy the worker

```bash
cd token-server
npm install -g wrangler
wrangler login
```

Put your URL in `wrangler.toml` (`LIVEKIT_URL`), then store the key/secret
(they stay in Cloudflare, never in git):

```bash
wrangler secret put LIVEKIT_API_KEY
wrangler secret put LIVEKIT_API_SECRET
wrangler deploy
```

Note the worker URL, e.g. `https://lanptt-tokens.<you>.workers.dev`.

## 3. Point the app at it

In the app's **Cloud mode** screen enter:

- LiveKit URL: `wss://xxxx.livekit.cloud`
- Token server: `https://lanptt-tokens.<you>.workers.dev`
- Room: e.g. `office` (auto-created on first join)
- Your name: e.g. `andre`

Hit **Join**, then hold the button to talk. Everyone in the same room hears you.

## Test the worker directly

```bash
curl -X POST https://lanptt-tokens.<you>.workers.dev/token \
  -H "Content-Type: application/json" \
  -d '{"room":"office","identity":"test"}'
```

Should return `{"token":"eyJ...","url":"wss://..."}`.
