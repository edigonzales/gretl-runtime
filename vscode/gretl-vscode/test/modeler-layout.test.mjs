import{test,after}from'node:test';import assert from'node:assert/strict';import{build}from'esbuild';import{mkdtemp,rm}from'node:fs/promises';import os from'node:os';import path from'node:path';import{pathToFileURL}from'node:url';import{Worker as Thread}from'node:worker_threads';
const temp=await mkdtemp(path.join(os.tmpdir(),'gretl-elk-worker-'));await build({entryPoints:['webview/layout.ts'],bundle:true,platform:'node',format:'esm',outfile:temp+'/layout.mjs'});const{LayoutService}=await import(pathToFileURL(temp+'/layout.mjs'));
const original=globalThis.Worker,threads=new Set();
globalThis.Worker=class BrowserWorkerTransport {
 constructor(uri){this.thread=new Thread("const{parentPort,workerData}=require('node:worker_threads');global.self=global;global.postMessage=value=>parentPort.postMessage(value);require(workerData);parentPort.on('message',data=>global.onmessage({data}));",{eval:true,workerData:uri});threads.add(this.thread);this.thread.on('message',data=>this.onmessage?.({data}));this.thread.on('error',error=>this.onerror?.(error));}
 postMessage(value){this.thread.postMessage(value);}
 terminate(){void this.thread.terminate().finally(()=>threads.delete(this.thread));}
};
after(async()=>{globalThis.Worker=original;await Promise.all([...threads].map(w=>w.terminate()));await rm(temp,{recursive:true,force:true});});
const worker=path.resolve('node_modules/elkjs/lib/elk-worker.min.js');const anchor={documentId:'file:///job.groovy',span:{startUtf16:0,endUtf16:1}};
const node=(id,parentId=null,kind='TASK')=>({id,parentId,kind,presentationKey:id,title:id,anchor,inputs:[],outputs:[],diagnostics:[],editCapabilities:{}});
const edge=(id,source,target,kind='DEPENDS_ON')=>({id,source,target,kind,anchor,schemaKnown:false});
test('actual ELK in a separate worker lays out nested project nodes and preserves view-only positions',async()=>{
 const layout=new LayoutService(worker,async url=>({url,dispose(){}}));try{
  const nodes=[node('root',null,'PROJECT'),node('sub',null,'PROJECT'),node('producer','root'),node('consumer','sub')],edges=[edge('data','producer','consumer'),edge('order','producer','consumer','AFTER')];const before=JSON.stringify({nodes,edges});
  const result=await layout.layout(nodes,edges,{});assert.equal(result.length,4);const byId=new Map(result.map(g=>[g.id,g]));
  for(const n of nodes){const g=byId.get(n.id);assert(g.width>0&&g.height>0);if(n.parentId){const parent=byId.get(n.parentId);assert(g.x>=0&&g.y>=0);assert(g.x+g.width<=parent.width);assert(g.y+g.height<=parent.height);}}
  const kept=await layout.layout(nodes,edges,{producer:{x:35,y:65,parentKey:'root'}});assert.equal(kept.find(g=>g.id==='producer').x,35);assert.equal(kept.find(g=>g.id==='producer').y,65);assert.equal(JSON.stringify({nodes,edges}),before);
 }finally{layout.dispose();}
});
test('new layouts cancel the actual old worker; disposal rejects late calls instead of resurrecting workers',async()=>{
 const layout=new LayoutService(worker,async url=>({url,dispose(){}}));const nodes=Array.from({length:250},(_,i)=>node('n'+i)),edges=nodes.slice(1).map((n,i)=>edge('e'+i,'n'+i,n.id));
 const old=layout.layout(nodes,edges,{}).catch(error=>error);await new Promise(resolve=>setImmediate(resolve));assert(threads.size>0);const current=await layout.layout([node('latest')],[],{});assert.match(String(await old),/LAYOUT_CANCELLED/);assert.deepEqual(current.map(n=>n.id),['latest']);
 layout.dispose();await assert.rejects(layout.layout([node('after-dispose')],[],{}),/LAYOUT_DISPOSED/);
});
test('actual nested multi-edge job geometry keeps sibling cards separate',async()=>{
 const layout=new LayoutService(worker,async url=>({url,dispose(){}}));try{
  const nodes=[node('root',null,'PROJECT'),node('sub',null,'PROJECT'),node('download','root'),node('extract','root'),node('done','root','ACTION'),node('child','sub','ACTION')],edges=[edge('dep','download','extract'),edge('order','download','extract','AFTER'),edge('binding','download','extract','RESULT_BINDING'),edge('done-dep','extract','done'),edge('child-dep','child','done')];
  const result=await layout.layout(nodes,edges,{}),byId=new Map(result.map(g=>[g.id,g]));
  for(const a of nodes)for(const b of nodes){if(a.id>=b.id||a.parentId!==b.parentId)continue;const x=byId.get(a.id),y=byId.get(b.id);assert(x.x+x.width<=y.x||y.x+y.width<=x.x||x.y+x.height<=y.y||y.y+y.height<=x.y,JSON.stringify({a:x,b:y}));}
 }finally{layout.dispose();}
});
