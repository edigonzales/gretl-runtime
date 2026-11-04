import {readFile} from 'node:fs/promises';
const base='/Users/stefan/.cache/gretl-tooling-review/P6-candidate-1-gui/workspace ä space';
for (const rel of ['project-main/job.groovy','project-other/job.groovy']) {
  const text=await readFile(`${base}/${rel}`,'utf8');
  console.log(`===== ${rel} (${Buffer.byteLength(text)} bytes) =====`);
  console.log(text);
}
