import { mkdir, writeFile, readFile, unlink } from 'node:fs/promises';
const dir = new URL('../public/assets/fonts/', import.meta.url);
await mkdir(dir, { recursive: true });
const previous = await readFile(new URL('../src/fonts.css', import.meta.url), 'utf8').catch(
  () => '',
);
const response = await fetch(
  'https://fonts.googleapis.com/css2?family=Baloo+2:wght@500..800&family=DM+Sans:wght@400..800&display=swap',
  {
    headers: {
      'User-Agent':
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
    },
  },
);
if (!response.ok) throw new Error('Font CSS unavailable');
let css = await response.text();
for (const url of [...new Set(css.match(/https:\/\/fonts\.gstatic\.com\/[^)\s]+/g))]) {
  const name = new URL(url).pathname.split('/').pop();
  const file = await fetch(url);
  if (!file.ok) throw new Error('Font unavailable');
  await writeFile(new URL(name, dir), new Uint8Array(await file.arrayBuffer()));
  css = css.replaceAll(url, '/assets/fonts/' + name);
}
await writeFile(new URL('../src/fonts.css', import.meta.url), css);
// Remove only previously referenced, generated font files inside our asset directory.
for (const match of previous.matchAll(/\/assets\/fonts\/([A-Za-z0-9_.-]+)/g)) {
  if (!css.includes('/assets/fonts/' + match[1]))
    await unlink(new URL(match[1], dir)).catch(() => {});
}
for (const family of ['baloo2', 'dmsans']) {
  const license = await fetch(
    'https://raw.githubusercontent.com/google/fonts/main/ofl/' + family + '/OFL.txt',
  );
  if (!license.ok) throw new Error('Font license unavailable');
  await writeFile(new URL(family + '-OFL.txt', dir), await license.text());
}
const styles = new URL('../src/styles.css', import.meta.url);
await writeFile(styles, (await readFile(styles, 'utf8')).replace(/@import url\([^)]*\);/, ''));
console.log('Fonts downloaded locally.');
