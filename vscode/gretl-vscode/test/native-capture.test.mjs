import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {mkdtemp,readFile,rm} from 'node:fs/promises';
import os from 'node:os';import path from 'node:path';import {createRequire} from 'node:module';
const temp=await mkdtemp(path.join(os.tmpdir(),'gretl-native-capture-'));after(()=>rm(temp,{recursive:true,force:true}));
// Exercise the actual host method. Only external Workbench/LanguageClient services are replaced.
const source=await readFile('src/extension.ts','utf8');
await build({stdin:{contents:source+'\nexport {RuntimeHost};',sourcefile:'extension.ts',resolveDir:path.resolve('src'),loader:'ts'},bundle:true,platform:'node',format:'cjs',outfile:temp+'/host.cjs',plugins:[{name:'external-host-services',setup(b){b.onResolve({filter:/^(vscode|vscode-languageclient\/node)$/},a=>({path:a.path,namespace:'host-fixture'}));b.onLoad({filter:/.*/,namespace:'host-fixture'},()=>({contents:"module.exports={Uri:{parse:s=>({toString:()=>new URL(s).href})},workspace:{textDocuments:[]}}",loader:'js'}));}}]});
const {RuntimeHost}=createRequire(import.meta.url)(temp+'/host.cjs');
test('native rename captures graph schema two and preserves session and source-vector checks',async()=>{
 const host=Object.create(RuntimeHost.prototype);host.instance='server';host.captures=new WeakMap();
 const context={protocolVersion:1,schemaVersion:2,serverInstanceId:'server',snapshotId:'snapshot',sourceStamps:[{documentId:'file:///work/owner.groovy',editable:true}]};
 host.synchronizedGraph=async()=>({value:{context:structuredClone(context)}});
 const captured=await host.capture({}, {uri:{toString:()=> 'file:///work/owner.groovy'}},{});
 assert.equal(captured.schemaVersion,2);assert.equal(captured.sourceStamps[0].version,null);assert.equal(captured.sourceStamps[0].incarnation,null);assert(host.captures.has(captured));
 for(const change of [{schemaVersion:1},{schemaVersion:3},{serverInstanceId:'foreign'},{sourceStamps:Array(4097).fill(context.sourceStamps[0])}]){
  host.synchronizedGraph=async()=>({value:{context:{...context,...change}}});await assert.rejects(host.capture({}, {uri:{toString:()=> 'file:///work/owner.groovy'}},{}),/stale or incompatible/);
 }
});
