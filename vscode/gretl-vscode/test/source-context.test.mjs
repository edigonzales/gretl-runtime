import {test,after} from 'node:test';import assert from 'node:assert/strict';import {build} from 'esbuild';
import {mkdtemp,rm} from 'node:fs/promises';import os from 'node:os';import path from 'node:path';import {pathToFileURL} from 'node:url';
const temp=await mkdtemp(path.join(os.tmpdir(),'gretl-panel-context-'));after(()=>rm(temp,{recursive:true,force:true}));
await build({entryPoints:['src/modeler/source-context.ts'],bundle:true,platform:'node',format:'esm',outfile:path.join(temp,'contexts.mjs')});
const {pinnedContext}=await import(pathToFileURL(path.join(temp,'contexts.mjs')));
const north={projectId:'p',entryDocument:'file:///work/north/job.groovy',ownerScope:':north',role:'FRAGMENT',contextToken:'north-old'};
const south={...north,entryDocument:'file:///work/south/job.groovy',ownerScope:':south',contextToken:'south'};
test('panel renews its own application while text selection and other panel differ',()=>{
 const contexts={contexts:[{...north,contextToken:'north-new'},south],selectedToken:'south'};
 assert.equal(pinnedContext(contexts,north).contextToken,'north-new');assert.equal(pinnedContext(contexts,south).contextToken,'south');
 assert.equal(pinnedContext(contexts).contextToken,'south');assert.throws(()=>pinnedContext({contexts:[south]},north),/no longer available/);
 assert.equal(pinnedContext({contexts:[north,south]}),undefined);
});
test('cross-source navigation retains owner entry and scope without confusing source role',()=>{
 const entry={...north,role:'ENTRY',contextToken:'entry'};
 assert.equal(pinnedContext({contexts:[entry]},north,true).contextToken,'entry');assert.throws(()=>pinnedContext({contexts:[entry]},north));
});
