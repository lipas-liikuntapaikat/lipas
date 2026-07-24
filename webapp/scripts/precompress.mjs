// Precompress release bundles for nginx brotli_static / gzip_static.
//
// Reads the module list from manifest.edn and writes a .br (brotli -q11) and
// .gz (gzip -9) sibling next to each bundle. nginx serves these directly
// instead of recompressing the immutable bundles on every request; clients
// without brotli support get the .gz, and if a sibling is missing nginx
// falls back to on-the-fly gzip.
//
// Only content-hashed filenames (app.A1B2C3D4.js) are compressed. Dev builds
// produce unhashed app.js, which must never get a precompressed sibling — a
// stale app.js.br would shadow fresh dev code, since brotli_static prefers
// the sibling without comparing mtimes.
//
// Runs via `npm run build` (manual deploys) and the frontend-precompress
// compose service (lipas-dev deploy workflow). Uses only node built-ins so
// it works in both node and the CI container without npm deps.

import { readFileSync, writeFileSync, readdirSync, existsSync, unlinkSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { brotliCompressSync, gzipSync, constants } from 'node:zlib';

const compiledDir = join(dirname(fileURLToPath(import.meta.url)), '..', 'resources', 'public', 'js', 'compiled');
const manifestPath = join(compiledDir, 'manifest.edn');

if (!existsSync(manifestPath)) {
  console.error(`[precompress] ${manifestPath} not found — did the release build run?`);
  process.exit(1);
}

// manifest.edn is EDN, but :output-name values are plain quoted strings
// written by shadow-cljs, so a regex extraction is safe here
const manifest = readFileSync(manifestPath, 'utf8');
const names = [...manifest.matchAll(/:output-name\s+"([^"]+)"/g)].map((m) => m[1]);
const hashed = names.filter((n) => /\.[0-9A-Fa-f]{4,}\.js$/.test(n));

if (hashed.length === 0) {
  console.log('[precompress] no content-hashed bundles in manifest.edn (dev build?) — nothing to do');
  process.exit(0);
}

// Prune orphaned .br/.gz whose source bundle is gone
for (const f of readdirSync(compiledDir)) {
  if ((f.endsWith('.js.br') || f.endsWith('.js.gz')) && !existsSync(join(compiledDir, f.replace(/\.(br|gz)$/, '')))) {
    unlinkSync(join(compiledDir, f));
    console.log(`[precompress] pruned orphan ${f}`);
  }
}

const kb = (n) => `${Math.round(n / 1000)} KB`;
let totals = { raw: 0, gz: 0, br: 0 };

for (const name of hashed) {
  const src = readFileSync(join(compiledDir, name));
  const br = brotliCompressSync(src, {
    params: {
      [constants.BROTLI_PARAM_QUALITY]: 11,
      [constants.BROTLI_PARAM_SIZE_HINT]: src.length,
    },
  });
  const gz = gzipSync(src, { level: 9 });
  writeFileSync(join(compiledDir, `${name}.br`), br);
  writeFileSync(join(compiledDir, `${name}.gz`), gz);
  totals.raw += src.length;
  totals.gz += gz.length;
  totals.br += br.length;
  console.log(`[precompress] ${name}: ${kb(src.length)} raw, ${kb(gz.length)} gz, ${kb(br.length)} br`);
}

console.log(`[precompress] total: ${kb(totals.raw)} raw, ${kb(totals.gz)} gz, ${kb(totals.br)} br (${hashed.length} bundles)`);
