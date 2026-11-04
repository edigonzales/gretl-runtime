import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import {mkdir, readFile, rm, writeFile} from 'node:fs/promises';
import path from 'node:path';

const product='/Users/stefan/.cache/gretl-tooling-review/P7-review-1/product';
const evidence='/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence';
const jar='/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence/artifacts/gretl-lsp/target/gretl-lsp-6.0.0-SNAPSHOT-jar-with-dependencies.jar';
const runDir=path.join(evidence,'counterprobes','transitive-binding');
const sourcePath=path.join(runDir,'job.groovy');
const source=[
  "task('producer', HttpDownload) {",
  "    url = 'https://example.invalid/a.zip'",
  "    output = workPath('archive.zip')",
  "}",
  "action('bridge', dependsOn: 'producer') { println 'not executed' }",
  "task('consumer', Extract, after: 'producer', dependsOn: [ 'bridge']) {",
  "    archive = workPath('archive.zip')",
  "    compression = 'ZIP'",
  "    outputDirectory = workPath('out')",
  "}",
  "defaults 'consumer'",
  ""
].join('\n');

await rm(runDir,{recursive:true,force:true});
await mkdir(runDir,{recursive:true});
await writeFile(sourcePath,source);

const sha256=bytes=>createHash('sha256').update(bytes).digest('hex');
const jarHash=sha256(await readFile(jar));
const failures=[];
const observations={};
function check(condition,message){if(!condition)failures.push(message);}

