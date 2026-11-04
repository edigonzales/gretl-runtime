import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const evidence = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/evidence';
const gui = resolve(evidence, 'gui/current');
const qualification = JSON.parse(readFileSync(resolve(gui, 'qualification.json'), 'utf8'));
const files = new Map(JSON.parse(readFileSync(resolve(evidence, 'gui/files.json'), 'utf8')).map(row => [row.path, row]));
const sha = path => createHash('sha256').update(readFileSync(path)).digest('hex');
const assertListed = path => {
  assert.existsSync?.(path);
  assert.ok(existsSync(path), path);
  const row = files.get(path);
  assert.ok(row, `not in gui/files.json: ${path}`);
  assert.equal(sha(path), row.sha256, path);
};

function existing(caseIds, actions, observations) {
  const screenshots = [];
  const details = [];
  for (const id of caseIds) {
    const names = qualification.cases[id];
    assert.ok(Array.isArray(names) && names.length > 0, `missing qualification cases for ${id}`);
    for (const name of names) {
      const image = resolve(gui, 'cases', `${name}.jpg`);
      const metadata = resolve(gui, 'cases', `${name}.json`);
      const ax = resolve(gui, 'cases', `${name}.ax.txt`);
      assert.ok(existsSync(image), `missing image ${image}`);
      assert.ok(existsSync(metadata), `missing metadata ${metadata}`);
      assert.ok(existsSync(ax), `missing AX ${ax}`);
      assertListed(image);
      screenshots.push({ path: image, sha256: sha(image), kind: 'native-screenshot' });
      details.push(metadata, ax);
    }
  }
  details.push(resolve(gui, 'qualification.json'));
  return { caseIds, kind: 'verified-current', actions, observations, screenshots, evidence: details };
}

const rows = [
  existing(['G01','G02','G03','G04','G05','G06','G07','G08','G09','G10'],
    'Actual installed VS Code CUA interactions recorded for G01-G10: isolated VSIX install/start, completion, hover/signature, diagnostics/navigation, references, rename with native undo/redo, quick fix, dirty outline, restart recovery, and configuration failure/restore.',
    'Reviewer validated every listed screenshot and AX hash, read the P5 cases, and confirmed the disclosed b5/C2/final product identities retain identical installed client payloads. Final G01 installed-payload verification and request artifact hashes match the final candidate.'),
  existing(['G11','G12','G13','G14','G15','G16'],
    'Actual installed VS Code CUA interactions recorded for G11-G16: definition graph, inner invocation visibility, native node drag/filter/pan, source-to-graph navigation, save-prompt recovery, and layout/accessibility states.',
    'Reviewer validated geometry, image and AX hashes. G13 is explicitly a view-only oracle on the pre-correction GUI payload; the final installed payload comparison and reviewer G30 layout action cover the final candidate semantics. G15 makes no claim that a reopened saved broken file remains dirty.'),
  existing(['G17','G18','G19','G20','G21','G22','G23','G24','G25'],
    'Actual installed VS Code CUA interactions recorded for G17-G25: empty-file creation, typed/nested forms, cycle rejection, result binding, delete/reference protection, transitive disconnect, opaque preservation, text/view undo-redo, close and reopen.',
    'Reviewer validated the screenshots, AX and source/buffer artifacts. The reviewer independently repeated a new result-binding, layout, native undo, restart and reopen path in G30 rather than relying on these implementer cases.'),
  existing(['G26','G27','G28','G29'],
    'Actual installed VS Code CUA interactions recorded for G26-G29: two independent native edits, stale draft after native history, server stop/restart with dirty buffers and catalog mismatch recovery, and a 500-action interactive graph.',
    'Reviewer read the four key images and validated their hashes and AX. G27 shows the stale confirmation disabled; G28 shows dirty text preserved across restart; G29 shows the real large-file modeler and bounded process evidence.'),
  {
    caseIds: ['G30'],
    kind: 'own-run',
    actions: 'Reviewer authored a new private fixture project-created/reviewer-compound/job.groovy through gui-bridge. Native CUA sequence: create/open, dirty selected-text replace, Modeler reopen, Bind result draft fetch/file -> unpack/archive, confirm source edit, Undo text, Reset layout, GRETL Restart Language Server, close dirty editor with observed Don\'t Save prompt, reopen same path from disk, and open the fresh Modeler.',
    observations: 'Dirty buffer version 2 hash 62c5c147...; binding produced version 3 hash 2d6b2288... and added archive = result(\'fetch\').file; Undo text restored exactly the pre-binding dirty hash and diagnostic; Reset layout left that hash unchanged; Restart changed serverInstanceId e2344a11... to 2693b140... while preserving the dirty hash; reopen loaded the saved baseline hash 373346d7... with the dirty marker gone, and the Modeler showed fetch -> unpack without the binding edge. Images and AX were personally read.',
    screenshots: ['001','002','005','006','007','008','009','010','011','012'].map(id => {
      const path = resolve(evidence, 'gui-bridge/responses', `${id}.jpg`);
      assert.ok(existsSync(path), path);
      return { path, sha256: sha(path), kind: 'reviewer-native-screenshot' };
    }),
    evidence: ['002','005','006','007','008','009','010','011','012'].flatMap(id => [
      resolve(evidence, 'gui-bridge/responses', `${id}.json`),
      resolve(evidence, 'gui-bridge/responses', `${id}.ax.txt`),
      resolve(evidence, 'gui-bridge/responses', `${id}.buffers.json`)
    ]).filter(path => existsSync(path)).concat(
      resolve(evidence, 'gui-bridge/README.md'),
      resolve(evidence, 'verify-g30.mjs'),
      resolve(evidence, 'g30-verification.json')
    )
  }
];
for (const row of rows) for (const path of row.evidence) assert.ok(existsSync(path), path);
writeFileSync(resolve(evidence, 'gui-evidence-generated.json'), JSON.stringify(rows, null, 2) + '\n');
console.log(`Generated ${rows.length} GUI groups, ${rows.flatMap(row => row.caseIds).length} case IDs and ${rows.flatMap(row => row.screenshots).length} screenshots.`);
