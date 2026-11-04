import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile,writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';

const digest=text=>createHash('sha256').update(text,'utf8').digest('hex');
// SourceText hashes original UTF-16 code units in big-endian order, including lone surrogates.
const snapshotDigest=text=>{const bytes=Buffer.alloc(text.length*2);for(let i=0;i<text.length;i++)bytes.writeUInt16BE(text.charCodeAt(i),i*2);return createHash('sha256').update(bytes).digest('hex');};
const identity=uri=>new URL(uri).href;
function document(capture,uri){
 const matches=capture.documents.filter(d=>identity(d.uri)===identity(uri));
 assert.equal(matches.length,1,'EVIDENCE_DOCUMENT');const doc=matches[0];
 assert.equal(typeof doc.text,'string');assert(Number.isSafeInteger(doc.version)&&doc.version>0);
 if(doc.sha256)assert.equal(doc.sha256,digest(doc.text),'EVIDENCE_SOURCE_HASH');
 return doc;
}
/** Checks read-only driver observations against a separately authored fixture expectation.
 * This is a test oracle, not a second DSL parser or a substitute for native images. */
export function verifyWorkbenchEvidence(after,{uri,nodeCount,status=nodeCount===0?'EMPTY':'FRESH',sourceChanged=false,before,textSha256}){
 assert(Array.isArray(after.documents)&&Array.isArray(after.modelers),'EVIDENCE_CAPTURE');
 const doc=document(after,uri),panels=after.modelers.filter(m=>m.documentId&&identity(m.documentId)===identity(uri));
 assert.equal(panels.length,1,'EVIDENCE_PINNED_PANEL');const panel=panels[0];
 assert.equal(panel.status,status,'EVIDENCE_GRAPH_STATE');
 assert.equal(panel.version,doc.version,'EVIDENCE_PANEL_VERSION');
 const stamp=panel.context.sourceStamps.find(s=>identity(s.documentId)===identity(uri));
 assert(stamp&&stamp.version===doc.version,'EVIDENCE_STALE_SNAPSHOT');
 if(stamp.sha256)assert.equal(stamp.sha256,snapshotDigest(doc.text),'EVIDENCE_SNAPSHOT_SOURCE');
 const nodes=panel.view.nodes.filter(n=>!n.hidden),ids=new Set(nodes.map(n=>n.id));
 assert.equal(ids.size,nodes.length,'EVIDENCE_DUPLICATE_NODE');
 assert.equal(nodes.length,nodeCount,'EVIDENCE_EXPECTED_NODE');
 for(const node of nodes){
  const dom=panel.view.domNodes.find(n=>n.id===node.id);
  assert(dom&&dom.visibility!=='hidden'&&Number(dom.opacity)>0&&dom.rect.width>0&&dom.rect.height>0,'EVIDENCE_RENDERED_NODE');
 }
 if(textSha256)assert.equal(digest(doc.text),textSha256,'EVIDENCE_EXPECTED_SOURCE');
 if(before){const old=document(before,uri);
  if(sourceChanged){assert(doc.version>old.version,'EVIDENCE_TEXT_VERSION');assert.notEqual(digest(doc.text),digest(old.text),'EVIDENCE_TEXT_CHANGE');}
  else {assert.equal(doc.version,old.version,'EVIDENCE_VIEW_CHANGED_TEXT_VERSION');assert.equal(digest(doc.text),digest(old.text),'EVIDENCE_VIEW_CHANGED_TEXT');}
 }else assert(!sourceChanged,'EVIDENCE_BEFORE_REQUIRED');
 return {passed:true,uri,version:doc.version,dirty:doc.dirty,nodeCount,sourceSha256:digest(doc.text),sourceChanged};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
 const [expectationFile,afterFile,outputFile]=process.argv.slice(2);
 assert(expectationFile&&afterFile&&outputFile,'Usage: node scripts/verify-workbench-evidence.mjs expectation.json after.json output.json');
 const expectation=JSON.parse(await readFile(expectationFile,'utf8'));
 if(expectation.beforeFile)expectation.before=JSON.parse(await readFile(expectation.beforeFile,'utf8'));
 const result=verifyWorkbenchEvidence(JSON.parse(await readFile(afterFile,'utf8')),expectation);
 await writeFile(outputFile,JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
}
