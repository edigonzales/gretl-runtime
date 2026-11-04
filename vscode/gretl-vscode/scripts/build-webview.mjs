import {build} from 'esbuild';
import {mkdir,copyFile,writeFile} from 'node:fs/promises';
import path from 'node:path';import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
await mkdir(path.join(root,'dist/modeler'),{recursive:true});
const result=await build({entryPoints:[path.join(root,'webview/view.tsx')],bundle:true,platform:'browser',format:'iife',target:'es2022',minify:true,legalComments:'eof',outfile:path.join(root,'dist/modeler/view.js'),metafile:true});
await copyFile(path.join(root,'node_modules/elkjs/lib/elk-worker.min.js'),path.join(root,'dist/modeler/elk-worker.min.js'));
await writeFile(path.join(root,'dist/modeler-dependencies.json'),JSON.stringify(result.metafile,null,2)+'\n');
console.log('Built local React Flow view and actual ELK worker.');
