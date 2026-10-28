import { build } from 'esbuild';
import { mkdir, copyFile, cp, writeFile, readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.dirname(fileURLToPath(import.meta.url));
const assets = path.resolve(root, '../app/src/main/assets');
await mkdir(path.join(assets, 'fonts'), { recursive: true });
await mkdir(path.join(assets, 'licenses'), { recursive: true });
await build({
  entryPoints: [path.join(root, 'src/preview.js')],
  outfile: path.join(assets, 'preview.js'),
  bundle: true, minify: true, platform: 'browser', target: 'chrome100', legalComments: 'eof',
});
await copyFile(path.join(root, 'src/preview.html'), path.join(assets, 'preview.html'));
await copyFile(path.join(root, 'node_modules/katex/dist/katex.min.css'), path.join(assets, 'katex.min.css'));
await cp(path.join(root, 'node_modules/katex/dist/fonts'), path.join(assets, 'fonts'), { recursive: true });
await copyFile(path.join(root, 'node_modules/katex/LICENSE'), path.join(assets, 'licenses/KaTeX-LICENSE.txt'));
await copyFile(path.join(root, 'node_modules/mathml2omml/LICENSE'), path.join(assets, 'licenses/mathml2omml-LICENSE.txt'));
await copyFile(path.join(root, 'node_modules/@xmldom/xmldom/LICENSE'), path.join(assets, 'licenses/xmldom-LICENSE.txt'));
// Keep the LGPL component's corresponding source and build metadata in the project.
await mkdir(path.join(root, 'vendor-sources/mathml2omml'), { recursive: true });
const sourceMap = JSON.parse(await readFile(path.join(root, 'node_modules/mathml2omml/dist/index.js.map'), 'utf8'));
for (let index = 0; index < sourceMap.sources.length; index++) {
  const source = sourceMap.sources[index];
  if (!source.startsWith('../src/')) continue;
  const target = path.join(root, 'vendor-sources/mathml2omml', source.slice(3));
  await mkdir(path.dirname(target), { recursive: true });
  await writeFile(target, sourceMap.sourcesContent[index]);
}
await cp(path.join(root, 'node_modules/mathml2omml/dist'), path.join(root, 'vendor-sources/mathml2omml/dist'), { recursive: true });
for (const file of ['LICENSE', 'README.md', 'package.json']) {
  await copyFile(path.join(root, 'node_modules/mathml2omml', file), path.join(root, 'vendor-sources/mathml2omml', file));
}
await writeFile(path.join(assets, 'licenses/NOTICE.txt'), `Offline preview and equation export use KaTeX 0.19.1 (MIT, https://github.com/KaTeX/KaTeX), mathml2omml 0.5.0 (LGPL-3.0-or-later, https://github.com/fiduswriter/mathml2omml), and @xmldom/xmldom 0.9.12 (MIT, https://github.com/xmldom/xmldom).\nThe complete mathml2omml corresponding source is supplied in web/vendor-sources/mathml2omml. Rebuild with npm ci && npm run build in web/. Local package replacements can be rebuilt with the same command.\n`);
