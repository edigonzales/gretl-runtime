import {test} from 'node:test';
import assert from 'node:assert/strict';
import {verifyWorkbenchEvidence} from '../scripts/verify-workbench-evidence.mjs';
const uri='file:///controlled/job.groovy';
function capture(text,version){return {documents:[{uri,text,version,dirty:version>1}],modelers:[{documentId:uri,version,status:'FRESH',context:{sourceStamps:[{documentId:uri,version}]},view:{nodes:[{id:'download'},{id:'extract'}],domNodes:['download','extract'].map(id=>({id,visibility:'visible',opacity:'1',rect:{width:244,height:112}}))}}]};}
test('controlled native observation can prove an edit and separately prove view-only invariance',()=>{
 const before=capture('before',1),after=capture('after',2);
 assert.equal(verifyWorkbenchEvidence(after,{uri,nodeCount:2,sourceChanged:true,before}).passed,true);
 assert.equal(verifyWorkbenchEvidence(before,{uri,nodeCount:2,before:structuredClone(before)}).passed,true);
 const parameterOnly=capture('parameter only',1);parameterOnly.modelers[0].view.nodes=[];parameterOnly.modelers[0].view.domNodes=[];
 assert.equal(verifyWorkbenchEvidence(parameterOnly,{uri,nodeCount:0,status:'FRESH'}).passed,true);
});
test('canary: an omitted expected graph node fails the same workbench oracle',()=>{
 const after=capture('after',2);after.modelers[0].view.nodes.pop();
 assert.throws(()=>verifyWorkbenchEvidence(after,{uri,nodeCount:2}),/EVIDENCE_EXPECTED_NODE/);
});
test('canary: suppressing the confirmed text mutation cannot become a passed GUI edit',()=>{
 const before=capture('before',1),after=capture('before',2);
 assert.throws(()=>verifyWorkbenchEvidence(after,{uri,nodeCount:2,sourceChanged:true,before}),/EVIDENCE_TEXT_CHANGE/);
 after.documents[0].version=1;after.modelers[0].version=1;after.modelers[0].context.sourceStamps[0].version=1;
 assert.throws(()=>verifyWorkbenchEvidence(after,{uri,nodeCount:2,sourceChanged:true,before}),/EVIDENCE_TEXT_VERSION/);
});
test('canary: foreign panels, invisible cards and a forged source digest are rejected',()=>{
 const after=capture('after',2);after.modelers[0].documentId='file:///other/job.groovy';
 assert.throws(()=>verifyWorkbenchEvidence(after,{uri,nodeCount:2}),/EVIDENCE_PINNED_PANEL/);
 after.modelers[0].documentId=uri;after.modelers[0].view.domNodes[0].opacity='0';
 assert.throws(()=>verifyWorkbenchEvidence(after,{uri,nodeCount:2}),/EVIDENCE_RENDERED_NODE/);
 after.documents[0].sha256='0'.repeat(64);
 assert.throws(()=>verifyWorkbenchEvidence(after,{uri,nodeCount:2}),/EVIDENCE_SOURCE_HASH/);
 const stale=capture('after',2);stale.modelers[0].context.sourceStamps[0].version=1;
 assert.throws(()=>verifyWorkbenchEvidence(stale,{uri,nodeCount:2}),/EVIDENCE_STALE_SNAPSHOT/);
});
