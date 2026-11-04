import {readFile,stat,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const here=path.dirname(fileURLToPath(import.meta.url));
const gui=path.join(here,'gui');
const hash=b=>createHash('sha256').update(b).digest('hex');
const sha=async p=>hash(await readFile(p));
const imageSize=b=>{
  if(b.length>=24&&b[0]===0x89&&b.toString('ascii',1,4)==='PNG')return{format:'png',width:b.readUInt32BE(16),height:b.readUInt32BE(20)};
  if(b.length>=4&&b[0]===0xff&&b[1]===0xd8){
    let i=2;
    while(i+9<b.length&&b[i]===0xff){
      const marker=b[i+1];if(marker===0xd8||marker===0xd9){i+=2;continue;}
      const length=b.readUInt16BE(i+2);if(length<2||i+2+length>b.length)return null;
      if([0xc0,0xc1,0xc2,0xc3].includes(marker))return{format:'jpeg',height:b.readUInt16BE(i+5),width:b.readUInt16BE(i+7)};
      i+=2+length;
    }
  }
  return null;
};
const problems=[];
const notes={};

const manifest=JSON.parse(await readFile(path.join(gui,'files.json'),'utf8'));
let manifestOk=0;
for(const entry of manifest){
  let st;try{st=await stat(entry.path);}catch{problems.push(`manifest missing ${entry.path}`);continue;}
  const h=await sha(entry.path);
  if(st.size!==entry.size)problems.push(`manifest size ${entry.path}: ${st.size} != ${entry.size}`);
  if(h!==entry.sha256)problems.push(`manifest hash ${entry.path}: ${h} != ${entry.sha256}`);
  if(st.size===entry.size&&h===entry.sha256)manifestOk++;
}
notes.manifest={entries:manifest.length,valid:manifestOk};

const index=JSON.parse(await readFile(path.join(gui,'index.json'),'utf8'));
notes.indexCandidate=index.candidate;
notes.guiCases=index.cases;
const caseDir=path.join(gui,'current','cases');
let caseOk=0,imageOk=0;const imageFormats={};
for(const [caseId,files] of Object.entries(index.cases)){
  for(const rel of files){
    if(!rel.startsWith('current/cases/'))continue;
    const p=path.join(gui,rel);
    const json=JSON.parse(await readFile(p,'utf8'));
    const base=p.slice(0,-5);
    if(Object.hasOwn(json,'candidateSha')&&json.candidateSha!==index.candidate)problems.push(`${rel}: candidate ${json.candidateSha}`);
    if(Object.hasOwn(json,'sourceDirty')&&json.sourceDirty!==false)problems.push(`${rel}: current case sourceDirty ${json.sourceDirty}`);
    if(Object.hasOwn(json,'serverSha256')&&json.serverSha256!=='5189670a5c28dbf86b37c9115d5514b4e2fdffb7f4e1c977e19b8512db8da80c')problems.push(`${rel}: server hash`);
    if(Object.hasOwn(json,'vsixSha256')&&!['283fd089be089b0e0f7cccff36fdc29bbb0eb99d30b6fdaea64062a39554e3df','151e875bc863398d952ed1f2946290ccd6c2f46d3766d64d3037cb20068a5e7b'].includes(json.vsixSha256))problems.push(`${rel}: vsix hash`);
    if(json.vsixSha256==='151e875bc863398d952ed1f2946290ccd6c2f46d3766d64d3037cb20068a5e7b'&&!rel.includes('final-service-vsix-startup'))problems.push(`${rel}: unexpected final vsix`);
    if(json.axSha256){
      const ax=await readFile(base+'.ax.txt');
      if(hash(ax)!==json.axSha256)problems.push(`${rel}: AX hash`);
      const png=await readFile(base+'.png');
      if(hash(png)!==json.screenshotSha256)problems.push(`${rel}: screenshot hash`);
      const size=imageSize(png);if(!size||size.width<400||size.height<300)problems.push(`${rel}: bad image dimensions ${JSON.stringify(size)}`);
      if(size&&size.width>=400&&size.height>=300){imageOk++;imageFormats[size.format]=(imageFormats[size.format]??0)+1;}
    }
    if(json.before){for(const key of ['before','after','closed','reopened','zoomed','restarted']){if(!json[key])continue;const q=path.join(path.dirname(path.dirname(p)),json[key]);try{await stat(q);}catch{problems.push(`${rel}: missing ${key} ${json[key]}`);}}}
    caseOk++;
  }
}
notes.cases={validated:caseOk,screenshots:imageOk,imageFormats};

const finalIdentity=JSON.parse(await readFile(path.join(gui,'current','identity.json'),'utf8'));
if(finalIdentity.candidateSha!==index.candidate||finalIdentity.sourceDirty!==false||finalIdentity.vsixSha256!=='151e875bc863398d952ed1f2946290ccd6c2f46d3766d64d3037cb20068a5e7b'||finalIdentity.serverSha256!=='5189670a5c28dbf86b37c9115d5514b4e2fdffb7f4e1c977e19b8512db8da80c')problems.push('final identity mismatch');
const qualification=JSON.parse(await readFile(path.join(gui,'current','qualification.json'),'utf8'));
if(qualification.candidateSha!==index.candidate||qualification.passed!==true||qualification.finalVsixSha256!==finalIdentity.vsixSha256||qualification.finalServerSha256!==finalIdentity.serverSha256)problems.push('qualification mismatch');

const reuse=JSON.parse(await readFile(path.join(gui,'current','client-payload-reuse-comparison.json'),'utf8'));
if(reuse.candidateSha!==index.candidate||reuse.developmentSourceDirty!==true||reuse.currentSourceDirty!==false||!Array.isArray(reuse.byteIdenticalClient)||reuse.byteIdenticalClient.length!==4)problems.push('client reuse description incomplete');
notes.reuse={clientEntries:reuse.byteIdenticalClient?.length,developmentDirty:reuse.developmentSourceDirty,currentDirty:reuse.currentSourceDirty};

const finalBuf=JSON.parse(await readFile(path.join(gui,'current','captures','buffers-1793780405426.json'),'utf8'));
notes.finalBuffer={keys:Object.keys(finalBuf),serverCount:[...JSON.stringify(finalBuf).matchAll(/serverInstance|serverInstanceId|server/g)].length};

const result={checkedAt:new Date().toISOString(),candidate:index.candidate,notes,problems};
await writeFile(path.join(here,'gui-evidence-validation.json'),JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify(result,null,2));
