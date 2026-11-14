import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile,mkdtemp,mkdir,writeFile,copyFile,access,rm} from 'node:fs/promises';
import {execFileSync,spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import path from 'node:path';import os from 'node:os';
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const archive=path.resolve('dist/gretl-runtime.vsix');
const temp=await mkdtemp(path.join(os.tmpdir(),'gretl-vsix-integrity-'));
after(()=>rm(temp,{recursive:true,force:true}));
test('actual VSIX inventory binds identity, versions, catalogue, protocol and every delivered component',async()=>{
 const inventory=JSON.parse(await readFile('dist/distribution-inventory.json','utf8'));
 const manifest=JSON.parse(execFileSync('unzip',['-p',archive,'extension/server/bundle-manifest.json'],{encoding:'utf8'}));
 assert.equal(inventory.extensionId,'edigonzales.gretl-runtime-vscode');assert.equal(inventory.candidateSha,execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim());
 assert.equal(inventory.vsixSha256,hash(await readFile(archive)));assert.equal(manifest.javaRelease,25);assert.match(manifest.javaVersion,/^25(?:[.-]|$)/);assert.match(manifest.groovyVersion,/^6\./);assert.ok(manifest.gradleVersion&&manifest.runtimeVersion);
 assert.equal(manifest.wireSchemaVersion,1);assert.match(manifest.catalogId,/^[a-f0-9]{64}$/);assert.match(manifest.catalogSha256,/^[a-f0-9]{64}$/);
 for(const name of ['dist/extension.cjs','dist/modeler/view.js','dist/modeler/view.css','dist/modeler/elk-worker.min.js','language-configuration.json','server/gretl-lsp.jar','server/operations.json','server/gretl-protocol-v1.schema.json','syntaxes/groovy.tmLanguage.json','syntaxes/LICENSE-TEXTMATE.txt'])assert.ok(manifest.payloadSha256[name],name);
 assert.equal(inventory.entries.length,Object.keys(manifest.payloadSha256).length+3);
});
test('tampering with a real packaged payload fails inventory verification and emits no success inventory',async()=>{
 const tampered=path.join(temp,'tampered.vsix');await copyFile(archive,tampered);await mkdir(path.join(temp,'extension'));await writeFile(path.join(temp,'extension/language-configuration.json'),'{}\n');
 execFileSync('zip',['-q',tampered,'extension/language-configuration.json'],{cwd:temp,stdio:'pipe'});
 const output=path.join(temp,'must-not-exist.json');const result=spawnSync(process.execPath,['scripts/inventory.mjs',tampered,output],{encoding:'utf8'});
 assert.notEqual(result.status,0);assert.match(result.stdout+result.stderr,/Payload digest mismatch: extension\/language-configuration.json/);await assert.rejects(access(output),{code:'ENOENT'});
});
