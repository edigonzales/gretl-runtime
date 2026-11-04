import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import path from 'node:path';
const dir='/Users/stefan/.cache/gretl-tooling-review/P7-candidate-3-gui/workspace ä space/transitive job';
const file=path.join(dir,'counterprobe.groovy');
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
await mkdir(dir,{recursive:true});
await writeFile(file,source);
const bytes=await readFile(file);
const report={generatedAt:new Date().toISOString(),path:file,size:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex'),source};
await writeFile('/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence/gui-transitive-fixture.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));
