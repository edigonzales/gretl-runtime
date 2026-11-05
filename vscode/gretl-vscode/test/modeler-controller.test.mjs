import{test,after}from'node:test';import assert from'node:assert/strict';import{build}from'esbuild';import{mkdtemp,rm}from'node:fs/promises';import os from'node:os';import path from'node:path';import{pathToFileURL}from'node:url';import{createHash}from'node:crypto';
class Emitter{listeners=new Set();event=fn=>{this.listeners.add(fn);return{dispose:()=>this.listeners.delete(fn)}};fire(value){for(const fn of [...this.listeners])fn(value);}dispose(){this.listeners.clear();}}
class Uri{constructor(value){this.value=value;this.scheme=value.split(':')[0];}toString(){return this.value;}static parse(value){return new Uri(value);}static joinPath(base,...parts){return new Uri(base.toString().replace(/\/$/,'')+'/'+parts.join('/'));}}
class CancellationTokenSource{token={isCancellationRequested:false};cancel(){this.token.isCancellationRequested=true;}dispose(){}}
const commands=new Map(),opened=[],documents=new Map(),changes=new Emitter(),closed=new Emitter(),selectionChanges=new Emitter();
const api={Uri,CancellationTokenSource,ViewColumn:{One:1,Two:2,Beside:-2},TextEditorRevealType:{InCenterIfOutsideViewport:0},Range:class{constructor(...values){this.values=values;}},window:{registerCustomEditorProvider:()=>({dispose(){}}),activeTextEditor:undefined,onDidChangeTextEditorSelection:selectionChanges.event,showTextDocument:async document=>{opened.push(document);return{document,revealRange(){}}},showInformationMessage(){},showQuickPick:async values=>values[0]},workspace:{asRelativePath:uri=>uri.toString(),getWorkspaceFolder:uri=>uri.toString().startsWith('file:///work/')?{}:undefined,onDidChangeTextDocument:changes.event,onDidCloseTextDocument:closed.event,openTextDocument:async uri=>{const doc=documents.get(uri.toString());if(!doc)throw Error('Unknown test document');return doc;}},languages:{setTextDocumentLanguage:async(document,languageId)=>{document.isClosed=true;const next={...document,isClosed:false,languageId};documents.set(document.uri.toString(),next);return next;}},commands:{registerCommand:(id,fn)=>{commands.set(id,fn);return{dispose:()=>commands.delete(id)}},executeCommand:async()=>{}}};globalThis.__gretlModelerController=api;
const temp=await mkdtemp(path.join(os.tmpdir(),'gretl-panel-controller-'));
await build({stdin:{contents:"export {GretlModelerProvider} from './src/modeler/provider'; export {ModelerEditRejected} from './src/modeler/edit-plan';",resolveDir:process.cwd(),loader:'ts'},bundle:true,platform:'node',format:'esm',outfile:temp+'/provider.mjs',plugins:[{name:'vscode-test-boundary',setup(build){build.onResolve({filter:/^vscode$/},()=>({path:'vscode',namespace:'test'}));build.onLoad({filter:/.*/,namespace:'test'},()=>({contents:"const api=globalThis.__gretlModelerController;export const{Uri,CancellationTokenSource,ViewColumn,TextEditorRevealType,Range,window,workspace,languages,commands}=api;",loader:'js'}));}}]});const{GretlModelerProvider,ModelerEditRejected}=await import(pathToFileURL(temp+'/provider.mjs'));after(async()=>{delete globalThis.__gretlModelerController;await rm(temp,{recursive:true,force:true});});
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function doc(name){const document={uri:Uri.parse('file:///work/'+name+'/job.groovy'),version:1,isClosed:false,languageId:'gretl-runtime',getText:()=>"action('known') {}\n",offsetAt:()=>0};documents.set(document.uri.toString(),document);return document;}
function graph(document,snapshot,project='project'){const uri=document.uri.toString(),bytes=Buffer.from(document.getText(),'utf16le');bytes.swap16();return{context:{protocolVersion:1,schemaVersion:1,serverInstanceId:'instance',projectId:project,workspaceGeneration:'1',snapshotId:snapshot,sourceStamps:[{documentId:uri,sha256:createHash('sha256').update(bytes).digest('hex'),version:1,editable:true}],completeness:{},notices:[]},payload:{status:'COMPLETE',nodes:[{id:snapshot+':known',presentationKey:'known',kind:'ACTION',title:'known',anchor:{documentId:uri,span:{startUtf16:0,endUtf16:18}},inputs:[],outputs:[],diagnostics:[],editCapabilities:{}}],edges:[],parameters:[],cleanup:[],diagnostics:[]}};}
function fixture(){const pending=[],navigations=[];const available=new Emitter(),analysis=new Emitter();let data={};const backend={onDidChange:available.event,onDidAnalyze:analysis.event,catalog:async graph=>({protocolVersion:1,schemaVersion:1,serverInstanceId:graph.context.serverInstanceId,state:'AVAILABLE',catalog:{catalogId:'catalog',operations:[],coverage:'COMPLETE'},notices:[]}),commit:async()=>{throw Error('No test edit configured');},graph:(document,options,token)=>new Promise(resolve=>pending.push({document,options,token,resolve})),navigate:async(...args)=>{navigations.push(args);return{context:{snapshotId:args[1]},elements:[]};}};const provider=new GretlModelerProvider({extensionUri:Uri.parse('file:///extension'),workspaceState:{get:(_,fallback)=>data??fallback,update:async(_,value)=>{data=value;}}},backend);return{provider,pending,navigations,backend,available,get stored(){return data;}};}
function panel(provider,document){const receive=new Emitter(),dispose=new Emitter(),view=new Emitter(),messages=[];const p={visible:true,active:false,viewColumn:2,webview:{cspSource:'https://resources.invalid',options:{},asWebviewUri:uri=>Uri.parse(uri.toString().replace('file:','https:')),onDidReceiveMessage:receive.event,postMessage:value=>{messages.push(value);return Promise.resolve(true);}},onDidDispose:dispose.event,onDidChangeViewState:view.event,reveal(){},dispose(){dispose.fire();}};const resolved=provider.resolveCustomTextEditor(document,p,{isCancellationRequested:false});const id=()=>JSON.parse(p.webview.html.match(/id="bootstrap" type="application\/json">([^<]+)/)[1]).panelId;return{messages,resolved,get id(){return id();},get html(){return p.webview.html;},send:value=>receive.fire({protocolVersion:1,panelId:id(),...value}),dispose:()=>dispose.fire()};}
test('late requests cannot replace a newer graph or silently follow the active editor',async()=>{
 const f=fixture(),a=doc('a'),other=doc('other'),p=panel(f.provider,a);try{
  p.send({kind:'ready'});await tick();assert.equal(f.pending.length,1);api.window.activeTextEditor={document:other};p.send({kind:'refresh'});await tick();assert.equal(f.pending.length,2);assert(f.pending[0].token.isCancellationRequested);assert(f.pending.every(request=>request.document===a));
  f.pending[1].resolve(graph(a,'new'));await tick();f.pending[0].resolve(graph(a,'old'));await tick();assert.deepEqual(p.messages.filter(x=>x.kind==='graph').map(x=>x.graph.context.snapshotId),['new']);
 }finally{f.provider.dispose();api.window.activeTextEditor=undefined;}
});
test('disposed panels ignore late graph responses and unregister all source/selection listeners',async()=>{
 const f=fixture(),a=doc('dispose'),p=panel(f.provider,a);p.send({kind:'ready'});await tick();p.dispose();assert(f.pending[0].token.isCancellationRequested);f.pending[0].resolve(graph(a,'late'));await tick();assert.equal(p.messages.filter(x=>x.kind==='graph').length,0);f.provider.dispose();assert.equal(changes.listeners.size,0);assert.equal(closed.listeners.size,0);assert.equal(selectionChanges.listeners.size,0);
});
test('two panels keep independent pinned identities and reject state or navigation from another snapshot',async()=>{
 const f=fixture(),a=doc('panel-a'),b=doc('panel-b'),pa=panel(f.provider,a),pb=panel(f.provider,b);try{
  pa.send({kind:'ready'});pb.send({kind:'ready'});await tick();f.pending[0].resolve(graph(a,'a-snapshot','a-project'));f.pending[1].resolve(graph(b,'b-snapshot','b-project'));await tick();
  const ga=pa.messages.find(x=>x.kind==='graph'),gb=pb.messages.find(x=>x.kind==='graph');assert.notEqual(ga.key,gb.key);
  const state=key=>({schemaVersion:1,key,positions:{},viewport:{x:1,y:2,zoom:1}});pa.send({kind:'viewState',snapshotId:'a-snapshot',state:state(gb.key)});await tick();assert.deepEqual(f.stored,{});
  pa.send({kind:'viewState',snapshotId:'a-snapshot',state:state(ga.key)});pb.send({kind:'viewState',snapshotId:'b-snapshot',state:state(gb.key)});await tick();await tick();assert.deepEqual(new Set(Object.keys(f.stored)),new Set([ga.key,gb.key]));
  pa.send({kind:'reveal',snapshotId:'b-snapshot',elementId:'b-snapshot:known'});await tick();assert.equal(f.navigations.length,0);pa.send({kind:'reveal',snapshotId:'a-snapshot',elementId:'a-snapshot:known'});await tick();assert.equal(f.navigations[0][0],a.uri.toString());assert.equal(f.navigations[0][1],'a-snapshot');
 }finally{f.provider.dispose();}
});

