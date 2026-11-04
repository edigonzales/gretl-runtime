import{test}from'node:test';import assert from'node:assert/strict';import{build}from'esbuild';import{mkdtemp,rm}from'node:fs/promises';import os from'node:os';import path from'node:path';import{pathToFileURL}from'node:url';
const temp=await mkdtemp(path.join(os.tmpdir(),'gretl-view-store-'));await build({entryPoints:['src/modeler/view-state-store.ts'],bundle:true,platform:'node',format:'esm',outfile:temp+'/store.mjs'});const{ViewStateStore}=await import(pathToFileURL(temp+'/store.mjs'));await rm(temp,{recursive:true});
const view=key=>({schemaVersion:1,key,positions:{node:{x:10,y:20,parentKey:null}},viewport:{x:1,y:2,zoom:1}});
test('concurrent panel saves retain separate records, remain detached and keep at most sixteen identities',async()=>{
 let saved={};const store=new ViewStateStore({get:()=>saved,update:async(_,value)=>{await new Promise(resolve=>setImmediate(resolve));saved=value;}});
 const first=view('one'),second=view('two');second.viewport.x=200;assert.deepEqual(await Promise.all([store.save(first),store.save(second)]),[true,true]);assert.equal(store.load('one').viewport.x,1);assert.equal(store.load('two').viewport.x,200);
 first.positions.node.x=999;assert.equal(store.load('one').positions.node.x,10);
 await Promise.all(Array.from({length:20},(_,i)=>store.save(view('p'+i))));assert.equal(Object.keys(saved).length,16);assert.equal(store.load('p0'),undefined);assert(store.load('p19'));
});
test('corrupt stored envelopes are ignored; write failures are reported and do not poison the save queue',async()=>{
 let saved=null,fail=true;const store=new ViewStateStore({get:()=>saved,update:async(_,value)=>{if(fail)throw Error('storage unavailable');saved=value;}});assert.equal(store.load('one'),undefined);assert.equal(await store.save(view('one')),false);fail=false;assert.equal(await store.save(view('two')),true);assert(store.load('two'));assert.equal(store.load('one'),undefined);
});
