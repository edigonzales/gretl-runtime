import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {mkdtemp,readFile,rm,realpath,writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import os from 'node:os';import path from 'node:path';import {createRequire} from 'node:module';
const temp=await mkdtemp(path.join(os.tmpdir(),'gretl-native-capture-'));after(()=>rm(temp,{recursive:true,force:true}));
// Exercise the actual host. Only external Workbench/LanguageClient services are replaced.
const source=await readFile('src/extension.ts','utf8');
await build({stdin:{contents:source+'\nexport {RuntimeHost,sourceDigest,vscode};',sourcefile:'extension.ts',resolveDir:path.resolve('src'),loader:'ts'},bundle:true,platform:'node',format:'cjs',outfile:temp+'/host.cjs',plugins:[{name:'external-host-services',setup(b){b.onResolve({filter:/^(vscode|vscode-languageclient\/node)$/},a=>({path:a.path,namespace:'host-fixture'}));b.onLoad({filter:/.*/,namespace:'host-fixture'},()=>({contents:`module.exports={
 Uri:{parse:s=>({toString:()=>new URL(s).href})},
 workspace:{textDocuments:[],isTrusted:true,applyEdit(){},onDidGrantWorkspaceTrust(){},onDidChangeWorkspaceFolders(){},onDidOpenTextDocument(){},onDidChangeConfiguration(){}},
 window:{createOutputChannel:()=>({}),createStatusBarItem:()=>({show(){}})},
 StatusBarAlignment:{Left:1},EventEmitter:class{fire(){}},commands:{registerCommand(){}},
 TextDocumentEdit:{is:change=>!!change.textDocument&&Array.isArray(change.edits)}
}`,loader:'js'}));}}]});
const {RuntimeHost,sourceDigest,vscode}=createRequire(import.meta.url)(temp+'/host.cjs');
const documentId='file:///work/owner.groovy';
const doc={uri:{toString:()=>documentId},version:1,isClosed:false};
const token={isCancellationRequested:false};
const context={protocolVersion:1,schemaVersion:2,serverInstanceId:'server',snapshotId:'snapshot',projectId:'project',
 workspaceGeneration:'1',ownerGeneration:'2',contextGeneration:'3',contextDocumentId:documentId,contextToken:'chosen',
 sourceStamps:[{documentId,editable:true}],notices:[]};
function hostWith(sendRequest){
 const host=Object.create(RuntimeHost.prototype);
 Object.assign(host,{instance:'server',captures:new WeakMap(),client:{isRunning:()=>true,sendRequest}});
 return host;
}

test('native capture synchronizes membership with context requests and preserves capture validation',async()=>{
 const calls=[];
 const host=hostWith(async(method,params,cancellation)=>{
  calls.push(method);
  assert.equal(params.documentId,documentId);
  if(method==='gretl/sourceContexts')return {};
  assert.equal(method,'gretl/context');assert.equal(cancellation,token);
  return {context:{...structuredClone(context),notices:calls.length===1?['INCLUDE_MEMBERSHIP_PENDING']:[]}};
 });
 const captured=await host.capture(host.client,doc,token);
 assert.deepEqual(calls,['gretl/context','gretl/sourceContexts','gretl/context']);
 assert.equal(captured.schemaVersion,2);assert.equal(captured.sourceStamps[0].version,null);assert.equal(captured.sourceStamps[0].incarnation,null);assert(host.captures.has(captured));
 for(const change of [{schemaVersion:1},{schemaVersion:3},{serverInstanceId:'foreign'},{sourceStamps:Array(4097).fill(context.sourceStamps[0])}]){
  host.client.sendRequest=async()=>({context:{...context,...change}});
  await assert.rejects(host.capture(host.client,doc,token),/stale or incompatible/);
 }
});

test('current brackets the asynchronous disk check with two context requests and fails closed',async()=>{
 const file=path.join(await realpath(temp),'guard.groovy');
 const uri=pathToFileURL(file).href;
 const edit={documentChanges:[{textDocument:{uri,version:null},edits:[]}]};
 const source='action(\'a\') {}\n';
 const capture={...context,sourceStamps:[{documentId:uri,version:null,editable:true,sha256:sourceDigest(source)}]};
 for(const [secondReply,error] of [
  [{},null],
  [{snapshotId:'new snapshot'},/context changed/],
  [{ownerGeneration:'4'},/context changed/],
  [{notices:['INCLUDE_MEMBERSHIP_PENDING']},/synchronization is pending/],
  [null,/file changed/],
 ]){
  await writeFile(file,'stale before the first request');
  let count=0;
  const host=hostWith(async(method,params)=>{
   assert.equal(method,'gretl/context');
   assert.deepEqual(params,{protocolVersion:1,documentId,contextToken:'chosen'});
   if(++count===1){
    if(secondReply!==null)await writeFile(file,source);
    return {context};
   }
   return {context:{...context,...secondReply}};
  });
  const current=host.current(host.client,capture,edit);
  if(error)await assert.rejects(current,error);else await current;
  assert.equal(count,secondReply===null?1:2);
 }
 const host=hostWith(async method=>{assert.equal(method,'gretl/context');throw Error('context request failed');});
 await assert.rejects(host.current(host.client,capture,edit),/context request failed/);
});

test('quick fix rejects a buffer change while its final context response is pending',async t=>{
 const source="action('consumer') {}";
 const nativeEdit={};
 let applyQuickFix;
 const applied=[];
 t.mock.method(vscode.commands,'registerCommand',(name,handler)=>{
  if(name==='gretlRuntime.applyCodeAction')applyQuickFix=handler;
 });
 t.mock.method(vscode.workspace,'applyEdit',async edit=>{
  applied.push(edit);
  return true;
 });
 const buffer={...doc,text:source,getText(){return this.text;}};
 vscode.workspace.textDocuments.push(buffer);
 try{
  for(const changeBuffer of [false,true]){
   const host=new RuntimeHost({});
   const capture={...context,sourceStamps:[{documentId,version:1,editable:true,sha256:sourceDigest(source)}]};
   const position={line:0,character:0};
   const edit={documentChanges:[{textDocument:{uri:documentId,version:1},edits:[
    {range:{start:position,end:position},newText:'// fix\n'}
   ]}]};
   const pending=Promise.withResolvers();
   const reply=Promise.withResolvers();
   let requests=0;
   const client={isRunning:()=>true,protocol2CodeConverter:{asWorkspaceEdit:async()=>nativeEdit},sendRequest:async method=>{
    assert.equal(method,'gretl/context');
    if(++requests===4){
     pending.resolve();
     await reply.promise;
    }
    return {context:capture};
   }};
   Object.assign(host,{instance:'server',client});
   host.captures.set(capture,new Map([[documentId,buffer]]));
   host.actions.set('fix',{client,context:capture,edit,expires:Infinity});
   const started=applyQuickFix('fix');
   await Promise.race([pending.promise,started]);
   assert.equal(requests,4);
   if(changeBuffer){buffer.version++;buffer.text='// user edit\n'+source;}
   reply.resolve();
   if(changeBuffer)await assert.rejects(started,/GRETL buffer changed/);
   else await started;
   assert.deepEqual(applied,[nativeEdit]);
  }
 }finally{vscode.workspace.textDocuments.pop();}
});

test('modeler graph still requests and returns the full projection',async()=>{
 const payload={status:'COMPLETE',nodes:[],edges:[],parameters:[],cleanup:[],diagnostics:[]};
 const response={context:{...context,sourceStamps:[]},payload};
 const calls=[];
 const host=hostWith(async(method,params,cancellation)=>{
  calls.push(method);
  assert.deepEqual(params,{protocolVersion:1,documentId,contextToken:'chosen',includeInnerInvocations:true,previewTargets:[]});
  assert.equal(cancellation,token);
  return response;
 });
 host.panelContext=async()=>({projectId:'project',entryDocument:documentId,ownerScope:':',role:'ENTRY',contextToken:'chosen'});
 host.graphSourceContexts=new WeakMap();host.modelerCaptures=new WeakMap();
 const graph=await host.graph(doc,{includeInnerInvocations:true,previewTargets:[]},token);
 assert.equal(graph.payload,payload);
 assert.deepEqual(calls,['gretl/graph']);
});