test('server invalidation blocks old navigation and discards a response from before restart',async()=>{
 const f=fixture(),a=doc('restart'),p=panel(f.provider,a);try{
  p.send({kind:'ready'});await tick();f.pending[0].resolve(graph(a,'before'));await tick();p.send({kind:'refresh'});await tick();assert.equal(f.pending.length,2);
  f.available.fire(false);assert(f.pending[1].token.isCancellationRequested);p.send({kind:'reveal',snapshotId:'before',elementId:'before:known'});await tick();assert.equal(f.navigations.length,0);
  f.available.fire(true);const deadline=Date.now()+2000;while(f.pending.length<3){if(Date.now()>deadline)throw Error('Expected debounced restart refresh');await new Promise(resolve=>setTimeout(resolve,5));}
  f.pending[2].resolve(graph(a,'after'));await tick();f.pending[1].resolve(graph(a,'obsolete'));await tick();assert.deepEqual(p.messages.filter(x=>x.kind==='graph').map(x=>x.graph.context.snapshotId),['before','after']);
 }finally{f.provider.dispose();}
});
test('workspace labels cannot escape bootstrap JSON, and another panel cannot start an analysis',async()=>{
 const f=fixture(),a=doc('label</script><img src=x>'),p=panel(f.provider,a);try{
  assert.equal((p.html.match(/<script\b/g)??[]).length,2);assert.equal(p.html.includes('<img src=x>'),false);assert(p.html.includes('\\u003c'));
  p.send({kind:'ready',panelId:'other'});await tick();assert.equal(f.pending.length,0);p.send({kind:'ready'});await tick();assert.equal(f.pending.length,1);
 }finally{f.provider.dispose();}
});
test('a revealed source range maps from its start rather than its exclusive end',async()=>{
 const f=fixture(),a=doc('range'),p=panel(f.provider,a);a.offsetAt=position=>position.character;
 try{
  p.send({kind:'ready'});await tick();f.pending[0].resolve(graph(a,'range-snapshot'));await tick();
  const editor={document:a,selection:{start:{line:0,character:0},active:{line:0,character:18},isEmpty:false}};
  selectionChanges.fire({textEditor:editor});await tick();assert.equal(f.navigations.at(-1)[3],0);
  editor.selection={start:{line:0,character:9},active:{line:0,character:9},isEmpty:true};
  selectionChanges.fire({textEditor:editor});await tick();assert.equal(f.navigations.at(-1)[3],9);
 }finally{f.provider.dispose();}
});
test('read-only inspection accepts only its outstanding nonce and current snapshot, and disposal resolves it',async()=>{
 const f=fixture(),a=doc('inspection'),p=panel(f.provider,a);try{
  p.send({kind:'ready'});await tick();f.pending[0].resolve(graph(a,'inspection-snapshot'));await tick();
  let finished=false;const result=commands.get('gretlRuntime.inspectModeler')().then(value=>{finished=true;return value;});await tick();
  const request=p.messages.findLast(m=>m.kind==='inspect');
  p.send({kind:'inspection',requestId:'wrong',snapshotId:request.snapshotId,view:{}});
  p.send({kind:'inspection',requestId:request.requestId,snapshotId:'obsolete',view:{}});await tick();assert.equal(finished,false);
  p.send({kind:'inspection',requestId:request.requestId,snapshotId:request.snapshotId,view:{viewport:{x:1,y:2,zoom:1}}});
  assert.equal((await result)[0].documentId,a.uri.toString());assert.equal(f.pending.length,1);assert.equal(f.navigations.length,0);
  const disposed=commands.get('gretlRuntime.inspectModeler')();p.dispose();assert.deepEqual(await disposed,[{unavailable:'disposed'}]);
 }finally{f.provider.dispose();}
});

