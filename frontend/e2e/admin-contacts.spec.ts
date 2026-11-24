import { test, expect } from '@playwright/test';
import { readFileSync, mkdirSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
const recipe = require('../../backend/tools/sprint2-fixtures.cjs');
const config = Object.fromEntries(
  readFileSync('../backend/.env.local', 'utf8')
    .split(/\r?\n/)
    .filter((l) => /^[A-Z_]+=/.test(l))
    .map((l) => {
      const i = l.indexOf('=');
      return [l.slice(0, i), l.slice(i + 1).trim()];
    }),
);
const run = randomUUID();
const name = 'TestContact' + run.slice(0, 8);
const email = 'fastcoloc-contact-ui-' + run + '@example.com';
const password = 'Fc!aA9' + randomUUID();
let id = '';
let token = '';
test.beforeAll(async () => {
  const created = await recipe.operator('POST', '/auth/v1/admin/users', {
    email,
    password,
    email_confirm: true,
  });
  id = created.id;
  token = (await recipe.api('POST', '/auth/login', null, { email, password })).access_token;
  await recipe.api('POST', '/users/sync', token, {});
  await recipe.api('PUT', '/onboarding/steps/1', token, {
    firstName: name,
    lastName: 'Fixture temporaire',
    age: 25,
    gender: null,
  });
  await recipe.api('PUT', '/onboarding/steps/4', token, {
    mobilePhone: '21000000',
    residentialPhone: '71000000',
    callAvailability: 'Après 18 h',
    msnContact: 'contact-test',
    skypeContact: 'skype-test',
    websiteUrl: 'https://example.com',
  });
});
test.afterAll(async () => {
  if (!id) return;
  const result = await recipe.operator('GET', '/auth/v1/admin/users/' + id);
  const actual = result.user || result;
  if (actual.id !== id || actual.email !== email) throw Error('Unexpected fixture owner');
  await recipe.operator('DELETE', '/auth/v1/admin/users/' + id);
  console.log('Single temporary contacts account cleaned.');
});
test('Contacts ADMIN : préremplissage, erreur sans effacement et dialogue responsive', async ({
  page,
}) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto('/login');
  await page.getByLabel('Adresse e-mail', { exact: true }).fill(config['FASTCOLOC_TEST_EMAIL']);
  await page
    .getByLabel('Mot de passe', { exact: true })
    .fill(config['FASTCOLOC_TEST_EMAIL_PASSWORD']);
  await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login$/);
  await page.goto('/admin');
  await page.getByLabel('Rechercher par nom', { exact: true }).fill(name);
  await page.getByRole('button', { name: 'Rechercher', exact: true }).click();
  const row = page.getByRole('row').filter({ hasText: id });
  await row.getByRole('button', { name: 'Corriger les contacts', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByLabel('Mobile', { exact: true })).toHaveValue('21000000');
  await expect(dialog.getByLabel('Fixe', { exact: true })).toHaveValue('71000000');
  await expect(dialog.getByLabel('Site Web', { exact: true })).toHaveValue('https://example.com');
  expect(await page.evaluate(() => getComputedStyle(document.documentElement).overflow)).toBe(
    'clip',
  );
  mkdirSync('test-results/admin-contacts', { recursive: true });
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 760 });
    const box = await dialog.boundingBox();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.y).toBeGreaterThanOrEqual(0);
    expect(box!.y + box!.height).toBeLessThanOrEqual(760);
    const footer = await dialog.locator('.admin-dialog-footer').boundingBox();
    expect(footer!.y + footer!.height).toBeLessThanOrEqual(760);
    const body = dialog.locator('.admin-dialog-body');
    await body.evaluate((el) => (el.scrollTop = el.scrollHeight));
    await expect(dialog.getByRole('button', { name: 'Annuler', exact: true })).toBeVisible();
    await body.evaluate((el) => (el.scrollTop = 0));
    expect(await dialog.evaluate((el) => el.scrollWidth <= el.clientWidth)).toBe(true);
    await page.screenshot({ path: 'test-results/admin-contacts/dialogue-' + width + '.png' });
  }
  await dialog.getByRole('button', { name: 'Fermer la fenêtre', exact: true }).click();
  let fail = true;
  await page.route('**/api/v1/admin/users/' + id + '/contacts', async (route) => {
    if (fail && route.request().method() === 'GET') {
      fail = false;
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: '{"code":"DATABASE_UNAVAILABLE"}',
      });
    } else await route.continue();
  });
  await row.getByRole('button', { name: 'Corriger les contacts', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('Impossible de charger');
  await expect(
    dialog.getByRole('button', { name: 'Enregistrer les contacts', exact: true }),
  ).toBeDisabled();
  await dialog.getByRole('button', { name: 'Réessayer', exact: true }).click();
  await expect(dialog.getByLabel('Mobile', { exact: true })).toHaveValue('21000000');
  await dialog.getByLabel('Mobile', { exact: true }).fill('22000000');
  await dialog.getByLabel('Motif obligatoire').fill('Correction isolée de la fixture contacts');
  await dialog.getByRole('button', { name: 'Enregistrer les contacts', exact: true }).click();
  await expect(dialog).not.toBeVisible();
  const data = await recipe.api('GET', '/users/me/details', token);
  expect(data.contacts.mobile_phone).toBe('22000000');
  expect(data.contacts.residential_phone).toBe('71000000');
  expect(data.contacts.website_url).toBe('https://example.com');
  expect(data.contacts.skype_contact).toBe('skype-test');
  expect(await page.evaluate(() => getComputedStyle(document.documentElement).overflow)).not.toBe(
    'hidden',
  );
  expect(errors).toEqual([]);
});
