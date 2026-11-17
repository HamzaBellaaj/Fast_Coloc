import { test, expect, Page } from '@playwright/test';
import { readFileSync, mkdirSync } from 'node:fs';
import { randomUUID } from 'node:crypto';

// Operator credentials stay in Node memory. They are never browser environment variables.
const config = Object.fromEntries(
  readFileSync('../backend/.env.local', 'utf8')
    .split(/\r?\n/)
    .filter((line) => /^[A-Z_]+=/.test(line))
    .map((line) => {
      const i = line.indexOf('=');
      return [line.slice(0, i), line.slice(i + 1).trim()];
    }),
);
const upstream = 'https://nklsbubnyznlfpxpxsrp.supabase.co';
const run = randomUUID();
const name = 'Web' + run.slice(0, 8);
const email = `fastcoloc-web-${run}@example.com`;
let password = 'Fc!aA9' + randomUUID();
let userId = '';
async function operator(
  method: string,
  path: string,
  body?: unknown,
): Promise<Record<string, unknown>> {
  const response = await fetch(upstream + path, {
    method,
    headers: {
      apikey: config['FASTCOLOC_SUPABASE_SECRET_KEY'],
      Authorization: 'Bearer ' + config['FASTCOLOC_SUPABASE_SECRET_KEY'],
      'Content-Type': 'application/json',
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok && response.status !== 404)
    throw new Error(`Fixture operation ${method} failed: HTTP ${response.status}`);
  return response.status === 204 ? {} : ((await response.json()) as Record<string, unknown>);
}
async function login(page: Page, userEmail = email, userPassword = password) {
  await page.goto('/login');
  await page.getByLabel('Adresse e-mail', { exact: true }).fill(userEmail);
  await page.getByLabel('Mot de passe', { exact: true }).fill(userPassword);
  await page.getByRole('button', { name: 'Se connecter →', exact: true }).click();
}
async function selectFirst(page: Page, id: string) {
  await expect(page.locator(id + ' option').nth(1)).toBeAttached();
  const value = await page
    .locator(id + ' option')
    .nth(1)
    .getAttribute('value');
  await page.locator(id).selectOption(value!);
}
async function validPage(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(
    await page.evaluate(() => {
      const ids = [...document.querySelectorAll('[id]')].map((e) => e.id);
      return ids.length === new Set(ids).size;
    }),
  ).toBe(true);
  expect(
    await page.evaluate(() =>
      [...document.querySelectorAll('label[for]')].every(
        (label) => (label as HTMLLabelElement).control !== null,
      ),
    ),
  ).toBe(true);
}
async function responsive(page: Page) {
  for (const width of [390, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    await validPage(page);
  }
}
const pageErrors = new WeakMap<Page, string[]>();
test.beforeEach(async ({ page }) => {
  const errors: string[] = [];
  pageErrors.set(page, errors);
  page.on('pageerror', (error) => errors.push(error.name));
});
test.afterEach(async ({ page }) => {
  expect(pageErrors.get(page) ?? []).toEqual([]);
});
test.describe.configure({ mode: 'serial' });
test.beforeAll(async () => {
  const user = await operator('POST', '/auth/v1/admin/users', {
    email,
    password,
    email_confirm: true,
    user_metadata: { fastcoloc_web_e2e: run },
  });
  userId = String(user['id']);
  if (!/^[0-9a-f-]{36}$/.test(userId)) throw new Error('Invalid fixture ID');
  mkdirSync('test-results/screenshots', { recursive: true });
});
test.afterAll(async () => {
  if (!userId) return;
  const files = await operator('POST', '/storage/v1/object/list/profile-photos', {
    prefix: userId + '/',
    limit: 100,
    offset: 0,
  });
  if (Array.isArray(files) && files.length)
    await operator('DELETE', '/storage/v1/object/profile-photos', {
      prefixes: files.map((f: { name: string }) => userId + '/' + f.name),
    });
  await operator('DELETE', '/auth/v1/admin/users/' + userId);
});
test('landing, responsive, ressources locales et validation avant confirmation', async ({
  page,
}) => {
  const failures: string[] = [];
  page.on('pageerror', (e) => failures.push(e.name));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: /La coloc/ })).toBeVisible();
  await page.screenshot({ path: 'test-results/screenshots/home-desktop.png', fullPage: true });
  for (const width of [390, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    await validPage(page);
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'test-results/screenshots/home-mobile.png', fullPage: true });
  await page.getByRole('link', { name: 'Rejoindre FastColoc →' }).click();
  await expect(page).toHaveURL(/register\/1/);
  await page.getByRole('button', { name: 'Créer mon compte et confirmer mon e-mail →' }).click();
  await expect(page.getByText('Ce champ est obligatoire.').first()).toBeVisible();
  await page.getByLabel('Prénom', { exact: true }).fill('Essai');
  await page.getByLabel('Nom', { exact: true }).fill('Validation');
  await page.getByLabel('Âge', { exact: true }).fill('25');
  await page.getByLabel('Adresse e-mail', { exact: true }).fill('bad');
  await page.getByLabel('Mot de passe', { exact: true }).fill('court');
  await page.getByLabel('Confirmer le mot de passe', { exact: true }).fill('autre');
  await page.getByRole('button', { name: 'Créer mon compte et confirmer mon e-mail →' }).click();
  await expect(page.getByText('Les mots de passe ne correspondent pas.')).toBeVisible();
  await expect(page.getByText('Entre une adresse e-mail valide.')).toBeVisible();
  expect(
    await page.evaluate(() => Object.values(sessionStorage).every((v) => !v.includes('court'))),
  ).toBe(true);
  await validPage(page);
  expect(failures).toEqual([]);
});
test('connexion réelle, cinq étapes, sauvegarde, reprise et photos privées', async ({ page }) => {
  await login(page);
  await expect(page).toHaveURL(/register\/1/);
  await page.getByLabel('Prénom', { exact: true }).fill(name);
  await page.getByLabel('Nom', { exact: true }).fill('Recette');
  await page.getByLabel('Âge', { exact: true }).fill('25');
  await page.getByRole('button', { name: 'Enregistrer et continuer →' }).click();
  await expect(page).toHaveURL(/register\/2/);
  await page.reload();
  await responsive(page);
  await selectFirst(page, '#governorate');
  await selectFirst(page, '#locality');
  await page.getByLabel('Rue', { exact: true }).fill('Rue de recette temporaire');
  await page.getByLabel('Code postal', { exact: true }).fill('1000');
  await page.getByRole('button', { name: 'Enregistrer et continuer →' }).click();
  await expect(page).toHaveURL(/register\/3/);
  await responsive(page);
  await page.getByLabel('Activités', { exact: true }).fill('Études');
  await page.getByLabel('Centres d’intérêt', { exact: true }).fill('Musique, lecture');
  await page.getByLabel('Langues parlées', { exact: true }).fill('Arabe, français');
  await page.getByLabel('Tabac', { exact: true }).selectOption('false');
  await page.getByRole('button', { name: 'Enregistrer et continuer →' }).click();
  await expect(page).toHaveURL(/register\/4/);
  await responsive(page);
  await page.getByLabel('Téléphone mobile (facultatif)', { exact: true }).fill('invalide');
  await page.getByRole('button', { name: 'Enregistrer et continuer →' }).click();
  await expect(page).toHaveURL(/register\/4/);
  await page.getByLabel('Téléphone mobile (facultatif)', { exact: true }).fill('+216 20 000 000');
  await page
    .getByLabel('Disponibilités de contact (facultatif)', { exact: true })
    .fill('En soirée');
  await page.getByRole('button', { name: 'Enregistrer et continuer →' }).click();
  await expect(page).toHaveURL(/register\/5/);
  await responsive(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: 'test-results/screenshots/register-photos-mobile.png',
    fullPage: true,
  });
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.getByRole('button', { name: 'Finaliser mon inscription →' }).click();
  await expect(
    page.getByRole('status').filter({ hasText: 'Ajoute au moins une photo' }),
  ).toBeVisible();
  const png = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64',
  );
  await page
    .locator('#photo-file')
    .setInputFiles({ name: 'recette.png', mimeType: 'image/png', buffer: png });
  await expect(page.getByAltText('Aperçu de la photo choisie')).toBeVisible();
  await page
    .getByLabel('Description (facultative)', { exact: true })
    .fill('Photo de recette temporaire');
  await page.getByRole('button', { name: 'Envoyer la photo' }).click();
  await expect(page.locator('.photo-item img')).toBeVisible();
  await page.getByRole('button', { name: 'Finaliser mon inscription →' }).click();
  await expect(page).toHaveURL(/\/profile$/);
  await expect(page.getByRole('heading', { name: 'Ton inscription est complète !' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: new RegExp(name) })).toBeVisible();
  await expect
    .poll(() =>
      page
        .locator('.photo-item img')
        .evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0),
    )
    .toBe(true);
  await page.screenshot({ path: 'test-results/screenshots/profile.png', fullPage: true });
  await responsive(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'test-results/screenshots/profile-mobile.png', fullPage: true });
});
test('modification du profil, préférences, session et refus ADMIN à USER', async ({ page }) => {
  await login(page);
  await expect(page).toHaveURL(/\/profile$/);
  await page.getByRole('link', { name: 'Modifier mes informations ↗' }).click();
  await page.getByLabel('Âge', { exact: true }).fill('26');
  await page.getByRole('button', { name: 'Enregistrer et continuer →' }).click();
  await expect(page).toHaveURL(/register\/2/);
  await page.goto('/profile');
  await expect(page.locator('dd').filter({ hasText: /^26$/ })).toBeVisible();
  await page.getByLabel('Budget minimum (DT/mois)', { exact: true }).fill('300');
  await page.getByLabel('Budget maximum (DT/mois)', { exact: true }).fill('500');
  await page.getByRole('button', { name: 'Enregistrer mes préférences' }).click();
  await expect(
    page.getByRole('status').filter({ hasText: 'Préférences enregistrées.' }),
  ).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Budget minimum (DT/mois)', { exact: true })).toHaveValue('300');
  await page.goto('/admin');
  await expect(page).toHaveURL(/\/profile$/);
  await expect(page.getByText('Tu ne disposes pas des droits nécessaires.')).toBeVisible();
  const status = await page.evaluate(async () => {
    const s = JSON.parse(sessionStorage.getItem('fastcoloc.session.v1')!);
    return (
      await fetch('/api/v1/admin/users', { headers: { Authorization: 'Bearer ' + s.access_token } })
    ).status;
  });
  expect(status).toBe(403);
  await page.evaluate(() => {
    const key = 'fastcoloc.session.v1';
    const s = JSON.parse(sessionStorage.getItem(key)!);
    s.expires_at = 0;
    sessionStorage.setItem(key, JSON.stringify(s));
  });
  const refreshed = page.waitForResponse(
    (r) => r.url().endsWith('/auth/refresh') && r.status() === 200,
  );
  await page.reload();
  await refreshed;
  await expect(page.getByRole('heading', { name: new RegExp(name) })).toBeVisible();
  await page.evaluate(() => {
    const key = 'fastcoloc.session.v1';
    const s = JSON.parse(sessionStorage.getItem(key)!);
    s.expires_at = 0;
    s.refresh_token = 'invalid-fixture-refresh';
    sessionStorage.setItem(key, JSON.stringify(s));
  });
  await page.reload();
  await expect(page).toHaveURL(/\/login$/);
});
test('récupération réelle, lien invalide, reset et connexion avec le nouveau mot de passe', async ({
  page,
}) => {
  await page.goto('/reset-password');
  await expect(page.getByText('Ce lien de récupération est absent ou a expiré.')).toBeVisible();
  await page.goto('/auth/callback?error=access_denied');
  await expect(page.getByRole('heading', { name: 'Le lien n’a pas abouti' })).toBeVisible();
  await page.goto('/forgot-password');
  await page.getByLabel('Adresse e-mail', { exact: true }).fill(email);
  const recover = page.waitForResponse((r) => r.url().endsWith('/auth/forgot-password'));
  await page.getByRole('button', { name: 'Recevoir le lien →' }).click();
  const response = await recover;
  expect(response.status()).toBe(202);
  await expect(
    page.getByText('Si cette demande est admissible, un e-mail sera envoyé.', { exact: false }),
  ).toBeVisible();
  const link = await operator('POST', '/auth/v1/admin/generate_link', { type: 'recovery', email });
  const properties = link['properties'] as Record<string, unknown> | undefined;
  const actionLink = String(properties?.['action_link'] ?? link['action_link'] ?? '');
  if (!actionLink.startsWith(upstream + '/auth/v1/verify'))
    throw new Error('No real recovery link returned');
  const actionUrl = new URL(actionLink);
  actionUrl.searchParams.set('redirect_to', 'http://localhost:4200/auth/callback');
  const verification = await fetch(actionUrl, { redirect: 'manual' });
  const returned = new URL(verification.headers.get('location') ?? 'http://invalid.local');
  // Check booleans so failed assertions never print a recovery link or token.
  expect(
    returned.origin === 'http://localhost:4200' && returned.pathname === '/auth/callback',
  ).toBe(true);
  expect(new URLSearchParams(returned.hash.slice(1)).get('type') === 'recovery').toBe(true);
  await page.evaluate((url) => {
    location.assign(url);
  }, returned.href);
  await expect(page).toHaveURL(/reset-password/);
  password = 'Fc!New9' + randomUUID();
  await page.getByLabel('Nouveau mot de passe', { exact: true }).fill(password);
  await page.getByLabel('Confirmer le mot de passe', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Enregistrer le mot de passe →' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await login(page);
  await expect(page).toHaveURL(/\/profile$/);
  await page.getByRole('button', { name: 'Se déconnecter', exact: true }).first().click();
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(() => sessionStorage.getItem('fastcoloc.session.v1'))).toBeNull();
});
test('ADMIN réel, confirmations, contacts, blocage, déblocage et suppression de la fixture', async ({
  page,
  browser,
}) => {
  await login(page, config['FASTCOLOC_TEST_EMAIL'], config['FASTCOLOC_TEST_EMAIL_PASSWORD']);
  await expect(page).not.toHaveURL(/\/login$/);
  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'Tableau de bord 👋' })).toBeVisible();
  await page.getByLabel('Rechercher par nom', { exact: true }).fill(name);
  await page.getByRole('button', { name: 'Rechercher', exact: true }).click();
  const row = page.getByRole('row').filter({ hasText: userId });
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: 'Bloquer', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: 'Annuler', exact: true }).click();
  await expect(row.getByText('Actif', { exact: true })).toBeVisible();
  await row.getByRole('button', { name: 'Corriger les contacts' }).click();
  await page.getByLabel('Mobile', { exact: true }).fill('+216 21 000 000');
  await page.getByLabel('Motif obligatoire').fill('Recette Web sur compte temporaire isolé');
  await page.getByRole('button', { name: 'Enregistrer les contacts' }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await row.getByRole('button', { name: 'Bloquer', exact: true }).click();
  await page.getByLabel('Motif obligatoire').fill('Recette du blocage temporaire');
  await page.getByRole('button', { name: 'Confirmer l’action' }).click();
  await expect(row.getByText('Bloqué', { exact: true })).toBeVisible();
  const userContext = await browser.newContext();
  const userPage = await userContext.newPage();
  await login(userPage);
  await expect(
    userPage.getByRole('status').filter({ hasText: 'Ce compte est bloqué' }),
  ).toBeVisible();
  await userContext.close();
  await row.getByRole('button', { name: 'Débloquer', exact: true }).click();
  await page.getByLabel('Motif obligatoire').fill('Fin de la recette du blocage');
  await page.getByRole('button', { name: 'Confirmer l’action' }).click();
  await expect(row.getByText('Actif', { exact: true })).toBeVisible();
  await page.screenshot({ path: 'test-results/screenshots/admin.png', fullPage: true });
  await responsive(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'test-results/screenshots/admin-mobile.png', fullPage: true });
  await page.setViewportSize({ width: 1440, height: 900 });
  await row.getByRole('button', { name: 'Supprimer', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Supprimer ce compte ?' })).toBeVisible();
  await page.getByLabel('Motif obligatoire').fill('Nettoyage du compte temporaire de recette Web');
  await page.getByRole('button', { name: 'Confirmer l’action' }).click();
  await expect(row).not.toBeVisible();
  await expect(page.getByRole('dialog')).not.toBeVisible();
});
test('Google réel : PKCE, state applicatif et redirection fournisseur', async ({ page }) => {
  await page.goto('/login');
  const outgoing = page.waitForRequest((r) => r.url().startsWith(upstream + '/auth/v1/authorize'));
  await page
    .getByRole('button', { name: 'Continuer avec Google', exact: true })
    .click({ noWaitAfter: true });
  const request = await outgoing;
  const url = new URL(request.url());
  expect(url.searchParams.get('code_challenge')).toMatch(/^[A-Za-z0-9_-]{43}$/);
  expect(new URL(url.searchParams.get('redirect_to')!).searchParams.get('state')).toMatch(
    /^[A-Za-z0-9_-]{43}$/,
  );
  await page.waitForURL(/accounts\.google\.com/, { timeout: 30000 });
});
