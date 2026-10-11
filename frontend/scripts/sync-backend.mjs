import { cp, lstat, mkdir, readdir, rm } from 'node:fs/promises';
import { dirname, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repo = resolve(frontend, '..');
const output = resolve(repo, 'backend/src/main/resources/static');
const dist = resolve(frontend, 'dist');
if (!output.startsWith(repo + sep) || !dist.startsWith(frontend + sep)) throw new Error('Build output escaped the repository.');
await lstat(resolve(dist, 'index.html')); // A failed build must never remove the existing UI.
await mkdir(output, { recursive: true });
if ((await lstat(output)).isSymbolicLink()) throw new Error('Refusing to sync into a linked output directory.');
// These directories belong exclusively to the frontend. Do not touch backend configuration.
for (const name of ['assets', 'css', 'js', 'images', 'fonts', 'icons']) {
  const target = resolve(output, name);
  if (!target.startsWith(output + sep)) throw new Error('Unsafe output path.');
  await rm(target, { recursive: true, force: true });
}
for (const entry of await readdir(dist)) await cp(resolve(dist, entry), resolve(output, entry), { recursive: true });
console.log('Synced the React build to Spring Boot static resources.');
