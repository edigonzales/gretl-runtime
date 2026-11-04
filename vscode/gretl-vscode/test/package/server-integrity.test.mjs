import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,copyFile,writeFile,access,rm} from 'node:fs/promises';
import {execFileSync,spawnSync} from 'node:child_process';
import path from 'node:path';import os from 'node:os';
test('tampering with the actual server JAR in a scratch VSIX fails the shipped inventory gate',async()=>{
 const root=await mkdtemp(path.join(os.tmpdir(),'gretl-server-canary-'));
 try{
  const original=path.resolve('dist/gretl-runtime.vsix'),copy=path.join(root,'bad-server.vsix');await copyFile(original,copy);
  const jar=execFileSync('unzip',['-p',original,'extension/server/gretl-lsp.jar'],{maxBuffer:32<<20});
  await mkdir(path.join(root,'extension/server'),{recursive:true});
  await writeFile(path.join(root,'extension/server/gretl-lsp.jar'),Buffer.concat([jar,Buffer.from('INTENTIONAL_SCRATCH_CORRUPTION')]));
  execFileSync('zip',['-q',copy,'extension/server/gretl-lsp.jar'],{cwd:root,stdio:'pipe'});
  const output=path.join(root,'must-not-exist.json'),run=spawnSync(process.execPath,['scripts/inventory.mjs',copy,output],{encoding:'utf8'});
  assert.notEqual(run.status,0);assert.match(run.stdout+run.stderr,/Payload digest mismatch: extension\/server\/gretl-lsp.jar/);
  await assert.rejects(access(output),{code:'ENOENT'});
 }finally{await rm(root,{recursive:true,force:true});}
});
