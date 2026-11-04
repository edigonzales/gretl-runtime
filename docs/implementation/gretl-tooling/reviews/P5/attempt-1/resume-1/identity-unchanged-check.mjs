import { execFileSync } from 'node:child_process';
import { readFileSync, statSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { captureRequirements } from '../../product/tools/gretl-tooling/review.mjs';

const E = '/Users/stefan/.cache/gretl-tooling-review/P5-review-1/evidence';
const P = '/Users/stefan/.cache/gretl-tooling-review/P5-review-1/product';
const CANDIDATE = '4520ee6960d8b0857fad828f972a44950c5aeb09';
const hash = (p) => createHash('sha256').update(readFileSync(p)).digest('hex');
const git = (repo, args) =>
  execFileSync('git', ['-C', repo, ...args], { encoding: 'utf8' }).trim();

const out = {
  checkedAt: new Date().toISOString(),
  candidate: CANDIDATE,
  reviewWorktree: {},
  advertisedProductRepo: {},
  requestDigests: {},
  modelIdentity: {},
  requestArtifacts: [],
  originalEvidenceFiles: [],
  guiInventory: {},
  bridgeRequests: [],
  bridgeResponses: [],
  errors: []
};

try {
  out.reviewWorktree = {
    repo: P,
    head: git(P, ['rev-parse', 'HEAD']),
    headTree: git(P, ['rev-parse', 'HEAD^{tree}']),
    statusPorcelain: git(P, ['status', '--porcelain=v1', '--untracked-files=all']),
    trackedDiffNames: git(P, ['diff', '--name-only', 'HEAD']),
    stagedDiffNames: git(P, ['diff', '--cached', '--name-only', 'HEAD'])
  };
  out.reviewWorktree.headMatchesCandidate = out.reviewWorktree.head === CANDIDATE;
  out.reviewWorktree.trackedClean =
    out.reviewWorktree.trackedDiffNames === '' &&
    out.reviewWorktree.stagedDiffNames === '';
  if (!out.reviewWorktree.headMatchesCandidate) out.errors.push('review worktree HEAD != candidate');
  if (!out.reviewWorktree.trackedClean) out.errors.push('review worktree has tracked changes');
} catch (error) {
  out.errors.push('review worktree: ' + error.message);
}

try {
  const repo = '/Users/stefan/sources/gretl-runtime';
  out.advertisedProductRepo = {
    repo,
    head: git(repo, ['rev-parse', 'HEAD']),
    trackedStatus: git(repo, ['status', '--porcelain=v1', '--untracked-files=no'])
  };
  out.advertisedProductRepo.headMatchesCandidate =
    out.advertisedProductRepo.head === CANDIDATE;
  out.advertisedProductRepo.trackedClean =
    out.advertisedProductRepo.trackedStatus === '';
  if (!out.advertisedProductRepo.headMatchesCandidate)
    out.errors.push('advertised product repo HEAD != candidate');
  if (!out.advertisedProductRepo.trackedClean)
    out.errors.push('advertised product repo tracked state dirty');
} catch (error) {
  out.errors.push('advertised product repo: ' + error.message);
}

const REQ = JSON.parse(readFileSync(`${E}/request.json`, 'utf8'));
try {
  const digests = captureRequirements(P);
  out.requestDigests = {
    computedRequirementsDigest: digests.requirementsDigest,
    requestRequirementsDigest: REQ.requirementsDigest,
    requirementsMatch: digests.requirementsDigest === REQ.requirementsDigest,
    computedRepositoryRulesDigest: digests.repositoryRulesDigest,
    requestRepositoryRulesDigest: REQ.repositoryRulesDigest,
    rulesMatch: digests.repositoryRulesDigest === REQ.repositoryRulesDigest
  };
  if (!out.requestDigests.requirementsMatch) out.errors.push('requirements digest mismatch');
  if (!out.requestDigests.rulesMatch) out.errors.push('repository rules digest mismatch');
} catch (error) {
  out.errors.push('digests: ' + error.message);
}

for (const artifact of REQ.artifacts) {
  try {
    const actual = hash(artifact.path);
    out.requestArtifacts.push({
      path: artifact.path,
      expected: artifact.sha256,
      actual,
      match: actual === artifact.sha256
    });
    if (actual !== artifact.sha256) out.errors.push('artifact hash mismatch: ' + artifact.path);
  } catch (error) {
    out.requestArtifacts.push({ path: artifact.path, error: error.message, match: false });
    out.errors.push('artifact unreadable: ' + artifact.path);
  }
}

const identityPath = REQ.modelIdentityEvidence;
try {
  const gateValidation = JSON.parse(readFileSync(`${E}/gate-validation.json`, 'utf8'));
  const record = gateValidation.modelIdentity ?? {};
  const actual = hash(identityPath);
  out.modelIdentity = {
    path: identityPath,
    recordedSha256: record.sha256 ?? null,
    actualSha256: actual,
    hashStable: record.sha256 ? record.sha256 === actual : null,
    recordedValidated: record.validated ?? null,
    recordedProvider: record.provider ?? null,
    recordedModel: record.model ?? null,
    recordedApiHost: record.apiHost ?? null
  };
} catch (error) {
  out.errors.push('model identity: ' + error.message);
}

const original = [
  'request.json',
  'review.md',
  'verdict.json',
  'validation.json',
  'process.json',
  'gate-validation.json',
  'root-standard/manifest.json',
  'root-standard/command.log',
  'root-services/manifest.json',
  'root-services/command.log',
  'gui/index.json',
  'gui/files.json',
  'counterprobe-host.json',
  'counterprobe-host.mjs',
  'extension-runs.json',
  'own-tooling-targeted-summary.json',
  'own-review-contract.log',
  'own-tooling-docs.log',
  'completed-session-identity.json'
];
for (const rel of original) {
  const p = `${E}/${rel}`;
  try {
    const st = statSync(p);
    out.originalEvidenceFiles.push({
      path: p,
      sha256: hash(p),
      size: st.size,
      mtime: st.mtime.toISOString()
    });
  } catch (error) {
    out.originalEvidenceFiles.push({ path: p, error: error.message });
    out.errors.push('original evidence unreadable: ' + rel);
  }
}

try {
  const files = JSON.parse(readFileSync(`${E}/gui/files.json`, 'utf8'));
  let verified = 0;
  const bad = [];
  for (const file of files) {
    try {
      if (hash(file.path) === file.sha256) verified++;
      else bad.push(file.path);
    } catch (error) {
      bad.push(`${file.path} (${error.message})`);
    }
  }
  out.guiInventory = {
    declaredFiles: files.length,
    verified,
    badCount: bad.length,
    bad: bad.slice(0, 10)
  };
  if (bad.length) out.errors.push('gui inventory mismatch: ' + bad.length);
} catch (error) {
  out.errors.push('gui inventory: ' + error.message);
}

for (const id of ['003', '004', '005', '006', '007', '008', '009']) {
  const reqPath = `${E}/gui-bridge/requests/${id}.json`;
  const resPath = `${E}/gui-bridge/responses/${id}.json`;
  try {
    out.bridgeRequests.push({
      id,
      path: reqPath,
      sha256: hash(reqPath),
      purpose: JSON.parse(readFileSync(reqPath, 'utf8')).purpose
    });
  } catch (error) {
    out.errors.push(`request ${id}: ${error.message}`);
  }
  try {
    const response = JSON.parse(readFileSync(resPath, 'utf8'));
    const pngPath = `${E}/gui-bridge/responses/${id}.png`;
    const axPath = `${E}/gui-bridge/responses/${id}.ax.txt`;
    const pngSha = hash(pngPath);
    const axSha = hash(axPath);
    out.bridgeResponses.push({
      id,
      jsonPath: resPath,
      jsonSha256: hash(resPath),
      at: response.at,
      candidate: response.candidate,
      transportOnly: response.transportOnly,
      failure: response.failure,
      pngPath,
      pngSha256: pngSha,
      pngDeclaredSha256: response.pngSha256,
      pngHashMatches: pngSha === response.pngSha256,
      axPath,
      axSha256: axSha,
      axDeclaredSha256: response.axSha256,
      axHashMatches: axSha === response.axSha256
    });
    if (pngSha !== response.pngSha256) out.errors.push(`png hash mismatch ${id}`);
    if (axSha !== response.axSha256) out.errors.push(`ax hash mismatch ${id}`);
    if (response.candidate !== CANDIDATE) out.errors.push(`response ${id} candidate mismatch`);
    if (response.failure !== null) out.errors.push(`response ${id} failure not null`);
  } catch (error) {
    out.errors.push(`response ${id}: ${error.message}`);
  }
}

out.ok = out.errors.length === 0;
writeFileSync(`${E}/resume-1/identity-unchanged-check.json`, JSON.stringify(out, null, 2));
console.log(JSON.stringify({
  ok: out.ok,
  errors: out.errors,
  head: out.reviewWorktree.head,
  trackedClean: out.reviewWorktree.trackedClean,
  status: out.reviewWorktree.statusPorcelain,
  advertised: out.advertisedProductRepo,
  artifactMatches: out.requestArtifacts.filter(a => a.match).length + '/' + out.requestArtifacts.length,
  guiInventory: out.guiInventory,
  modelIdentity: out.modelIdentity,
  digests: out.requestDigests,
  responses: out.bridgeResponses.map(r => ({ id: r.id, pngHashMatches: r.pngHashMatches, axHashMatches: r.axHashMatches }))
}, null, 2));