const processHandle=spawn('/usr/bin/java',['-jar',jar,'--stdio'],{cwd:runDir,stdio:['pipe','pipe','pipe']});
let stderr='';
processHandle.stderr.setEncoding('utf8');
processHandle.stderr.on('data',chunk=>{stderr+=chunk;});
let pending=new Map(),nextId=0,incomingNotifications=[];
let buffer=Buffer.alloc(0);
function encode(message){
  const body=Buffer.from(JSON.stringify(message),'utf8');
  return Buffer.concat([Buffer.from(`Content-Length: ${body.length}\r\n\r\n`,'ascii'),body]);
}
function send(method,params,id=null){
  processHandle.stdin.write(encode({jsonrpc:'2.0',method,...(id===null?{}:{id}),params}));
}
function readFrame(){
  while(true){
    const marker=buffer.indexOf('\r\n\r\n');
    if(marker<0)return null;
    const header=buffer.subarray(0,marker).toString('ascii');
    const match=/Content-Length:\s*(\d+)/i.exec(header);
    if(!match)throw new Error('invalid LSP header '+JSON.stringify(header));
    const length=Number(match[1]);
    if(buffer.length<marker+4+length)return null;
    const body=buffer.subarray(marker+4,marker+4+length).toString('utf8');
    buffer=buffer.subarray(marker+4+length);
    return JSON.parse(body);
  }
}
processHandle.stdout.on('data',chunk=>{
  buffer=Buffer.concat([buffer,chunk]);
  for(;;){
    let message;
    try{message=readFrame();}catch(error){failures.push('read: '+error.message);return;}
    if(!message)break;
    if(Object.hasOwn(message,'id')&&pending.has(message.id)){
      const {resolve}=pending.get(message.id);
      pending.delete(message.id);
      resolve(message);
    }else if(message.method){
      incomingNotifications.push(message);
    }
  }
});
function call(method,params,timeoutMs=30000){
  const id=++nextId;
  return new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{pending.delete(id);reject(new Error(`timeout ${method}`));},timeoutMs);
    pending.set(id,{resolve:value=>{clearTimeout(timer);resolve(value);}});
    send(method,params,id);
  });
}
async function waitExit(timeoutMs=8000){
  if(processHandle.exitCode!==null)return processHandle.exitCode;
  return await new Promise(resolve=>{
    const timer=setTimeout(()=>resolve(null),timeoutMs);
    processHandle.once('exit',code=>{clearTimeout(timer);resolve(code);});
  });
}
function applyTextEdits(text,edits){
  const lines=text.split('\n');
  const offsets=[];let offset=0;
  for(const line of lines){offsets.push(offset);offset+=line.length+1;}
  const abs=(position)=>offsets[position.line]+position.character;
  const ordered=[...edits].sort((a,b)=>abs(b.range.start)-abs(a.range.start));
  let result=text;
  for(const edit of ordered){
    const start=abs(edit.range.start),end=abs(edit.range.end);
    if(!(start<=end)||end>result.length)throw new Error('invalid edit range');
    result=result.slice(0,start)+edit.newText+result.slice(end);
  }
  return result;
}
async function main(){
  check(jarHash==='f6acda578e81d806da9a016bdfdad00c67ee35a2640bfee21daa4845f63d0465','server artifact digest differs from candidate artifact');
  const uri=new URL('file://'+sourcePath).href;
  const init=await call('initialize',{rootUri:new URL('file://'+runDir+'/').href,locale:'en',capabilities:{workspace:{workspaceEdit:{documentChanges:true}}},initializationOptions:{trusted:true}});
  check(!init.error,'initialize failed '+JSON.stringify(init.error));
  send('initialized',{});
  send('textDocument/didOpen',{textDocument:{uri,languageId:'gretl-runtime',version:1,text:source}});
  const graph=await call('gretl/graph',{protocolVersion:1,documentId:uri});
  check(!graph.error,'graph failed '+JSON.stringify(graph.error));
  const graphResult=graph.result;
  const snapshot=graphResult?.context?.snapshotId;
  observations.snapshotId=snapshot;
  observations.initialNodes=graphResult?.payload?.nodes?.map(n=>({title:n.title,kind:n.kind}));
  observations.initialEdges=graphResult?.payload?.edges?.map(e=>({kind:e.kind,source:e.source,target:e.target}));
  check(typeof snapshot==='string'&&snapshot.length>0,'missing snapshot id');
  const planned=await call('gretl/planEdit',{
    protocolVersion:1,
    documentId:uri,
    expectedSnapshotId:snapshot,
    requireOpenDocuments:true,
    intent:{
      kind:'BindResult',
      producer:{namespace:'TASK',qualifiedName:'producer'},
      outputField:'file',
      target:{namespace:'TASK',qualifiedName:'consumer'},
      field:'archive'
    }
  });
  check(!planned.error,'planEdit failed '+JSON.stringify(planned.error));
  const response=planned.result;
  observations.capability=response?.capability;
  observations.planId=response?.planId;
  check(response?.capability?.state==='ALLOWED','binding not ALLOWED: '+JSON.stringify(response?.capability));
  const documentChanges=response?.edit?.documentChanges??[];
  check(documentChanges.length===1,'expected one source document change, got '+documentChanges.length);
  const change=documentChanges[0];
  check(new URL(change?.textDocument?.uri).href===uri,'unexpected changed document '+JSON.stringify(change?.textDocument));
  check(change?.textDocument?.version===1,'unexpected document version '+JSON.stringify(change?.textDocument));
  const applied=applyTextEdits(source,change?.edits??[]);
  observations.edits=change?.edits;
  observations.applied=applied;
  await writeFile(path.join(runDir,'planned.groovy'),applied);

  check(applied.includes("archive = result('producer').file"),'result binding missing');
  check(!/^archive = workPath/m.test(applied),'old archive placeholder retained');
  const consumerHeader=(applied.split('\n').find(line=>line.startsWith("task('consumer'"))??'');
  observations.consumerHeader=consumerHeader;
  check(consumerHeader.includes("after: 'producer'"),'after edge was not preserved');
  check(consumerHeader.includes("dependsOn: [ 'bridge']"),'transitive bridge dependency was not preserved verbatim');
  check(!/dependsOn\s*:\s*\[[^\]]*'producer'/.test(consumerHeader),'redundant direct producer dependency was added to dependsOn list');
  check(!/dependsOn\s*:\s*'producer'/.test(consumerHeader),'redundant scalar producer dependency was added');
  check((applied.match(/result\('producer'\)/g)??[]).length===1,'expected exactly one result reference');
  check((applied.match(/println 'not executed'/g)??[]).length===1,'opaque action body changed or duplicated');
  check(source.startsWith(applied.slice(0,source.indexOf("task('consumer'"))),'prefix before consumer changed unexpectedly');

  send('textDocument/didChange',{textDocument:{uri,version:2},contentChanges:[{text:applied}]});
  const post=await call('gretl/graph',{protocolVersion:1,documentId:uri});
  check(!post.error,'post-plan graph failed '+JSON.stringify(post.error));
  const postResult=post.result;
  const nodes=postResult?.payload?.nodes??[];
  const edges=postResult?.payload?.edges??[];
  const byTitle=new Map(nodes.map(n=>[n.title,n]));
  const producer=byTitle.get('producer'),consumer=byTitle.get('consumer');
  check(Boolean(producer&&consumer),'post-plan graph missing producer/consumer');
  const resultEdges=edges.filter(e=>e.kind==='RESULT_BINDING'&&e.source===producer?.id&&e.target===consumer?.id);
  const directDependencyEdges=edges.filter(e=>e.kind==='DEPENDS_ON'&&e.source===producer?.id&&e.target===consumer?.id);
  const directAfterEdges=edges.filter(e=>e.kind==='AFTER'&&e.source===producer?.id&&e.target===consumer?.id);
  observations.postEdges=edges.map(e=>({kind:e.kind,source:e.source,target:e.target,outputField:e.outputField,inputField:e.inputField}));
  observations.resultEdgeCount=resultEdges.length;
  observations.directDependencyCount=directDependencyEdges.length;
  observations.directAfterCount=directAfterEdges.length;
  check(resultEdges.length===1&&resultEdges[0].outputField==='file'&&resultEdges[0].inputField==='archive','result edge missing or wrong ports');
  check(directDependencyEdges.length===0,'post-plan graph contains redundant direct producer->consumer dependency');
  check(directAfterEdges.length===1,'pre-existing after edge was not preserved');

  await call('shutdown',{});
  send('exit',{});
  const exitCode=await waitExit();
  observations.exitCode=exitCode;
  check(exitCode===0,'server exit code '+exitCode);

  const report={
    kind:'NEW_NON_GUI_COUNTERPROBE',
    generatedAt:new Date().toISOString(),
    candidateSha:'53f7f3daaf92d002fd75978c25471321fd99a310',
    serverJar:jar,
    serverJarSha256:jarHash,
    sourceSha256Before:sha256(Buffer.from(source)),
    sourceSha256After:sha256(Buffer.from(applied)),
    passed:failures.length===0,
    failures,
    observations,
    stderr
  };
  await writeFile(path.join(evidence,'counterprobe-transitive-binding-result.json'),JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify({passed:report.passed,failures,result:path.join(evidence,'counterprobe-transitive-binding-result.json')},null,2));
  if(failures.length)process.exitCode=1;
}

try{
  await main();
}catch(error){
  failures.push(String(error?.stack??error));
  try{processHandle.kill('SIGKILL');}catch{}
  const report={
    kind:'NEW_NON_GUI_COUNTERPROBE',
    generatedAt:new Date().toISOString(),
    candidateSha:'53f7f3daaf92d002fd75978c25471321fd99a310',
    serverJar:jar,
    serverJarSha256:jarHash,
    passed:false,
    failures,
    observations,
    stderr
  };
  await writeFile(path.join(evidence,'counterprobe-transitive-binding-result.json'),JSON.stringify(report,null,2)+'\n');
  console.error(JSON.stringify(report,null,2));
  process.exitCode=1;
}
