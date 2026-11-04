import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const base = '/Users/stefan/.cache/gretl-tooling-review/P8-review-1/evidence/gui-bridge/responses';
const requestIds = ['001','002','004','005','006','007','008','009','010','011','012'];
const retryId = '003';
const sha = path => createHash('sha256').update(readFileSync(path)).digest('hex');
const json = id => JSON.parse(readFileSync(resolve(base, `${id}.json`), 'utf8'));
const buffers = id => JSON.parse(readFileSync(resolve(base, `${id}.buffers.json`), 'utf8'));
const candidate = '54bb123070fc7bd81aea58b4e1191beecf568cbf';
const vsix = 'c501014f5b1e7bfd26596e6d8cb5df49ff8d89560dc04dfcbbcc11995719fb6e';
const server = '36d8bdd4655b0974a4ced93ef9da8350449ac41721baeaef2d5a604640c5ef1e';
const docUri = 'file:///Users/stefan/.cache/gretl-tooling-review/P8-candidate-1-gui/workspace%20%C3%A4%20space/project-created/reviewer-compound/job.groovy';
const findDoc = state => state.documents.find(doc => doc.uri === docUri);
const findModeler = state => state.modelers.find(modeler => modeler.documentId === docUri);

for (const id of requestIds) {
  const response = json(id);
  assert.equal(response.id, id);
  if (id === '010') assert.match(response.error, /Buffer capture: Error: No guarded command center/);
  else assert.equal(response.error, null, `${id}: ${response.error}`);
  assert.equal(response.artifactSourceCommit, candidate);
  assert.equal(response.vsixSha256, vsix);
  assert.equal(response.serverSha256, server);
  assert.ok(existsSync(resolve(base, `${id}.jpg`)), `${id} image`);
  assert.ok(existsSync(resolve(base, `${id}.ax.txt`)), `${id} AX`);
}

const retry = json(retryId);
assert.equal(retry.id, retryId);
assert.match(retry.error, /Bind result: matches 0/);
assert.equal(retry.artifactSourceCommit, candidate);
assert.ok(existsSync(resolve(base, `${retryId}.jpg`)));
assert.ok(existsSync(resolve(base, `${retryId}.ax.txt`)));

