import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {mkdtemp,rm,writeFile,symlink,realpath} from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import {pathToFileURL} from 'node:url';
const directory=await mkdtemp(path.join(await realpath(os.tmpdir()),'gretl-edit-guard-'));
after(()=>rm(directory,{recursive:true,force:true}));
await build({entryPoints:['src/edit-guard.ts'],bundle:true,platform:'node',format:'esm',outfile:path.join(directory,'guard.mjs')});
await build({entryPoints:['src/host.ts'],bundle:true,platform:'node',format:'esm',outfile:path.join(directory,'host.mjs')});
const {validateEditSources:validate,diskSourceHash,validateEditContext}=await import(pathToFileURL(path.join(directory,'guard.mjs')));
const {sourceDigest}=await import(pathToFileURL(path.join(directory,'host.mjs')));
const uri=pathToFileURL(path.join(directory,'job.groovy')).toString();
const doc=()=>({version:4,isClosed:false,getText:()=>"action('a') {}\n"});
const stamp=(documentId=uri)=>({documentId,sha256:sourceDigest(doc().getText()),version:4,incarnation:'capture-id',editable:true,unavailableCode:null});
test('current version and buffer identity permit a native edit',async()=>{
 const document=doc(),buffers=new Map([[uri,document]]);
 await validate([stamp()],[{uri,version:4}],buffers,()=>buffers);
});
test('version changes, closed/reopened same-version buffers and read-only edits fail',async()=>{
 const document=doc(),captured=new Map([[uri,document]]),buffers=new Map(captured);
 document.version++;await assert.rejects(validate([stamp()],[{uri,version:4}],captured,()=>buffers),/buffer changed/);
 buffers.set(uri,doc());await assert.rejects(validate([stamp()],[{uri,version:4}],captured,()=>buffers),/buffer changed/);
 await assert.rejects(validate([{...stamp(),editable:false}],[{uri,version:4}],captured,()=>captured),/precondition/);
 await assert.rejects(validate([stamp()],[{uri,version:null}],captured,()=>captured),/precondition/);
});
test('closed/missing sources are checked, including dependent source hashes',async()=>{
 const first={...stamp(),version:null};
 await validate([first],[{uri,version:null}],new Map(),()=>new Map(),async()=>first.sha256);
 await assert.rejects(validate([first],[{uri,version:null}],new Map(),()=>new Map(),async()=>null),/file changed/);
 const dependent={...first,documentId:uri+'-settings',sha256:null,editable:false};
 await assert.rejects(validate([first,dependent],[{uri,version:null}],new Map(),()=>new Map(),async id=>id===uri?first.sha256:'new file'),/file changed/);
});
test('a source opening while disk validation awaits is rejected',async()=>{
 const first={...stamp(),version:null},buffers=new Map();
 await assert.rejects(validate([first],[{uri,version:null}],new Map(),()=>buffers,async()=>{buffers.set(uri,doc());return first.sha256;}),/opened after capture/);
});
test('buffer mutation while another source is hashed is rejected',async()=>{
 const document=doc(),buffers=new Map([[uri,document]]),second={...stamp(uri+'-settings'),version:null};
 await assert.rejects(validate([stamp(),second],[{uri,version:4}],buffers,()=>buffers,async()=>{document.version++;return second.sha256;}),/buffer changed/);
});
test('disk guard preserves BOM/hash format, rejects symlinks and over-limit files',async()=>{
 const filename=path.join(directory,'closed.groovy'),url=pathToFileURL(filename).toString();
 const source="\uFEFF// 😀\r\naction('a') {}\r\n";
 await writeFile(filename,source);assert.equal(await diskSourceHash(url),sourceDigest(source));
 const link=path.join(directory,'link.groovy');await symlink(filename,link);await assert.rejects(diskSourceHash(pathToFileURL(link).toString()),/symlink/);
 await writeFile(filename,Buffer.alloc(4*1024*1024+1));await assert.rejects(diskSourceHash(url),/byte limit/);
 await assert.rejects(diskSourceHash(pathToFileURL(directory).toString()),/regular file/);
 assert.equal(await diskSourceHash(pathToFileURL(path.join(directory,'absent')).toString()),null);
});

test('unchanged source bytes cannot excuse a new owner, selected context or stale context token',()=>{
 const before={protocolVersion:1,schemaVersion:1,serverInstanceId:'server',snapshotId:'snapshot',ownerGeneration:'1',contextGeneration:'4',contextDocumentId:uri,contextToken:'token'};
 validateEditContext(before,{...before});
 for(const change of[{ownerGeneration:'2'},{contextGeneration:'5'},{contextDocumentId:uri+'x'},{contextToken:'new'},{serverInstanceId:'other'},{snapshotId:'new'}])assert.throws(()=>validateEditContext(before,{...before,...change}),/ownership or source context/);
});