test('restored non-job custom editors share the current GRETL document incarnation before querying',async()=>{
 const f=fixture(),a=doc('restored');a.languageId='groovy';const pa=panel(f.provider,a),pb=panel(f.provider,a);
 try{await Promise.all([pa.resolved,pb.resolved]);pa.send({kind:'ready'});pb.send({kind:'ready'});await tick();
  assert(a.isClosed);assert.equal(f.pending.length,2);assert(f.pending.every(r=>r.document.languageId==='gretl-runtime'&&!r.document.isClosed));assert.equal(f.pending[0].document,f.pending[1].document);
 }finally{f.provider.dispose();}
});

test('two panels of the same source keep separate persisted view slots',async()=>{
 const f=fixture(),a=doc('same-source'),pa=panel(f.provider,a),pb=panel(f.provider,a);
 try{pa.send({kind:'ready'});pb.send({kind:'ready'});await tick();for(const request of f.pending)request.resolve(graph(a,'shared-snapshot'));await tick();
  const ga=pa.messages.find(m=>m.kind==='graph'),gb=pb.messages.find(m=>m.kind==='graph');assert.notEqual(ga.key,gb.key);
  for(const [p,g,x]of[[pa,ga,10],[pb,gb,90]])p.send({kind:'viewState',snapshotId:'shared-snapshot',state:{schemaVersion:1,key:g.key,positions:{},viewport:{x,y:20,zoom:1}}});await tick();await tick();
  assert.equal(f.stored[ga.key].viewport.x,10);assert.equal(f.stored[gb.key].viewport.x,90);
  pa.send({kind:'viewState',snapshotId:'shared-snapshot',state:{schemaVersion:1,key:gb.key,positions:{},viewport:{x:500,y:20,zoom:1}}});await tick();assert.equal(f.stored[gb.key].viewport.x,90);
 }finally{f.provider.dispose();}
});
test('closing a panel cancels outstanding source navigation and cannot open a late location',async()=>{
 const f=fixture(),a=doc('late-navigation'),p=panel(f.provider,a);let complete,requestToken;f.backend.navigate=(...args)=>{requestToken=args[4];return new Promise(resolve=>{complete=resolve;});};
 try{p.send({kind:'ready'});await tick();f.pending[0].resolve(graph(a,'navigation-snapshot'));await tick();const before=opened.length;
  p.send({kind:'reveal',snapshotId:'navigation-snapshot',elementId:'navigation-snapshot:known'});await tick();p.dispose();assert(requestToken.isCancellationRequested);
  complete({elements:[],location:{uri:a.uri.toString(),range:{start:{line:0,character:0},end:{line:0,character:18}}}});await tick();assert.equal(opened.length,before);
 }finally{f.provider.dispose();}
});
test('a cancelled older navigation cannot mark the current graph stale',async()=>{
 const f=fixture(),a=doc('superseded-navigation'),p=panel(f.provider,a),requests=[];f.backend.navigate=(...args)=>new Promise((resolve,reject)=>requests.push({token:args[4],resolve,reject}));
 try{p.send({kind:'ready'});await tick();f.pending[0].resolve(graph(a,'current'));await tick();
  for(let i=0;i<2;i++){p.send({kind:'reveal',snapshotId:'current',elementId:'current:known'});await tick();}
  assert(requests[0].token.isCancellationRequested);requests[0].reject(Error('cancelled'));requests[1].resolve({elements:[]});await tick();
  assert.equal(p.messages.some(m=>m.kind==='state'&&m.status==='STALE'),false);
 }finally{f.provider.dispose();}
});
test('semantic messages are pinned, snapshot guarded and serialize confirmations without optimistic graph changes',async()=>{
 const f=fixture(),a=doc('semantic-edit'),p=panel(f.provider,a),commits=[];let finish;
 f.backend.commit=(...args)=>{commits.push(args);return new Promise(resolve=>{finish=resolve;});};
 try{p.send({kind:'ready'});await tick();const g=graph(a,'edit-current');f.pending[0].resolve(g);await tick();const intent={kind:'AddAction',name:'added',insertionOffset:a.getText().length};
  p.send({kind:'intent',requestId:'obsolete',snapshotId:'old',intent});p.send({kind:'intent',requestId:'foreign',snapshotId:'edit-current',intent,panelId:'other'});p.send({kind:'intent',requestId:'injected',snapshotId:'edit-current',intent:{...intent,textEdits:[]}});await tick();assert.equal(commits.length,0);
  p.send({kind:'intent',requestId:'first',snapshotId:'edit-current',intent});p.send({kind:'intent',requestId:'duplicate',snapshotId:'edit-current',intent});await tick();assert.equal(commits.length,1);assert.equal(commits[0][0],a);assert.equal(commits[0][1],g);assert.deepEqual(commits[0][2],intent);assert.equal(p.messages.find(m=>m.kind==='editResult'&&m.requestId==='duplicate').applied,false);
  assert.equal(p.messages.filter(m=>m.kind==='graph').length,1);finish(['Planned in Core']);await tick();assert.equal(p.messages.find(m=>m.kind==='editResult'&&m.requestId==='first').applied,true);assert.equal(p.messages.filter(m=>m.kind==='graph').length,1);assert.equal(a.version,1);
 }finally{f.provider.dispose();}
});
test('panel disposal and source invalidation cancel pending semantic edits, with no late UI resurrection',async()=>{
 for(const dispose of[true,false]){const f=fixture(),a=doc('edit-cancel-'+dispose),p=panel(f.provider,a);let reject,token;f.backend.commit=(document,graph,intent,t)=>{token=t;return new Promise((resolve,r)=>{reject=r;});};
  try{p.send({kind:'ready'});await tick();f.pending[0].resolve(graph(a,'before-edit'));await tick();p.send({kind:'intent',requestId:'edit',snapshotId:'before-edit',intent:{kind:'AddAction',name:'added',insertionOffset:0}});await tick();const count=p.messages.length;
   if(dispose)p.dispose();else changes.fire({document:a,contentChanges:[{text:'external change'}]});assert(token.isCancellationRequested);reject(Error('EDIT_CANCELLED'));await tick();if(dispose)assert.equal(p.messages.length,count);else assert.equal(p.messages.findLast(m=>m.kind==='editResult').applied,false);
  }finally{f.provider.dispose();}
 }
});
test('a superseded catalogue request cannot repopulate a newer graph or disposed panel',async()=>{
 const f=fixture(),a=doc('catalog-race'),p=panel(f.provider,a),catalogs=[];f.backend.catalog=(graph,token)=>new Promise(resolve=>catalogs.push({graph,token,resolve}));
 try{p.send({kind:'ready'});await tick();f.pending[0].resolve(graph(a,'old'));await tick();p.send({kind:'refresh'});await tick();f.pending[1].resolve(graph(a,'new'));await tick();assert(catalogs[0].token.isCancellationRequested);catalogs[0].resolve({state:'AVAILABLE'});await tick();assert.equal(p.messages.filter(m=>m.kind==='catalog').length,0);p.dispose();catalogs[1].resolve({state:'AVAILABLE'});await tick();assert.equal(p.messages.filter(m=>m.kind==='catalog').length,0);
 }finally{f.provider.dispose();}
});
test('opening required sources consumes only a host-issued request nonce and captured workspace URIs',async()=>{
 const f=fixture(),a=doc('required-main'),other=doc('required-side'),p=panel(f.provider,a),reads=[];const original=api.workspace.openTextDocument;api.workspace.openTextDocument=async uri=>{reads.push(uri.toString());return original(uri);};
 f.backend.commit=async()=>{throw new ModelerEditRejected(['EDIT_OPEN_DOCUMENTS'],[other.uri.toString()]);};
 try{p.send({kind:'ready'});await tick();const g=graph(a,'required');g.context.sourceStamps.push({documentId:other.uri.toString(),editable:true});f.pending[0].resolve(g);await tick();p.send({kind:'intent',requestId:'confirmation',snapshotId:'required',intent:{kind:'AddAction',name:'added',insertionOffset:0}});await tick();assert.deepEqual(p.messages.findLast(m=>m.kind==='editResult').requiredOpenDocuments,[other.uri.toString()]);
  p.send({kind:'openRequiredSources',requestId:'other'});p.send({kind:'openRequiredSources',requestId:'confirmation',uri:'file:///outside'});await tick();assert.deepEqual(reads,[]);p.send({kind:'openRequiredSources',requestId:'confirmation'});await tick();assert.deepEqual(reads,[other.uri.toString()]);p.send({kind:'openRequiredSources',requestId:'confirmation'});await tick();assert.equal(reads.length,1);
 }finally{api.workspace.openTextDocument=original;f.provider.dispose();}
});
test('native text undo/redo and save are scoped to the pinned document, without accepting arbitrary commands',async()=>{
 const f=fixture(),a=doc('native-undo'),p=panel(f.provider,a),calls=[];const oldShow=api.window.showTextDocument,oldExecute=api.commands.executeCommand;a.save=async()=>{calls.push('save');return true;};api.window.showTextDocument=async document=>{assert.equal(document,a);api.window.activeTextEditor={document};return{document,revealRange(){}};};api.commands.executeCommand=async command=>{calls.push(command);};
 try{for(const kind of['undoText','redoText','saveSource']){p.send({kind});await tick();}assert.deepEqual(calls,['undo','redo','save']);p.send({kind:'undoText',command:'terminal'});p.send({kind:'executeCommand',command:'terminal'});await tick();assert.deepEqual(calls,['undo','redo','save']);
  let finish;api.window.showTextDocument=()=>new Promise(resolve=>{finish=resolve;});p.send({kind:'undoText'});await tick();p.dispose();finish({document:a});await tick();assert.deepEqual(calls,['undo','redo','save']);
 }finally{api.window.showTextDocument=oldShow;api.commands.executeCommand=oldExecute;api.window.activeTextEditor=undefined;f.provider.dispose();}
});

