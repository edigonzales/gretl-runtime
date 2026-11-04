import { readFileSync, writeFileSync } from 'node:fs';
const root = '/Users/stefan/.cache/gretl-tooling-review/P7-candidate-3-gui/workspace ä space';
const names = [
  'transitive job/job.groovy',
  'preservation project/job.groovy',
  'preservation project/dynamic.groovy',
  'preservation project/empty.groovy',
  'preservation project/foreign.groovy',
  'preservation project/sub/job.groovy',
  'generated job/job.groovy',
  'generated job/native empty/job.groovy',
  'gretl-modeler.code-workspace',
];
const files = names.map((name) => {
  try {
    const path = `${root}/${name}`;
    const bytes = readFileSync(path);
    return { name, path, size: bytes.length, text: bytes.toString('utf8') };
  } catch (e) {
    return { name, error: String(e) };
  }
});
const out = '/Users/stefan/.cache/gretl-tooling-review/P7-review-1/evidence/workspace-fixtures.json';
writeFileSync(out, JSON.stringify({ root, files }, null, 2));
for (const f of files) {
  console.log(`### ${f.name} (${f.size ?? 'error'})`);
  if (f.error) console.log(f.error); else console.log(f.text.replace(/\r/g, '<CR>'));
}
console.log(`wrote ${out}`);
