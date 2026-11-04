import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {mkdtemp,readFile,writeFile,mkdir,rm} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
const temp=await mkdtemp(path.join(os.tmpdir(),'gretl-host-ä space $()-'));
after(()=>rm(temp,{recursive:true,force:true}));
const entry=path.resolve('src/host.ts');
await build({entryPoints:[entry],bundle:true,platform:'node',format:'esm',outfile:path.join(temp,'host.mjs')});
const {javaMajor,checkJava,checkBundle,digest,sourceDigest,languageConfiguration,localHost}=await import(pathToFileURL(path.join(temp,'host.mjs')));
test('Java output supports actual vendor formats and rejects adjacent major versions',()=>{
 for(const value of ['openjdk version "25.0.2" 2026-01-20','java version "25"','openjdk 25-ea 2025'])assert.equal(javaMajor(value),25);
 for(const value of ['openjdk version "21.0.9"','java version "1.8.0"','not java'])assert.notEqual(javaMajor(value),25);
});
test('Java preflight executes the current real Java25 and fails clearly without shell execution',async()=>{
 await checkJava('java');
 await assert.rejects(checkJava(path.join(temp,'missing;touch sentinel')),/Java 25/);
 await assert.rejects(checkJava(process.execPath),/Java 25/);
 await assert.rejects(readFile(path.join(temp,'sentinel')),{code:'ENOENT'});
});
test('source hashes preserve Java UTF16BE, BOM and unpaired surrogates',()=>{
 assert.equal(sourceDigest('abc'),digest(Buffer.from('006100620063','hex')));
 assert.equal(sourceDigest('\uFEFF😀\uD800'),digest(Buffer.from('feffd83dde00d800','hex')));
 assert.notEqual(sourceDigest('abc'),digest('abc'));
});
test('bundle checks every resource, rejects escaping names and mismatching overrides',async()=>{
 const root=path.join(temp,'bundle');await mkdir(path.join(root,'server'),{recursive:true});
 const files={};
 for(const name of ['gretl-lsp.jar','operations.json','catalog-manifest.json','gretl-protocol-v1.schema.json']){await writeFile(path.join(root,'server',name),name);files[name]=digest(name);}
 const manifest={schemaVersion:1,extensionVersion:'0.1.0',sourceCommit:'fixture',files};
 const save=()=>writeFile(path.join(root,'server/bundle-manifest.json'),JSON.stringify(manifest));await save();
 assert.equal((await checkBundle(root,'')).jar,path.join(root,'server/gretl-lsp.jar'));
 await assert.rejects(checkBundle(root,'relative.jar'),/bundle/);
 const override=path.join(temp,'copied server.jar');await writeFile(override,'gretl-lsp.jar');await checkBundle(root,override);
 await writeFile(override,'tampered');await assert.rejects(checkBundle(root,override),/bundle/);
 await writeFile(path.join(root,'server/operations.json'),'tampered');await assert.rejects(checkBundle(root,''),/bundle/);
 await writeFile(path.join(root,'server/operations.json'),'operations.json');
 delete files['operations.json'];files['../escape']='bad';await save();await assert.rejects(checkBundle(root,''),/bundle/);
});
test('only strict language/trust is forwarded; URI support is filesystem only',()=>{
 assert.deepEqual(languageConfiguration('auto','de-CH'),{language:'de',trusted:true});
 assert.deepEqual(languageConfiguration('en','de'),{language:'en',trusted:true});
 assert.deepEqual(languageConfiguration('unknown','de'),{language:'en',trusted:true});
 assert.equal(localHost(['file','file']),true);
 for(const scheme of ['vscode-remote','https','memfs'])assert.equal(localHost(['file',scheme]),false);
});
test('manifest owns exact filenames and explicit language, never arbitrary Groovy/Gradle',async()=>{
 const pkg=JSON.parse(await readFile('package.json','utf8'));
 assert.equal(pkg.publisher+'.'+pkg.name,'edigonzales.gretl-runtime-vscode');
 assert.deepEqual(pkg.contributes.languages[0].filenames,['job.groovy','settings.gretl']);
 assert.equal(pkg.contributes.languages[0].extensions,undefined);
 assert.deepEqual(pkg.extensionKind,['workspace']);
 assert.equal(pkg.capabilities.virtualWorkspaces,false);
 assert.equal(pkg.capabilities.untrustedWorkspaces.supported,'limited');
});
