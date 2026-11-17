import { randomBytes } from 'node:crypto';
const state = randomBytes(32).toString('base64url');
const config = await (await fetch('http://localhost:8001/api/v1/auth/config')).json();
const google = await (
  await fetch('http://localhost:8001/api/v1/auth/google?codeChallenge=' + 'A'.repeat(43))
).json();
const authorize = new URL(google.authorizationUrl),
  callback = new URL(authorize.searchParams.get('redirect_to'));
callback.searchParams.set('state', state);
authorize.searchParams.set('redirect_to', callback.href);
const response = await fetch(authorize, { redirect: 'manual' });
const provider = new URL(response.headers.get('location'));
const providerState = provider.searchParams.get('state') ?? '';
let payload = {};
try {
  payload = JSON.parse(Buffer.from(providerState.split('.')[1], 'base64url').toString());
} catch {}
const referrer = payload.referrer ?? payload.redirect_to;
console.log(
  JSON.stringify(
    {
      googleEnabled: config.googleEnabled,
      confirmationRequired: config.confirmationRequired,
      configuredCallback: config.callbackUrl,
      authorizeStatus: response.status,
      providerHost: provider.hostname,
      callbackPreserved: referrer ? referrer === callback.href : null,
      interactiveCallbackVerified: false,
    },
    null,
    2,
  ),
);
