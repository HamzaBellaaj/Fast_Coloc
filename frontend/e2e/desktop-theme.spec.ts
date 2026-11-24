import { test, expect, Page } from '@playwright/test';
import { fixture } from './support/ui-fixture';
const key = 'fastcoloc.desktop-theme.v1';
test.use({ viewport: { width: 1440, height: 1000 } });
async function readable(page: Page, selector: string) {
  const samples = await page.locator(selector).evaluateAll((elements) =>
    elements
      .filter((e) => e.getClientRects().length)
      .map((e) => {
        let parent: Element | null = e,
          bg = '';
        while (parent) {
          bg = getComputedStyle(parent).backgroundColor;
          if (bg !== 'rgba(0, 0, 0, 0)') break;
          parent = parent.parentElement;
        }
        const L = (color: string) => {
          const rgb = color
            .match(/[0-9.]+/g)!
            .slice(0, 3)
            .map(Number)
            .map((v) => v / 255)
            .map((v) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
          return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722;
        };
        const fg = L(getComputedStyle(e).color),
          back = L(bg);
        return {
          name: e.tagName + ' ' + e.className,
          ratio: (Math.max(fg, back) + 0.05) / (Math.min(fg, back) + 0.05),
        };
      }),
  );
  for (const sample of samples)
    expect(sample.ratio, sample.name + ' contrast').toBeGreaterThanOrEqual(4.5);
}

test('system default, saved choice, reload and desktop boundary', async ({ page }) => {
  await fixture(page);
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('data-desktop-theme', 'dark');
  const control = page.getByLabel('Thème du bureau');
  await expect(control).toHaveValue('system');
  await control.selectOption('light');
  await page.reload();
  await expect(control).toHaveValue('light');
  await expect(page.locator('html')).toHaveAttribute('data-desktop-theme', 'light');
  await control.selectOption('system');
  await page.emulateMedia({ colorScheme: 'light' });
  await expect(page.locator('html')).toHaveAttribute('data-desktop-theme', 'light');
  await page.emulateMedia({ colorScheme: 'dark' });
  await expect(page.locator('html')).toHaveAttribute('data-desktop-theme', 'dark');
  await page.setViewportSize({ width: 850, height: 900 });
  await expect(control).toHaveCount(0);
  await expect(page.locator('html')).toHaveAttribute('data-desktop-theme', 'dark');
  await page.setViewportSize({ width: 851, height: 900 });
  await expect(control).toHaveValue('system');
  await expect(page.locator('html')).toHaveAttribute('data-desktop-theme', 'dark');
});
test('theme remains usable when browser storage is blocked', async ({ page }) => {
  await fixture(page);
  await page.addInitScript(() =>
    Object.defineProperty(window, 'localStorage', {
      get() {
        throw new DOMException('blocked', 'SecurityError');
      },
    }),
  );
  await page.goto('/');
  await page.getByLabel('Thème du bureau').selectOption('dark');
  await expect(page.locator('html')).toHaveAttribute('data-desktop-theme', 'dark');
});
test('filter dialog traps focus, closes with Escape, restores focus and submits', async ({
  page,
}) => {
  await fixture(page, true);
  await page.goto('/annonces');
  await expect(page.locator('.preview-card')).toHaveCount(4);
  const trigger = page.getByRole('button', { name: 'Filtres et tri' });
  await trigger.focus();
  await page.keyboard.press('Enter');
  const dialog = page.getByRole('dialog', { name: 'Filtres', exact: true });
  await expect(dialog).toBeVisible();
  await expect(page.getByRole('button', { name: 'Fermer les filtres' })).toBeFocused();
  await dialog.getByRole('button', { name: 'Afficher les résultats' }).focus();
  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');
  expect(
    await page.evaluate(
      () => !!document.activeElement?.closest('dialog') || document.activeElement === document.body,
    ),
  ).toBe(true);
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  await expect(trigger).toBeFocused();
  await trigger.click();
  await dialog.getByLabel('Offres', { exact: true }).check();
  await dialog.getByRole('button', { name: 'Afficher les résultats' }).click();
  await expect(page).toHaveURL(/kind=OFFER/);
  await expect(dialog).not.toBeVisible();
});
for (const theme of ['light', 'dark'])
  test(theme + ' renders screens and accessible state icons', async ({ page }) => {
    await fixture(page, true);
    await page.addInitScript(({ key, theme }) => localStorage.setItem(key, theme), { key, theme });
    for (const route of ['/', '/annonces', '/annonces/ui-listing', '/profile']) {
      await page.goto(route);
      if (route === '/profile')
        await expect(page.getByRole('heading', { name: 'Notifications par e-mail' })).toBeVisible();
      else if (route === '/annonces/ui-listing')
        await expect(page.locator('.listing-title')).toBeVisible();
      else await expect(page.locator('.preview-card')).toHaveCount(4);
      await page.evaluate(() => document.fonts.ready);
      await readable(
        page,
        '.home-heading p,.listing-filters>label,.listing-filters>label>input,.detail-facts p,.contact-price span,.section-card dt',
      );
      await expect(page.locator('html')).toHaveAttribute('data-desktop-theme', theme);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
      await page.screenshot({
        path:
          'test-results/desktop-' + theme + '-' + (route.replaceAll('/', '-') || 'home') + '.png',
        fullPage: true,
      });
    }
    let release!: () => void;
    const gate = new Promise<void>((r) => (release = r));
    await page.route('**/api/v1/notification-preferences', async (route) => {
      if (route.request().method() === 'PATCH') await gate;
      await route.fulfill({
        json: {
          newMessageEmail: route.request().method() !== 'PATCH',
          deliveryAvailable: true,
          deliveryMode: 'local',
        },
      });
    });
    await page.reload();
    const warning = page.locator('.notification-card [data-status=warning]');
    await expect(warning.locator('svg')).toBeVisible();
    await page.getByRole('checkbox', { name: /Recevoir un e-mail/ }).uncheck();
    await page.getByRole('button', { name: 'Enregistrer les notifications', exact: true }).click();
    const loading = page.locator('.notification-card button[data-status=loading]');
    await expect(loading.locator('svg')).toBeVisible();
    release();
    const success = page.locator('.notification-card [data-status=success]');
    await expect(success).toHaveAttribute('role', 'status');
    await expect(success.locator('svg')).toBeVisible();
    await page.route('**/api/v1/notification-preferences', (route) =>
      route.fulfill({ status: 503, json: { message: 'Service indisponible' } }),
    );
    await page.reload();
    const error = page.locator('.notification-card [role=alert]');
    await expect(error.locator('svg')).toBeVisible();
    await readable(page, '.notification-card [role=alert]');
    await page.screenshot({
      path: 'test-results/desktop-' + theme + '-feedback.png',
      fullPage: true,
    });
    await page.route('**/api/v1/auth/forgot-password', (r) => r.fulfill({ json: {} }));
    await page.goto('/forgot-password');
    await page.getByLabel('Adresse e-mail', { exact: true }).fill('fixture@example.invalid');
    await page.locator('form button[type=submit], form button.btn').last().click();
    const pending = page.locator('[role=status][data-status=pending]');
    await expect(pending.locator('svg')).toBeVisible();
    expect(await pending.locator('svg').getAttribute('aria-hidden')).toBe('true');
  });

test('desktop header fits at its breakpoint and mobile follows a saved dark choice', async ({
  page,
}) => {
  await fixture(page, true);
  await page.addInitScript(() => localStorage.setItem('fastcoloc.desktop-theme.v1', 'dark'));
  await page.goto('/');
  for (const width of [851, 900, 1024, 1440]) {
    await page.setViewportSize({ width, height: 1000 });
    await expect(page.getByLabel('Thème du bureau')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
  }
  for (const width of [390, 768, 850]) {
    await page.setViewportSize({ width, height: 900 });
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-desktop-theme', 'dark');
    await expect(page.getByLabel('Thème du bureau')).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
  }
});
