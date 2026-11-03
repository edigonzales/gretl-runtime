import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {build} from 'esbuild';
import {mkdtemp,rm} from 'node:fs/promises';
import os from 'node:os';import path from 'node:path';import {pathToFileURL} from 'node:url';
const temp=await mkdtemp(path.join(os.tmpdir(),'gretl-process-'));
after(()=>rm(temp,{recursive:true,force:true}));
await build({entryPoints:['src/process.ts'],bundle:true,platform:'node',format:'esm',outfile:path.join(temp,'process.mjs')});
const {endOwnedProcess}=await import(pathToFileURL(path.join(temp,'process.mjs')));
test('owned termination ends the chosen child, preserves another process and is idempotent',async()=>{
 const owned=spawn(process.execPath,['-e','setInterval(()=>{},1000)']),foreign=spawn(process.execPath,['-e','setInterval(()=>{},1000)']);
 try{await Promise.all([once(owned,'spawn'),once(foreign,'spawn')]);await endOwnedProcess(owned);assert.ok(owned.exitCode!==null||owned.signalCode!==null);assert.equal(foreign.exitCode,null);assert.equal(foreign.signalCode,null);await endOwnedProcess(owned);}
 finally{await endOwnedProcess(foreign);}
});
test('an owned process ignoring graceful stop receives bounded forced termination',async()=>{
 const owned=spawn(process.execPath,['-e',"process.on('SIGTERM',()=>{});console.log('ready');setInterval(()=>{},1000)"]);
 try{await once(owned.stdout,'data');const started=Date.now();await endOwnedProcess(owned);assert.equal(owned.signalCode,'SIGKILL');assert.ok(Date.now()-started<2500);}
 finally{await endOwnedProcess(owned);}
});
