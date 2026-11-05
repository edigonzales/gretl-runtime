import {test,after} from 'node:test';import assert from 'node:assert/strict';import {build} from 'esbuild';
import {mkdtemp,rm} from 'node:fs/promises';import os from 'node:os';import path from 'node:path';import {pathToFileURL} from 'node:url';
const temp=await mkdtemp(path.join(os.tmpdir(),'gretl-fragment-membership-'));after(()=>rm(temp,{recursive:true,force:true}));
await build({entryPoints:['src/fragment-documents.ts'],bundle:true,platform:'node',format:'esm',outfile:path.join(temp,'fragments.mjs')});
const {FragmentDocumentCoordinator,membershipUpdate,sourceContexts}=await import(pathToFileURL(path.join(temp,'fragments.mjs')));
const instance='11111111-1111-4111-8111-111111111111',token='22222222-2222-4222-8222-222222222222',root='file:///work',fragment=root+'/tasks/custom.groovy';
const update=(members=[fragment],generation='1')=>({protocolVersion:1,includeToolingVersion:1,serverInstanceId:instance,workspaceRoot:root,anchorDocument:root+'/job.groovy',ownerGeneration:generation,syncToken:token,ownersComplete:true,members:members.map(documentId=>({documentId,contextCount:1}))});
const doc=(uri=fragment,languageId='groovy',text='// ä😀\r\ntask(\'dirty\',Echo){}',version=9)=>({uri,languageId,text,version,dirty:true,closed:false,getText(){return this.text;}});
function fixture(){let coordinator;const docs=[doc(),doc(root+'/unrelated.groovy'),doc(root+'/build.gradle')],changes=[],synced=[],confirms=[];
 const host={instance:()=>instance,normalize:uri=>new URL(uri).href,owns:uri=>uri.startsWith(root+'/'),documents:()=>docs,
  async changeLanguage(previous,languageId){previous.closed=true;const next={...previous,languageId,closed:false,version:1};docs.splice(docs.indexOf(previous),1,next);changes.push([previous.uri,languageId]);coordinator.closed(previous);void coordinator.opened(next);return next;},
  async synchronize(d){synced.push([d.uri,d.version,d.getText()]);},async closed(){},digest:text=>'hash:'+text,
  async confirm(u,buffers){confirms.push({u,buffers});return true;}};
 coordinator=new FragmentDocumentCoordinator(host);return {coordinator,docs,changes,synced,confirms};}
test('dirty Groovy member retains bytes and dirty flag across new incarnation; unrelated files retain mode',async()=>{
 const f=fixture(),original=f.docs[0],text=original.text;await f.coordinator.acceptMembership(update());await f.coordinator.settled();
 assert(original.closed);assert.equal(f.docs[0].languageId,'gretl-runtime');assert.equal(f.docs[0].text,text);assert(f.docs[0].dirty);
 assert.equal(f.docs[1].languageId,'groovy');assert.equal(f.docs[2].languageId,'groovy');assert.equal(f.changes.length,1);
 assert.deepEqual(f.confirms.at(-1).buffers,[{documentId:fragment,version:1,sha256:'hash:'+text}]);
 await f.coordinator.acceptMembership(update([],'2'));assert.equal(f.docs[0].languageId,'groovy');assert.equal(f.docs[0].text,text);assert(f.docs[0].dirty);await f.coordinator.dispose();
});
test('explicit user mode is preserved while member text still synchronizes',async()=>{
 const f=fixture();await f.coordinator.acceptMembership(update());const previous=f.docs[0];previous.closed=true;
 const chosen={...previous,languageId:'groovy',closed:false,version:2};f.docs[0]=chosen;f.coordinator.closed(previous);await f.coordinator.opened(chosen);await f.coordinator.settled();
 assert.equal(chosen.languageId,'groovy');assert.equal(f.changes.length,1);assert(f.synced.some(s=>s[1]===2&&s[2]===chosen.text));
 await f.coordinator.acceptMembership(update([fragment],'2'));assert.equal(f.changes.length,1);await f.coordinator.dispose();
});
test('stale sessions ignored; duplicate members, malformed generations and forged selection rejected',async()=>{
 const f=fixture();await f.coordinator.acceptMembership({...update(),serverInstanceId:'33333333-3333-4333-8333-333333333333'});assert.equal(f.changes.length,0);
 assert.throws(()=>membershipUpdate({...update(),members:[...update().members,...update().members]}));assert.throws(()=>membershipUpdate({...update(),ownerGeneration:1}));
 assert.throws(()=>sourceContexts({protocolVersion:1,includeToolingVersion:1,serverInstanceId:instance,documentId:fragment,ownerGeneration:'1',contextGeneration:'1',ownersComplete:true,selectionRequired:false,selectedToken:token,contexts:[]}));
 await assert.rejects(f.coordinator.acceptMembership(update(['file:///outside/secret.groovy'])));await f.coordinator.dispose();
});
test('membership removal supersedes queued transition and obsolete confirmation',async()=>{
 const f=fixture();await Promise.all([f.coordinator.acceptMembership(update()),f.coordinator.acceptMembership(update([],'2'))]);
 assert.equal(f.docs[0].languageId,'groovy');assert.equal(f.confirms.length,1);assert.equal(f.confirms[0].u.ownerGeneration,'2');await f.coordinator.dispose();
});
