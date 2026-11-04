import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const file='/Users/stefan/.cache/gretl-tooling-review/P7-candidate-3-gui/workspace ä space/transitive job/counterprobe.groovy';
const bytes=await readFile(file);
const text=bytes.toString('utf8');
const sha256=createHash('sha256').update(bytes).digest('hex');
const header=text.split('\n').find(line=>line.startsWith("task('consumer'"))??'';
const checks={
  resultReference:text.includes("archive = result('producer').file"),
  placeholderRemoved:!/^\s*archive\s*=\s*workPath/m.test(text),
  afterPreserved:header.includes("after: 'producer'"),
  transitiveDependencyPreserved:/dependsOn\s*:\s*\[[^\]]*'bridge'/.test(header),
  noDirectProducerDependency:!/dependsOn\s*:\s*\[[^\]]*'producer'/.test(header)&&!/dependsOn\s*:\s*'producer'/.test(header),
  noDuplicateResultReference:(text.match(/result\('producer'\)/g)??[]).length===1,
  opaqueActionPreserved:(text.match(/println 'not executed'/g)??[]).length===1,
  defaultTargetPreserved:text.includes("defaults 'consumer'")
};
const report={generatedAt:new Date().toISOString(),file,sha256,size:bytes.length,text,header,checks,passed:Object.values(checks).every(Boolean)};
await writeFile('/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence/gui-transitive-result-verification.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));
if(!report.passed)process.exitCode=1;
