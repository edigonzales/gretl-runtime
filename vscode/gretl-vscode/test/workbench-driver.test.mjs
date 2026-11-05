import {test} from 'node:test';import assert from 'node:assert/strict';
import {readFile,mkdtemp,readdir,rm} from 'node:fs/promises';import path from 'node:path';import os from 'node:os';import vm from 'node:vm';import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
test('buffer-only evidence captures pre-activation dirty Groovy without starting GRETL',async()=>{
 const directory=await mkdtemp(path.join(os.tmpdir(),'gretl-preactivation-evidence-')),commands=new Map();let executions=0;
 const uri={scheme:'file',toString:()=> 'file:///controlled/parts/custom.groovy'},text="// ä😀 dirty before membership\ntask('source',Gzip){}";
 const api={workspace:{getConfiguration:()=>({get:()=>directory}),getWorkspaceFolder:()=>({}),textDocuments:[{uri,version:7,isDirty:true,getText:()=>text}]},window:{activeTextEditor:undefined,setStatusBarMessage(){}},extensions:{getExtension:()=>({isActive:false})},commands:{registerCommand:(name,callback)=>{commands.set(name,callback);return{dispose(){}};},getCommands:async()=>[...commands.keys()],executeCommand:async()=>{executions++;throw Error('Inactive GRETL must not be called');}}};
 const module={exports:{}};
 try{
  vm.runInNewContext(await readFile('test/workbench-driver/extension.cjs','utf8'),{exports:module.exports,require:name=>name==='vscode'?api:require(name),process:{env:{}},setTimeout,clearTimeout,console});
  module.exports.activate({subscriptions:[]});await commands.get('gretlEvidence.captureBuffers')();
  const files=await readdir(directory);assert.equal(files.length,1);const captured=JSON.parse(await readFile(path.join(directory,files[0]),'utf8'));
  assert.equal(executions,0);assert.equal(captured.productActive,false);assert.deepEqual(captured.modelers,[]);assert.equal(captured.readOnly,true);
  assert.equal(captured.documents[0].text,text);assert.equal(captured.documents[0].version,7);assert.equal(captured.documents[0].dirty,true);
 }finally{await rm(directory,{recursive:true,force:true});}
});
