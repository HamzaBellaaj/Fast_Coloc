import { test, expect } from '@playwright/test';
const recipe = require('../../backend/tools/sprint2-fixtures.cjs');
test('live server: member browses a real listing, favorites it, and contacts its owner', async ({
  page,
}) => {
  test.setTimeout(360000);
  try {
    const data = await recipe.fixtures();
    for (const user of [data.owner, data.reader])
      await recipe.api('PATCH', '/notification-preferences', user.session.access_token, {
        newMessageEmail: false,
      });
    const title = '[TEST TEMPORAIRE] Refonte UI ' + data.run.slice(0, 8);
    const row = await recipe.api('POST', '/listings', data.owner.session.access_token, {
      kind: 'REQUEST',
      title,
      description:
        'Annonce temporaire créée pour vérifier les parcours réels de la nouvelle interface. Elle est supprimée après le test.',
      localityId: data.area.id,
      street: 'Zone de recette temporaire',
      latitude: null,
      longitude: null,
      rent: 450,
      capacity: 2,
      propertyType: 'Appartement',
      roommateType: 'Étudiant',
      equipment: ['WiFi'],
      availableFrom: '2026-12-01',
      availableUntil: null,
      publicationDays: 1,
    });
    await recipe.api('POST', '/listings/' + row.id + '/publish', data.owner.session.access_token);
    await page.goto('/login');
    await page.getByLabel('Adresse e-mail', { exact: true }).fill(data.reader.email);
    await page.getByLabel('Mot de passe', { exact: true }).fill(data.reader.password);
    await page.getByRole('button', { name: 'Se connecter', exact: true }).click();
    await expect(page).not.toHaveURL(/login$/);
    console.log('Live login passed');
    console.log('Live listing created');
    await page.goto('/annonces?q=' + encodeURIComponent(title));
    await expect(page.locator('.preview-card')).toHaveCount(1);
    await page.locator('.preview-body a').click();
    await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
    const favorite = page.locator('.detail-save');
    await favorite.click();
    await expect(favorite).toHaveAttribute('aria-pressed', 'true');
    await page.getByRole('link', { name: 'Contacter', exact: true }).click();
    await page
      .getByLabel('Ton message')
      .fill('Bonjour, je souhaite en savoir plus sur votre recherche de colocation.');
    await page.getByRole('button', { name: 'Envoyer', exact: true }).click();
    await expect(page).toHaveURL(/messages\/[0-9a-f-]+$/);
    await expect(page.locator('.message.mine')).toBeVisible();
    await page.goto('/profile');
    await expect(page.getByRole('checkbox', { name: /Recevoir un e-mail/ })).not.toBeChecked();
    await page.screenshot({ path: '../.hallmark/live-member-profile.png', fullPage: true });
  } finally {
    console.log('Temporary member accounts cleaned:', await recipe.cleanup());
  }
});
