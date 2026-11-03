import {test} from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {mkdtemp,rm} from 'node:fs/promises';
import os from 'node:os';import path from 'node:path';import {pathToFileURL} from 'node:url';
const temp=await mkdtemp(path.join(os.tmpdir(),'gretl-current-read-'));
const output=path.join(temp,'query.mjs');
await build({entryPoints:['src/read-query.ts'],bundle:true,platform:'node',format:'esm',outfile:output});
const {currentRead}=await import(pathToFileURL(output));await rm(temp,{recursive:true});
test('transient content modifications are retried without caching an empty outline',async()=>{
 let calls=0;const symbols=[{name:'dirty task'}];
 assert.equal(await currentRead(async()=>{if(++calls<3)throw {code:-32801};return symbols;},()=>true),symbols);assert.equal(calls,3);
});
test('read retries are bounded and other errors are preserved',async()=>{
 let calls=0;const stale={code:-32801};await assert.rejects(currentRead(async()=>{calls++;throw stale;},()=>true),error=>error===stale);assert.equal(calls,3);
 const invalid={code:-32602};calls=0;await assert.rejects(currentRead(async()=>{calls++;throw invalid;},()=>true),error=>error===invalid);assert.equal(calls,1);
});
test('cancelled or replaced documents and sessions cannot return stale symbols',async()=>{
 let active=false,calls=0;await assert.rejects(currentRead(async()=>{calls++;},()=>active),/obsolete/);assert.equal(calls,0);
 active=true;await assert.rejects(currentRead(async()=>{active=false;return ['old'];},()=>active),/obsolete/);
 active=true;calls=0;await assert.rejects(currentRead(async()=>{calls++;active=false;throw {code:-32801};},()=>active));assert.equal(calls,1);
});
