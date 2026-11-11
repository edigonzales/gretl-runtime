#!/usr/bin/env node
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {pathToFileURL,fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {spawn,spawnSync} from 'node:child_process';

const repo=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const probeScriptSha256=createHash('sha256').update(fs.readFileSync(fileURLToPath(import.meta.url))).digest('hex');
const options={};for(let i=2;i<process.argv.length;i+=2){assert(process.argv[i].startsWith('--')&&process.argv[i+1],'Named option/value pairs required');options[process.argv[i].slice(2)]=process.argv[i+1];}
assert(options.jar&&options.evidence,'Usage: node tools/gretl-tooling/qualify-server.mjs --jar /absolute/server/gretl-lsp.jar --evidence /fresh/directory [--docker-image sha256:...] [--candidate SHA]');
const jar=path.resolve(options.jar),evidence=path.resolve(options.evidence),docker=options['docker-image'];
assert(!fs.existsSync(evidence),'Fresh evidence directory required');fs.mkdirSync(evidence,{recursive:true,mode:0o700});
const workspace=evidence+'/workspace';fs.mkdirSync(workspace,{mode:0o755});
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
function command(program,args){const r=spawnSync(program,args,{encoding:'utf8',timeout:60000,maxBuffer:8<<20});assert.equal(r.status,0,r.stderr||String(r.error));return r.stdout+r.stderr;}
const bundle=JSON.parse(fs.readFileSync(path.dirname(jar)+'/bundle-manifest.json'));
assert.equal(hash(fs.readFileSync(jar)),bundle.files['gretl-lsp.jar']);
const head=command('git',['-C',repo,'rev-parse','HEAD']).trim(),dirty=command('git',['-C',repo,'status','--porcelain']).trim()!=='';
if(options.candidate){assert.equal(bundle.sourceCommit,options.candidate);assert.equal(head,options.candidate);assert.equal(dirty,false);assert.equal(bundle.sourceDirty,false);}
const container='gretl-lsp-qualification-'+process.pid+'-'+Date.now(),marker=docker?'/tmp/gretl-qualification-must-not-execute':workspace+'/must-not-execute';
const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin/java'):'java';
const image=docker?JSON.parse(command('docker',['image','inspect',docker]))[0]:null;
if(image)assert.equal(image.Os,'linux');
const runtimeImage=image?.Id;
const javaVersion=docker?command('docker',['run','--rm','--network','none','--entrypoint','java',runtimeImage,'-version']):command(java,['-version']);
assert.match(javaVersion,/version "25(?:[.\"])/,'An actual Java 25 runtime is required');
const rootUri=docker?'file:///workspace/':pathToFileURL(workspace+path.sep).href;
const argv=docker?['docker','run','--rm','-i','--name',container,'--network','none','--read-only','--tmpfs','/tmp:rw,noexec,nosuid,size=64m,mode=1777','--memory','768m','--cpus','2','--pids-limit','128','--cap-drop','ALL','--security-opt','no-new-privileges','--user','1000:1000','--mount','type=bind,src='+jar+',dst=/opt/gretl/gretl-lsp.jar,readonly','--mount','type=bind,src='+workspace+',dst=/workspace,readonly','--entrypoint','java',runtimeImage,'-Xmx256m','-jar','/opt/gretl/gretl-lsp.jar','--stdio']:[java,'-Xmx256m','-jar',jar,'--stdio'];
const startedAt=new Date().toISOString(),child=spawn(argv[0],argv.slice(1),{cwd:evidence,stdio:['pipe','pipe','pipe']});
let buffer=Buffer.alloc(0),sequence=0,stderr='',protocolFailure,initialized=false,exitFacts;
const pending=new Map(),notifications=[],rpc=[],measurements=[];
const ended=new Promise(resolve=>child.once('close',(code,signal)=>{exitFacts={code,signal};for(const p of pending.values()){clearTimeout(p.timer);p.reject(Error('SERVER_EXITED'));}pending.clear();resolve(exitFacts);}));
function send(value){const body=Buffer.from(JSON.stringify({jsonrpc:'2.0',...value}));child.stdin.write(Buffer.concat([Buffer.from('Content-Length: '+body.length+'\r\n\r\n'),body]));}
function notify(method,params){send({method,params});}
function request(method,params){const id=++sequence,start=performance.now();return new Promise((resolve,reject)=>{
 const timer=setTimeout(()=>{pending.delete(id);reject(Error('RPC_TIMEOUT: '+method));},40000);
 pending.set(id,{timer,resolve:value=>{rpc.push({method,milliseconds:performance.now()-start});resolve(value);},reject:error=>{rpc.push({method,milliseconds:performance.now()-start,errorCode:error.code,message:error.message});reject(error);}});send({id,method,params});
});}
child.stdin.on('error',()=>{});child.stderr.on('data',chunk=>{stderr+=chunk;if(stderr.length>1<<20)stderr=stderr.slice(-(1<<20));});
child.stdout.on('data',chunk=>{try{
 buffer=Buffer.concat([buffer,chunk]);
 while(true){const boundary=buffer.indexOf('\r\n\r\n');if(boundary<0){assert(buffer.length<8192,'Invalid protocol header');break;}
  const header=buffer.subarray(0,boundary).toString(),match=header.match(/(?:^|\r\n)Content-Length: (\d+)/i);assert(match,'Non-protocol stdout');const length=Number(match[1]);assert(length<=16<<20,'Response frame budget exceeded');if(buffer.length<boundary+4+length)break;
  const message=JSON.parse(buffer.subarray(boundary+4,boundary+4+length).toString());buffer=buffer.subarray(boundary+4+length);
  if(message.method){if(message.id!==undefined)send({id:message.id,result:message.method==='workspace/configuration'?[]:null});else{notifications.push(message);if(notifications.length>300)notifications.shift();}}
  else {const p=pending.get(message.id);if(p){clearTimeout(p.timer);pending.delete(message.id);if(message.error){const error=Object.assign(Error(message.error.message),{code:message.error.code});p.reject(error);}else p.resolve(message.result);}}
 }
 }catch(error){protocolFailure=String(error);for(const p of pending.values()){clearTimeout(p.timer);p.reject(error);}pending.clear();child.kill('SIGTERM');}});
const uri=relative=>new URL(relative,rootUri).href;
async function graph(relative,version){
 let last;for(let attempt=0;attempt<30;attempt++)try{const value=await request('gretl/graph',{protocolVersion:1,documentId:uri(relative),expectedSnapshotId:null,includeInnerInvocations:false,requestedSources:[]});assert.equal(value.context.protocolVersion,1);if(version!==undefined&&version!==null&&value.context.sourceStamps.find(s=>new URL(s.documentId).href===uri(relative))?.version!==version)throw Object.assign(Error('CURRENT_VERSION_PENDING: '+JSON.stringify(value.context.sourceStamps.map(s=>({documentId:s.documentId,version:s.version})))),{code:-32801});return value;}
 catch(error){last=error;if(![-32801,-32800].includes(error.code))throw error;await new Promise(resolve=>setTimeout(resolve,50));}throw last;
}
async function open(relative,text){const file=path.join(workspace,relative);fs.mkdirSync(path.dirname(file),{recursive:true,mode:0o755});fs.writeFileSync(file,text);notify('textDocument/didOpen',{textDocument:{uri:uri(relative),languageId:'gretl-runtime',version:1,text}});}
async function measure(label,relative,check,version=1){const start=performance.now(),value=await graph(relative,version);check(value);measurements.push({label,milliseconds:performance.now()-start,nodeCount:value.payload.nodes.length,diagnostics:value.payload.diagnostics.length,snapshotId:value.context.snapshotId});return value;}
const memory=[];function sample(){if(docker)return;const p=spawnSync('ps',['-o','rss=','-p',String(child.pid)],{encoding:'utf8',timeout:2000});const rss=Number(p.stdout.trim());if(p.status===0&&rss>0)memory.push({time:new Date().toISOString(),rssKiB:rss});}
const sampler=setInterval(sample,500);let failure;
try{
 await request('initialize',{processId:docker?null:process.pid,clientInfo:{name:'GRETL distribution qualifier',version:'1'},initializationOptions:{trusted:true},rootUri,workspaceFolders:[{uri:rootUri,name:'controlled fixtures'}],capabilities:{workspace:{workspaceFolders:true},textDocument:{hover:{contentFormat:['markdown']},publishDiagnostics:{versionSupport:true}}}});initialized=true;notify('initialized',{});
 const small="// Controlled nonexecution probe. 😀\r\nnew File('"+marker.replaceAll('\\','\\\\').replaceAll("'","\\'")+"').text = 'MUST_NOT_EXECUTE'\r\n"+fs.readFileSync(repo+'/gretl-integration-tests/src/test/resources/jobs/modeler-created/job.groovy','utf8');
 const before=hash(Buffer.from(small));await open('job.groovy',small);
 await measure('small','job.groovy',g=>assert.equal(g.payload.nodes.filter(n=>n.kind==='TASK').length,2));
 const catalog=await request('gretl/catalog',{protocolVersion:1,catalogSchemaVersion:1});assert.equal(catalog.state,'AVAILABLE');assert.equal(catalog.catalog.catalogId,bundle.catalogId);assert.equal(catalog.catalog.operations.length,45);
 const offset=small.indexOf('HttpDownload')+3,lines=small.slice(0,offset).split('\n');const hover=await request('textDocument/hover',{textDocument:{uri:uri('job.groovy')},position:{line:lines.length-1,character:lines.at(-1).length}});assert(JSON.stringify(hover).includes('HttpDownload'));
 for(const [label,count]of[['medium',200],['large',1200]]){const text=Array.from({length:count},(_,i)=>"action('"+label+i+"') {}\n").join('');await open(label+'/job.groovy',text);await measure(label,label+'/job.groovy',g=>assert.equal(g.payload.nodes.filter(n=>n.kind==='ACTION').length,count));}
 await open('broken/job.groovy',"action('safe') {}\ntask('broken', HttpDownload) { url = \"unterminated ${\n");await measure('broken-recovery','broken/job.groovy',g=>{assert(g.payload.diagnostics.length>0);assert(g.payload.nodes.some(n=>n.title==='safe'));});
 await open('over-budget/job.groovy','x'.repeat(1_048_580));const oversized=await measure('source-budget','over-budget/job.groovy',g=>assert(/LIMIT/.test(JSON.stringify(g))),null);assert(oversized.context.completeness);
 await open('burst/job.groovy',"action('initial') {}\n");for(let version=2;version<=21;version++)notify('textDocument/didChange',{textDocument:{uri:uri('burst/job.groovy'),version},contentChanges:[{text:"action('latest"+version+"') {}\n"}]});
 await measure('twenty-rapid-edits','burst/job.groovy',g=>assert(g.payload.nodes.some(n=>n.title==='latest21')),21);
 for(let i=0;i<12;i++){const relative='retention-'+i+'/job.groovy';await open(relative,"action('retained"+i+"') {}\n");await measure('retention-'+i,relative,g=>assert.equal(g.payload.nodes.length,1));notify('textDocument/didClose',{textDocument:{uri:uri(relative)}});}
 notify('workspace/didChangeWorkspaceFolders',{event:{added:[],removed:[{uri:rootUri,name:'controlled fixtures'}]}});await assert.rejects(graph('job.groovy'),/WORKSPACE|ROOT|SOURCE|PROJECT/i);
 notify('workspace/didChangeWorkspaceFolders',{event:{added:[{uri:rootUri,name:'controlled fixtures'}],removed:[]}});await measure('root-readded','job.groovy',g=>assert.equal(g.payload.nodes.filter(n=>n.kind==='TASK').length,2));
 if(docker)command('docker',['exec',container,'sh','-c','test ! -e '+marker]);else assert(!fs.existsSync(marker),'Workspace code executed');
 assert.equal(hash(fs.readFileSync(workspace+'/job.groovy')),before,'Analysis changed source');sample();
 if(docker)memory.push({time:new Date().toISOString(),dockerStats:command('docker',['stats','--no-stream','--format','{{json .}}',container]).trim()});
 if(!docker){assert(memory.length>0);assert(Math.max(...memory.map(m=>m.rssKiB))<768*1024,'Sampled RSS exceeds 768 MiB');}
}catch(error){failure={message:error.message,stack:error.stack};}
finally{
 clearInterval(sampler);try{if(initialized&&!exitFacts){await request('shutdown',null);notify('exit',null);child.stdin.end();}}catch(error){failure??={message:String(error)};child.kill('SIGTERM');}
 let stopTimer;const timeout=new Promise(resolve=>{stopTimer=setTimeout(()=>{child.kill('SIGKILL');resolve({code:null,signal:'PROBE_TIMEOUT'});},10000);});await Promise.race([ended,timeout]);clearTimeout(stopTimer);
 if(docker&&(!exitFacts||exitFacts.code!==0))spawnSync('docker',['rm','-f',container],{stdio:'ignore'});
}
if(buffer.length&&!protocolFailure)protocolFailure='TRUNCATED_OR_NON_PROTOCOL_STDOUT';
const result={kind:options.candidate?'FINAL_CANDIDATE_SERVER_PROBE':'DEVELOPMENT_SERVER_PROBE',passed:!failure&&!protocolFailure&&exitFacts?.code===0,candidateSha:bundle.sourceCommit,sourceDirty:bundle.sourceDirty,probeRepositoryHead:head,probeRepositoryDirty:dirty,probeScriptSha256,serverJar:jar,serverJarSha256:hash(fs.readFileSync(jar)),host:{platform:process.platform,arch:process.arch,release:os.release()},runtime:docker?{os:image.Os,architecture:image.Architecture,imageId:image.Id,repoDigests:image.RepoDigests,network:'none',memoryLimitMiB:768,cpuLimit:2}: {os:process.platform,architecture:process.arch},javaVersion,heapLimitMiB:256,startedAt,endedAt:new Date().toISOString(),measurements,memory,rpc,retainedNotifications:notifications.length,markerAbsent:!failure,sourceUnchanged:!failure,exit:exitFacts,protocolFailure,failure};
fs.writeFileSync(evidence+'/server.stderr.log',stderr);fs.writeFileSync(evidence+'/qualification.json',JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify({passed:result.passed,runtime:result.runtime,measurements,exit:exitFacts,failure,protocolFailure}));if(!result.passed)process.exitCode=1;
