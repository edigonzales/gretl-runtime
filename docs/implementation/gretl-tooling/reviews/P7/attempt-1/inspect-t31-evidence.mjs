import {createHash} from 'node:crypto';
import {readFile,readdir,stat,writeFile} from 'node:fs/promises';
import path from 'node:path';
const evidence='/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence';
const product='/Users/stefan/.cache/gretl-tooling-review/P7-review-1/product';
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const manifests={standard:JSON.parse(await readFile(evidence+'/root-standard/manifest.json','utf8')),services:JSON.parse(await readFile(evidence+'/root-services/manifest.json','utf8'))};
const fixture=path.join(product,'gretl-integration-tests/src/test/resources/jobs/modeler-created/job.groovy');
const provenance=path.join(product,'gretl-integration-tests/src/test/resources/jobs/modeler-created/gui-provenance.json');
const fixtureBytes=await readFile(fixture),fixtureHash=hash(fixtureBytes),provenanceJson=JSON.parse(await readFile(provenance,'utf8'));
const runs={};
for(const [name,manifest] of Object.entries(manifests)){
  const xmlEntry=manifest.captured.find(e=>e.path.endsWith('/TEST-ch.so.agi.gretl.it.GraphicalModelerScriptJobIT.xml'));
  const txtEntry=manifest.captured.find(e=>e.path.endsWith('/ch.so.agi.gretl.it.GraphicalModelerScriptJobIT.txt'));
  if(!xmlEntry||!txtEntry){runs[name]={found:false};continue;}
  const xml=await readFile(xmlEntry.path,'utf8'),txt=await readFile(txtEntry.path,'utf8');
  const attrs=Object.fromEntries([...(xml.match(/<testsuite\b([^>]*)>/)?.[1]??'').matchAll(/([A-Za-z]+)="([^"]*)"/g)].map(m=>[m[1],m[2]]));
  const testcase=(xml.match(/<testcase\b[^>]*actualGuiGeneratedSourceDownloadsAndExtractsALocalArchiveWithoutRewritingItsBytes[^>]*\/?>/)??[])[0]??'';
  runs[name]={
    found:true,
    xmlPath:xmlEntry.path,
    xmlSha256:hash(Buffer.from(xml)),
    xmlManifestSha256:xmlEntry.sha256,
    xmlHashMatches:hash(Buffer.from(xml))===xmlEntry.sha256,
    txtPath:txtEntry.path,
    txtSha256:hash(Buffer.from(txt)),
    txtManifestSha256:txtEntry.sha256,
    txtHashMatches:hash(Buffer.from(txt))===txtEntry.sha256,
    attrs,
    passed:attrs.tests==='1'&&attrs.failures==='0'&&attrs.errors==='0'&&attrs.skipped==='0'&&!/<failure\b|<error\b/.test(xml)&&/Tests run: 1, Failures: 0, Errors: 0, Skipped: 0/.test(txt),
    testcasePresent:testcase.length>0,
    testcase
  };
}
const foundFiles=[];
async function walk(dir,depth=0){
  if(depth>5)return;
  let entries;try{entries=await readdir(dir,{withFileTypes:true});}catch{return;}
  for(const entry of entries){
    const full=path.join(dir,entry.name);
    if(entry.isDirectory())await walk(full,depth+1);
    else if(entry.name==='gui-runtime-identity.json')foundFiles.push(full);
  }
}
await walk('/Users/stefan/sources/gretl-runtime/gretl-integration-tests/target');
await walk('/Users/stefan/.cache/gretl-tooling-review/p7-c3-standard/evidence/gretl-integration-tests/target');
await walk('/Users/stefan/.cache/gretl-tooling-review/p7-c3-services/evidence/gretl-integration-tests/target');
const identities=[];
for(const file of [...new Set(foundFiles)]){
  try{
    const bytes=await readFile(file),json=JSON.parse(bytes.toString('utf8'));
    const extra={};
    for(const key of ['source','stagedSource','report']){
      if(typeof json[key]==='string'){
        try{const content=await readFile(json[key]);extra[key]={path:json[key],sha256:hash(content),size:content.length,mtime:(await stat(json[key])).mtime.toISOString()};}
        catch(error){extra[key]={path:json[key],error:String(error)};}
      }
    }
    const info=await stat(file);
    identities.push({path:file,sha256:hash(bytes),mtime:info.mtime.toISOString(),json,extra});
  }catch(error){identities.push({path:file,error:String(error)});}
}
const report={
  generatedAt:new Date().toISOString(),
  candidateSha:'53f7f3daaf92d002fd75978c25471321fd99a310',
  trackedFixture:{path:fixture,sha256:fixtureHash,size:fixtureBytes.length,provenancePath:provenance,provenanceSha256:hash(await readFile(provenance)),provenance:provenanceJson},
  fixtureMatchesProvenance:fixtureHash===provenanceJson.sha256,
  runs,
  runtimeIdentityFiles:identities,
  passed:Object.values(runs).every(r=>r.found&&r.passed&&r.testcasePresent)&&fixtureHash===provenanceJson.sha256&&identities.some(i=>i.json?.kind==='EXACT_GUI_SOURCE_SCRIPT_ROUNDTRIP'&&i.json.inputSha256===fixtureHash&&i.json.mode==='SCRIPT'&&i.json.httpRequests===1&&i.json.outputSha256===i.json.expectedOutputSha256&&i.extra?.source?.sha256===fixtureHash&&i.extra?.stagedSource?.sha256===fixtureHash)
};
await writeFile(evidence+'/t31-evidence-validation.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));
if(!report.passed)process.exitCode=1;