test('panel pins survive view controls without entering editable webview options; reveal confirms the node owner',async()=>{
 const f=fixture(),a=doc('context-panel'),p=panel(f.provider,a),pin={projectId:'p',entryDocument:'file:///work/north/job.groovy',ownerScope:':north',role:'FRAGMENT'},confirmations=[];
 f.backend.activateSourceContext=async(...args)=>{confirmations.push(args);};
 f.backend.navigate=async(...args)=>({context:{snapshotId:args[1]},elements:[],sourceContext:pin,location:{uri:a.uri.toString(),range:{start:{line:0,character:0},end:{line:0,character:1}}}});
 try{
  p.send({kind:'ready'});await tick();f.pending[0].options.sourceContext=pin;f.pending[0].resolve(graph(a,'pinned'));await tick();
  const published=p.messages.find(m=>m.kind==='graph');assert.deepEqual(published.sourceContext,pin);assert.deepEqual(Object.keys(published.options).sort(),['includeInnerInvocations','previewTargets']);
  p.send({kind:'reveal',snapshotId:'pinned',elementId:'pinned:known'});await tick();assert.equal(confirmations.length,1);assert.equal(confirmations[0][0],a.uri.toString());assert.deepEqual(confirmations[0][1],pin);
  p.send({kind:'options',previewTargets:[],includeInnerInvocations:true});const deadline=Date.now()+2000;while(f.pending.length<2){assert(Date.now()<deadline,'View-control refresh did not arrive');await new Promise(resolve=>setTimeout(resolve,5));}assert.deepEqual(f.pending[1].options.sourceContext,pin);
 }finally{f.provider.dispose();}
});
