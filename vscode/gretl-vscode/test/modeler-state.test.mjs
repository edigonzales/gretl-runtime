import{test}from'node:test';import assert from'node:assert/strict';import{build}from'esbuild';import{mkdtemp,rm}from'node:fs/promises';import os from'node:os';import path from'node:path';import{pathToFileURL}from'node:url';
const dir=await mkdtemp(path.join(os.tmpdir(),'gretl-modeler-state-'));await build({entryPoints:['src/modeler/graph-model.ts'],bundle:true,platform:'node',format:'esm',outfile:dir+'/model.mjs'});const m=await import(pathToFileURL(dir+'/model.mjs'));await rm(dir,{recursive:true});
const anchor={documentId:'file:///work/job.groovy',span:{startUtf16:0,endUtf16:12}};
const node=(id,key,parentId=null)=>({id,presentationKey:key,parentId,anchor,kind:'TASK',title:'<img onerror="bad">',inputs:[],outputs:[],diagnostics:[],editCapabilities:{}});
const graph=()=>({context:{protocolVersion:1,schemaVersion:2,serverInstanceId:'server',snapshotId:'snapshot',projectId:'project',workspaceGeneration:'1',sourceStamps:[]},payload:{status:'COMPLETE',nodes:[node('a','stable-a'),node('b','stable-b')],edges:[],parameters:[],cleanup:[],diagnostics:[]}});
const state=()=>({schemaVersion:1,key:'key',positions:{'stable-a':{x:20,y:30,parentKey:null},'stable-b':{x:80,y:90,parentKey:null}},viewport:{x:1,y:2,zoom:1}});
test('graph boundaries preserve labels as data, permit unanchored diagnostics and reject cycles/missing endpoints',()=>{
 const g=graph();g.payload.diagnostics.push({code:'INCOMPLETE',severity:'INFORMATION',message:'Missing source',anchor:{documentId:'file:///work/missing'}});assert.equal(m.graphResponse(g),g);assert.equal(g.payload.nodes[0].title,'<img onerror="bad">');
 g.payload.nodes[0].parentId='b';g.payload.nodes[1].parentId='a';assert.throws(()=>m.graphResponse(g),/PARENT/);
 const bad=graph();bad.payload.edges.push({id:'edge',source:'a',target:'missing',kind:'AFTER',anchor});assert.throws(()=>m.graphResponse(bad),/EDGE/);
});
test('positions survive only unique identities in the same project/document and hierarchy',()=>{
 const g=graph(),s=state();assert.deepEqual(m.reconcilePositions(g,s,'key'),s.positions);assert.deepEqual(m.reconcilePositions(g,s,'other'),{});
 g.payload.nodes[1].presentationKey='stable-a';assert.deepEqual(m.reconcilePositions(g,s,'key'),{});
 const moved=graph();moved.payload.nodes[0].parentId='b';assert.deepEqual(Object.keys(m.reconcilePositions(moved,s,'key')),['stable-b']);
});
test('view state rejects nonfinite positions and invalid zoom; reads never share mutable state',()=>{
 const s=state();s.positions['stable-a'].notViewData={source:'must not persist'};const read=m.readViewState(s,'key');assert.equal('notViewData' in read.positions['stable-a'],false);read.positions['stable-a'].x=999;assert.equal(s.positions['stable-a'].x,20);
 s.viewport.zoom=0;assert.equal(m.readViewState(s,'key'),undefined);s.viewport.zoom=1;s.positions['stable-a'].x=Infinity;assert.equal(m.readViewState(s,'key'),undefined);
});
test('message channel rejects another panel, URI/command injection and extra fields',()=>{
 const base={protocolVersion:1,panelId:'own',kind:'ready'};assert(m.modelerMessage(base,'own'));assert.equal(m.modelerMessage(base,'other'),undefined);
 for(const attack of [{...base,command:'workbench.action.terminal.new'},{...base,kind:'reveal',snapshotId:'s',elementId:'a',uri:'file:///elsewhere'},{...base,kind:'executeCommand'},{...base,kind:'options',previewTargets:[3],includeInnerInvocations:false}])assert.equal(m.modelerMessage(attack,'own'),undefined);
});
test('older request/layout results and disposed panels cannot be accepted',()=>{
 const gate=new m.RequestGate(),old=gate.next(),current=gate.next();assert.equal(gate.accepts(old),false);assert(gate.accepts(current));gate.dispose();assert.equal(gate.accepts(current),false);assert.equal(gate.accepts(gate.next()),false);
});

test('finalizer graph schema is explicit and carries conditional candidates without data ports',()=>{
 const g=graph();g.payload.edges.push({id:'final',source:'a',target:'b',kind:'FINALIZED_BY',anchor,schemaKnown:false});
 g.payload.preview={available:true,targets:['a'],orderedSymbols:['a','b'],normalSymbols:['a'],conditionalSymbols:['b']};
 assert.equal(m.graphResponse(g),g);
 const old=structuredClone(g);old.context.schemaVersion=1;assert.throws(()=>m.graphResponse(old),/CONTEXT/);
 const unknown=structuredClone(g);unknown.payload.edges[0].kind='UNKNOWN';assert.throws(()=>m.graphResponse(unknown),/EDGE/);
 const bad=structuredClone(g);bad.payload.preview.conditionalSymbols=['foreign'];assert.throws(()=>m.graphResponse(bad),/PREVIEW/);
});
