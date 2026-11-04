import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {readFile, writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';

const evidence='/Users/stefan/.cache/gretl-tooling-review/P6-review-1/evidence';
const jar=`${evidence}/artifacts/gretl-lsp/target/gretl-lsp-6.0.0-SNAPSHOT-jar-with-dependencies.jar`;
const fixture='/Users/stefan/.cache/gretl-tooling-review/P6-candidate-1-gui/workspace ä space';
const mainPath=`${fixture}/project-main`;
const otherPath=`${fixture}/project-other`;
const rootUri=pathToFileURL(mainPath).toString();
const otherRootUri=pathToFileURL(otherPath).toString();
const mainDoc=pathToFileURL(`${mainPath}/job.groovy`).toString();
const otherDoc=pathToFileURL(`${otherPath}/job.groovy`).toString();

const assertions=[];
const check=(name,ok,detail)=>{assertions.push({name,ok:!!ok,detail});};

const jarBytes=await readFile(jar);
const jarSha=createHash('sha256').update(jarBytes).digest('hex');
check('jar-sha256-matches-supplied-artifact',jarSha==='5189670a5c28dbf86b37c9115d5514b4e2fdffb7f4e1c977e19b8512db8da80c',jarSha);

const mainText=await readFile(`${mainPath}/job.groovy`,'utf8');
const otherText=await readFile(`${otherPath}/job.groovy`,'utf8');

const child=spawn('java',['-jar',jar,'--stdio'],{stdio:['pipe','pipe','pipe']});
let stderr='';
child.stderr.on('data',d=>{stderr+=d.toString();});

let buf=Buffer.alloc(0);
const pending=new Map();
let nextId=1;
const send=(message)=>{
  const body=Buffer.from(JSON.stringify(message),'utf8');
  child.stdin.write(`Content-Length: ${body.length}\r\n\r\n`);
  child.stdin.write(body);
};
const request=(method,params)=>new Promise((resolve,reject)=>{
  const id=nextId++;
  const timer=setTimeout(()=>{pending.delete(id);reject(new Error(`timeout ${method}`));},120000);
  pending.set(id,{resolve,reject,timer});
  send({jsonrpc:'2.0',id,method,params});
});
const notify=(method,params)=>send({jsonrpc:'2.0',method,params});

child.stdout.on('data',chunk=>{
  buf=Buffer.concat([buf,chunk]);
  for(;;){
    const sep=buf.indexOf('\r\n\r\n');
    if(sep<0)return;
    const header=buf.slice(0,sep).toString('ascii');
    const m=/Content-Length:\s*(\d+)/i.exec(header);
    if(!m)throw new Error('bad header '+header);
    const length=Number(m[1]);
    if(buf.length<sep+4+length)return;
    const body=buf.slice(sep+4,sep+4+length).toString('utf8');
    buf=buf.slice(sep+4+length);
    let message;
    try{message=JSON.parse(body);}catch(e){throw new Error('bad json '+e.message);}
    if(message.id!==undefined&&message.method===undefined){
      const entry=pending.get(message.id);
      if(entry){pending.delete(message.id);clearTimeout(entry.timer);entry.resolve(message);}
    }else if(message.id!==undefined&&message.method!==undefined){
      // server->client request: answer harmlessly
      send({jsonrpc:'2.0',id:message.id,result:null});
    }
    // notifications ignored
  }
});

const closeServer=async()=>{
  try{await Promise.race([request('shutdown',null),new Promise(r=>setTimeout(r,3000))]);}catch{}
  try{notify('exit',null);}catch{}
  await new Promise(r=>setTimeout(r,300));
  child.kill('SIGTERM');
  await new Promise(r=>setTimeout(r,300));
  if(child.exitCode===null)child.kill('SIGKILL');
};

const result={jar:{path:jar,sha256:jarSha},rootUri,otherRootUri,mainDoc,otherDoc};

try{
  const init=await request('initialize',{
    processId:process.pid,
    clientInfo:{name:'independent-p6-review-probe',version:'1.0'},
    locale:'en',
    rootUri,
    workspaceFolders:[{uri:rootUri,name:'project-main'},{uri:otherRootUri,name:'project-other'}],
    capabilities:{},
    initializationOptions:{trusted:true,language:'en'}
  });
  check('initialize-ok',!init.error,init.error);
  notify('initialized',{});
  await new Promise(r=>setTimeout(r,500));

  // Open the Main buffer explicitly (matches disk text at version 1).
  notify('textDocument/didOpen',{textDocument:{uri:mainDoc,languageId:'gretl-runtime',version:1,text:mainText}});
  await new Promise(r=>setTimeout(r,300));

  const downloadOffset=mainText.indexOf("'download'")+1;
  check('main-download-offset-nonnegative',downloadOffset>0,downloadOffset);

  const gMain=await request('gretl/graph',{
    protocolVersion:1,documentId:mainDoc,expectedSnapshotId:null,
    includeInnerInvocations:true,requestedSources:[],previewTargets:[':done']
  });
  if(gMain.error){throw new Error('graph main failed '+JSON.stringify(gMain.error));}
  const mainResp=gMain.result;
  const mainNodes=mainResp.payload.nodes;
  const mainDownload=mainNodes.filter(n=>n.kind==='TASK'&&n.symbol&&String(n.symbol.qualifiedName).includes('download'));
  const nodeIds=new Set(mainNodes.map(n=>n.id));
  result.mainGraph={
    status:mainResp.payload.status,
    snapshotId:mainResp.context.snapshotId,
    nodeCount:mainNodes.length,
    edgeCount:mainResp.payload.edges.length,
    downloadTaskIds:mainDownload.map(n=>n.id),
    downloadTaskSymbols:mainDownload.map(n=>n.symbol),
    nodeSummary:mainNodes.map(n=>({id:n.id,kind:n.kind,title:n.title,symbol:n.symbol})),
    preview:mainResp.payload.preview
  };
  check('main-graph-status-complete',mainResp.payload.status==='COMPLETE',mainResp.payload.status);
  check('main-has-single-task-download',mainDownload.length===1,mainDownload.length);
  check('main-preview-available',mainResp.payload.preview&&mainResp.payload.preview.available===true,mainResp.payload.preview);
  const ordered=mainResp.payload.preview?mainResp.payload.preview.orderedSymbols:[];
  const nodeQualified=new Set(mainNodes.filter(n=>n.symbol).map(n=>n.symbol.qualifiedName));
  check('main-preview-ordered-nonempty',ordered.length>0,ordered.length);
  check('main-preview-ordered-subset-of-nodes',ordered.every(q=>nodeQualified.has(q)),ordered);

  // An unresolved/unqualified target must be reported honestly, never fabricated.
  const gUnknown=await request('gretl/graph',{
    protocolVersion:1,documentId:mainDoc,expectedSnapshotId:mainResp.context.snapshotId,
    includeInnerInvocations:true,requestedSources:[],previewTargets:['done']
  });
  const unknownPreview=gUnknown.error?null:gUnknown.result.payload.preview;
  result.unknownTargetPreview={error:gUnknown.error??null,preview:unknownPreview};
  check('unknown-target-preview-not-fabricated',!gUnknown.error&&unknownPreview&&unknownPreview.available===false&&unknownPreview.reason==='UNKNOWN_TARGET'&&unknownPreview.orderedSymbols.length===0,unknownPreview);

  const nMain=await request('gretl/navigation',{
    protocolVersion:1,documentId:mainDoc,expectedSnapshotId:mainResp.context.snapshotId,
    elementId:null,offsetUtf16:downloadOffset
  });
  const mainElements=nMain.error?[]:(nMain.result.elements||[]);
  result.mainNavigate={error:nMain.error??null,elements:mainElements,location:nMain.error?null:nMain.result.location};
  check('main-navigate-offset-selects-task-download',!nMain.error&&mainDownload.length===1&&mainElements.includes(mainDownload[0].id),mainElements);

  // Other project has an ACTION named download (same name, different source/kind).
  const gOther=await request('gretl/graph',{
    protocolVersion:1,documentId:otherDoc,expectedSnapshotId:null,
    includeInnerInvocations:true,requestedSources:[],previewTargets:null
  });
  if(gOther.error){throw new Error('graph other failed '+JSON.stringify(gOther.error));}
  const otherResp=gOther.result;
  const otherNodes=otherResp.payload.nodes;
  const otherDownload=otherNodes.filter(n=>n.kind==='ACTION'&&n.symbol&&String(n.symbol.qualifiedName).includes('download'));
  result.otherGraph={
    status:otherResp.payload.status,
    snapshotId:otherResp.context.snapshotId,
    nodeCount:otherNodes.length,
    downloadActionIds:otherDownload.map(n=>n.id),
    downloadActionSymbols:otherDownload.map(n=>n.symbol),
    nodeSummary:otherNodes.map(n=>({id:n.id,kind:n.kind,title:n.title,symbol:n.symbol}))
  };
  check('other-graph-status-complete',otherResp.payload.status==='COMPLETE',otherResp.payload.status);
  check('other-has-single-action-download',otherDownload.length===1,otherDownload.length);
  check('same-name-different-kinds',mainDownload.length===1&&otherDownload.length===1&&mainDownload[0].kind==='TASK'&&otherDownload[0].kind==='ACTION',{main:mainDownload[0]?.kind,other:otherDownload[0]?.kind});
  check('same-name-different-node-id',mainDownload.length===1&&otherDownload.length===1&&mainDownload[0].id!==otherDownload[0].id,{main:mainDownload[0]?.id,other:otherDownload[0]?.id});

  const otherOffset=otherText.indexOf("'download'")+1;
  check('other-download-offset-nonnegative',otherOffset>0,otherOffset);
  const nOther=await request('gretl/navigation',{
    protocolVersion:1,documentId:otherDoc,expectedSnapshotId:otherResp.context.snapshotId,
    elementId:null,offsetUtf16:otherOffset
  });
  const otherElements=nOther.error?[]:(nOther.result.elements||[]);
  result.otherNavigate={error:nOther.error??null,elements:otherElements,location:nOther.error?null:nOther.result.location};
  check('other-navigate-offset-selects-action-download',!nOther.error&&otherDownload.length===1&&otherElements.includes(otherDownload[0].id),otherElements);
  check('other-navigate-does-not-return-main-node',mainDownload.length===1&&!otherElements.includes(mainDownload[0].id),otherElements);

  // Stale identity: change the Main buffer, then reuse the pre-change snapshot id.
  const staleSnapshot=mainResp.context.snapshotId;
  const edited=mainText.replace('// Preserve this comment during rename and quick fixes. 😀','// Independent stale-identity probe edit 😀');
  check('stale-edit-changed-text',edited!==mainText,'replaced comment');
  notify('textDocument/didChange',{textDocument:{uri:mainDoc,version:2},contentChanges:[{text:edited}]});
  await new Promise(r=>setTimeout(r,800));
  const stale=await request('gretl/navigation',{
    protocolVersion:1,documentId:mainDoc,expectedSnapshotId:staleSnapshot,
    elementId:null,offsetUtf16:downloadOffset
  });
  result.staleNavigate={error:stale.error??null,hasResult:!!stale.result};
  const errCode=stale.error&&stale.error.code;
  const errMessage=stale.error&&stale.error.message;
  check('stale-navigate-rejected',!!stale.error,{code:errCode,message:errMessage});
  check('stale-navigate-content-modified',errCode===-32801,{code:errCode});
  check('stale-navigate-snapshot-mismatch',errMessage==='SNAPSHOT_MISMATCH',errMessage);

  result.assertions=assertions;
  result.pass=assertions.every(a=>a.ok);
  result.stderrTail=stderr.slice(-2000);
}catch(error){
  result.assertions=assertions;
  result.pass=false;
  result.error=error.message;
  result.stderrTail=stderr.slice(-2000);
}finally{
  await closeServer();
}
await writeFile(`${evidence}/counterprobe-lsp.json`,JSON.stringify(result,null,2));
console.log(JSON.stringify({pass:result.pass,assertions:result.assertions,error:result.error},null,2));
