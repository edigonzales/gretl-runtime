import{test,after}from'node:test';import assert from'node:assert/strict';import{build}from'esbuild';import{mkdtemp,rm}from'node:fs/promises';import os from'node:os';import path from'node:path';import{pathToFileURL}from'node:url';import{createServer}from'node:http';
const temp=await mkdtemp(path.join(os.tmpdir(),'gretl-blob-worker-'));await build({entryPoints:['webview/layout.ts'],bundle:true,platform:'node',format:'esm',outfile:temp+'/layout.mjs'});const{localWorkerResource,LayoutService}=await import(pathToFileURL(temp+'/layout.mjs'));
const script='self.onmessage = event => self.postMessage(event.data);';const server=createServer((req,res)=>{if(req.url==='/worker'){res.end(script);}else if(req.url==='/large'){res.end(Buffer.alloc(4*1024*1024+1));}else if(req.url==='/pending'){}else{res.statusCode=404;res.end();}});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const base='http://127.0.0.1:'+server.address().port;
after(async()=>{server.closeAllConnections();await new Promise(resolve=>server.close(resolve));await rm(temp,{recursive:true,force:true});});
test('bundled worker source becomes an exact Blob resource and its object URL is revoked',async()=>{
 const resource=await localWorkerResource(base+'/worker',new AbortController().signal);assert.match(resource.url,/^blob:/);assert.equal(await(await fetch(resource.url)).text(),script);resource.dispose();await assert.rejects(fetch(resource.url));
});
test('resource errors, byte limits and aborts fail closed before worker construction',async()=>{
 await assert.rejects(localWorkerResource(base+'/missing',new AbortController().signal),/LAYOUT_WORKER_RESOURCE/);await assert.rejects(localWorkerResource(base+'/large',new AbortController().signal),/LAYOUT_WORKER_RESOURCE_LIMIT/);
 const abort=new AbortController();const pending=localWorkerResource(base+'/pending',abort.signal);abort.abort();await assert.rejects(pending,{name:'AbortError'});
});
test('a source resource completing after panel disposal is released and cannot start a worker',async()=>{
 let finish,released=0;const service=new LayoutService('local',()=>new Promise(resolve=>{finish=resolve;}));
 const pending=service.layout([{id:'node',kind:'TASK',title:'node',inputs:[],outputs:[],diagnostics:[],editCapabilities:{}}],[],{}).catch(error=>error);service.dispose();finish({url:'must-not-be-used',dispose(){released++;}});
 assert.match(String(await pending),/LAYOUT_CANCELLED/);assert.equal(released,1);
});