const initial = findDoc(buffers('001'));
assert.equal(initial.dirty, false);
assert.equal(initial.sha256, '373346d7d1832992138f70c49ef4f09a40bdae257bb50749865dea25383b025e');
const dirty = findDoc(buffers('002'));
assert.equal(dirty.dirty, true);
assert.equal(dirty.sha256, '62c5c14727e5c7e582580fdec359032774e39930ce59ce87183f7736dd292df0');
assert.match(dirty.text, /DIRTY REVIEWER MARKER/);
assert.doesNotMatch(dirty.text, /archive = result/);
const bound = findDoc(buffers('006'));
assert.equal(bound.dirty, true);
assert.equal(bound.sha256, '2d6b2288fa34fe09163be3606e7e94c91308eb4f54710623863cb46300fe9ea3');
assert.match(bound.text, /archive = result\('fetch'\)\.file/);
const undone = findDoc(buffers('007'));
assert.equal(undone.dirty, true);
assert.equal(undone.sha256, dirty.sha256);
assert.equal(undone.text, dirty.text);
assert.match(undone.text, /DIRTY REVIEWER MARKER/);
const layout = findDoc(buffers('008'));
assert.equal(layout.sha256, dirty.sha256, 'layout changed source');
assert.equal(layout.text, dirty.text);
const restarted = findDoc(buffers('009'));
assert.equal(restarted.sha256, dirty.sha256, 'restart changed source');
assert.equal(restarted.text, dirty.text);
assert.notEqual(findModeler(buffers('008')).context.serverInstanceId, findModeler(buffers('009')).context.serverInstanceId, 'restart did not replace server instance');
const closedPrompt = readFileSync(resolve(base, '010.ax.txt'), 'utf8');
assert.match(closedPrompt, /Do you want to save the changes you made to job\.groovy\?/);
assert.match(closedPrompt, /button Save/);
assert.match(closedPrompt, /button Don't Save/);
assert.match(closedPrompt, /button Cancel/);
assert.equal(json('010').buffers, null);
const reopenedText = findDoc(buffers('011'));
assert.equal(reopenedText.dirty, false);
assert.equal(reopenedText.sha256, initial.sha256);
assert.equal(reopenedText.text, initial.text);
assert.doesNotMatch(reopenedText.text, /DIRTY REVIEWER MARKER/);
assert.doesNotMatch(reopenedText.text, /archive = result/);
const reopenedModeler = findModeler(buffers('012'));
assert.equal(reopenedModeler.status, 'FRESH');
assert.equal(reopenedModeler.dirty, false);
assert.equal(findDoc(buffers('012')).sha256, initial.sha256);
assert.equal(findDoc(buffers('012')).version, 5);

const images = requestIds.map(id => ({ id, path: resolve(base, `${id}.jpg`), sha256: sha(resolve(base, `${id}.jpg`)) }));
const result = {
  caseId: 'G30',
  candidateSha: candidate,
  vsixSha256: vsix,
  serverSha256: server,
  fixture: '/Users/stefan/.cache/gretl-tooling-review/P8-candidate-1-gui/workspace ä space/project-created/reviewer-compound/job.groovy',
  sequence: [
    { id: '001', action: 'create/open + modeler', sourceVersion: initial.version, dirty: initial.dirty, sha256: initial.sha256, serverInstanceId: findModeler(buffers('001')).context.serverInstanceId },
    { id: '002', action: 'dirty selected-text replace', sourceVersion: dirty.version, dirty: dirty.dirty, sha256: dirty.sha256, serverInstanceId: findModeler(buffers('002')).context.serverInstanceId },
    { id: '006', action: 'confirm BindResult fetch/file -> unpack/archive', sourceVersion: bound.version, dirty: bound.dirty, sha256: bound.sha256, serverInstanceId: findModeler(buffers('006')).context.serverInstanceId },
    { id: '007', action: 'Undo text', sourceVersion: undone.version, dirty: undone.dirty, sha256: undone.sha256, serverInstanceId: findModeler(buffers('007')).context.serverInstanceId },
    { id: '008', action: 'Reset layout', sourceVersion: layout.version, dirty: layout.dirty, sha256: layout.sha256, serverInstanceId: findModeler(buffers('008')).context.serverInstanceId },
    { id: '009', action: 'GRETL Restart Language Server', sourceVersion: restarted.version, dirty: restarted.dirty, sha256: restarted.sha256, serverInstanceId: findModeler(buffers('009')).context.serverInstanceId },
    { id: '010', action: 'close dirty source, native prompt observed', prompt: true, buffers: null },
    { id: '011', action: "Don't Save", sourceVersion: reopenedText.version, dirty: reopenedText.dirty, sha256: reopenedText.sha256 },
    { id: '012', action: 'reopen same path + open fresh Modeler', sourceVersion: findDoc(buffers('012')).version, dirty: findDoc(buffers('012')).dirty, sha256: findDoc(buffers('012')).sha256, serverInstanceId: reopenedModeler.context.serverInstanceId }
  ],
  retryObservation: {
    request: retryId,
    error: retry.error,
    meaning: 'The first label-only click found no hittable AX match; the immediate occurrence:0 retry (004) opened the same visible Bind result button. The failed attempt is preserved and is not counted as a positive action.'
  },
  images,
  conclusion: 'All requested G30 effects are visible in native images/AX and exact read-only buffers. Undo text restored the pre-binding dirty source byte-for-byte, Reset layout did not edit source, restart replaced the server instance while preserving dirty version 4, and reopen loaded the unchanged saved baseline from disk.'
};
writeFileSync('/Users/stefan/.cache/gretl-tooling-review/P8-review-1/evidence/g30-verification.json', JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result, null, 2));
