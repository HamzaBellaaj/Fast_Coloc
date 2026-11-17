import { readFile, readdir } from 'node:fs/promises';
import { resolve, extname } from 'node:path';
import { fileURLToPath } from 'node:url';
const config = Object.fromEntries(
  (await readFile(new URL('../../backend/.env.local', import.meta.url), 'utf8'))
    .split(/\r?\n/)
    .filter((v) => /^[A-Z_]+=/.test(v))
    .map((v) => {
      const i = v.indexOf('=');
      return [v.slice(0, i), v.slice(i + 1).trim()];
    }),
);
const values = Object.entries(config)
  .filter(
    ([key, value]) => /PASSWORD|SECRET_KEY|MANAGEMENT_ACCESS_TOKEN/.test(key) && value.length > 8,
  )
  .map(([, value]) => value);
let files = 0;
const failures = [];
async function scan(path) {
  for (const file of await readdir(path, { withFileTypes: true })) {
    const full = resolve(path, file.name);
    if (file.isDirectory()) await scan(full);
    else if (['.js', '.ts', '.html', '.css', '.json', '.map', '.md'].includes(extname(file.name))) {
      const content = await readFile(full, 'utf8');
      files++;
      if (
        values.some((value) => content.includes(value)) ||
        /sb_secret_[A-Za-z0-9_-]{15,}/.test(content) ||
        /eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(content)
      )
        failures.push(full);
    }
  }
}
await scan(fileURLToPath(new URL('../src', import.meta.url)));
await scan(fileURLToPath(new URL('../dist', import.meta.url)));
console.log(
  JSON.stringify({
    checkedFiles: files,
    configuredSecretValues: values.length,
    failures: failures.length,
  }),
);
if (failures.length) process.exitCode = 1;
