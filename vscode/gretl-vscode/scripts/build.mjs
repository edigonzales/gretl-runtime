import {build} from 'esbuild';
import {readFile,mkdir,copyFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const repo=path.resolve(root,'../..');
await mkdir(path.join(root,'dist'),{recursive:true});
await mkdir(path.join(root,'server'),{recursive:true});
await build({entryPoints:[path.join(root,'src/extension.ts')],bundle:true,platform:'node',target:'node22',format:'cjs',external:['vscode'],outfile:path.join(root,'dist/extension.cjs'),metafile:true}).then(async result=>writeFile(path.join(root,'dist/dependencies.json'),JSON.stringify(result.metafile,null,2)+'\n'));
const files={
 'gretl-lsp.jar':'gretl-lsp/target/gretl-lsp-6.0.0-SNAPSHOT-jar-with-dependencies.jar',
 'operations.json':null,
 'catalog-manifest.json':null,
 'gretl-protocol-v1.schema.json':null
};
const hashes={};
for(const [name,source] of Object.entries(files)){
 const bytes=source ? await readFile(path.join(repo,source)) : execFileSync('unzip',['-p',path.join(repo,files['gretl-lsp.jar']),name],{maxBuffer:8*1024*1024});
 if(name==='gretl-protocol-v1.schema.json'&&!bytes.equals(await readFile(path.join(repo,'gretl-lsp/src/main/resources/gretl-protocol-v1.schema.json'))))throw Error('Rebuild the server: bundled protocol schema differs from source.');
 hashes[name]=createHash('sha256').update(bytes).digest('hex');
 await writeFile(path.join(root,'server',name),bytes);
}
const pkg=JSON.parse(await readFile(path.join(root,'package.json'),'utf8'));
await writeFile(path.join(root,'server/bundle-manifest.json'),JSON.stringify({schemaVersion:1,extensionVersion:pkg.version,sourceCommit:execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim(),files:hashes},null,2)+'\n');
// Keep all bundled package licences; no dependency install is needed in the VSIX.
const licenseFiles=execFileSync('rg',['--files','--hidden','--no-ignore','node_modules'],{cwd:root,encoding:'utf8'}).split('\n').filter(name=>/(?:^|\/)(?:licen[cs]e|notice)(?:\.[^/]*)?$/i.test(name));
let notices='Bundled npm dependency licences (exact versions: package-lock.json)\n';
for(const file of licenseFiles.sort())notices+='\n\n--- '+file+' ---\n'+await readFile(path.join(root,file),'utf8');
await writeFile(path.join(root,'server/THIRD-PARTY-NOTICES.txt'),notices);
