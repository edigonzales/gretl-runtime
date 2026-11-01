#!/usr/bin/env node
// Local phase-review orchestration. OpenCode is the only model client.
import { spawn, spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync, existsSync, mkdirSync, openSync, closeSync } from 'node:fs';
import { resolve, isAbsolute } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

export function criteria(phase) {
  assert.match(phase, /^TST[1-4]$/);
  const phases = phase === 'TST4' ? [1, 2, 3, 4] : [Number(phase.slice(3))];
  return phases.flatMap(n => Array.from({ length: n === 4 ? 8 : 10 }, (_, i) => `TST${n}-${String(i + 1).padStart(2, '0')}`));
}
const exactSet = (actual, expected) => {
  assert.equal(new Set(actual).size, actual.length, 'Duplicate entries');
  assert.deepEqual([...actual].sort(), [...expected].sort(), 'Incomplete or unexpected entries');
};
export function validateRequest(request) {
  assert.equal(request.schemaVersion, 1);
  assert.equal(request.assignment, 'gretl-script-first-testing');
  for (const field of ['baseSha', 'assignmentBaseSha', 'candidateSha']) assert.match(request[field], /^[a-f0-9]{40}$/);
  assert(Number.isInteger(request.attempt) && request.attempt > 0);
  exactSet(request.criteria, criteria(request.phase));
  for (const field of ['reviewWorktree', 'reviewDirectory']) assert(isAbsolute(request[field]));
  assert.notEqual(request.reviewDirectory, request.reviewWorktree);
  assert(!request.reviewDirectory.startsWith(request.reviewWorktree + '/'));
  assert(request.requiredChecks.length > 0);
  exactSet(request.requiredChecks.map(c => c.id), [...new Set(request.requiredChecks.map(c => c.id))]);
  assert.equal(request.reviewer.provider, 'deepseek');
  assert.equal(request.reviewer.model, 'deepseek-flash');
  assert.equal(request.reviewer.apiHost, 'api.deepseek.com');
  assert.equal(request.reviewer.requestedVersion, 'DeepSeek-V4.1-Flash-1017');
}
export function validateVerdict(request, verdict, report, completion) {
  validateRequest(request);
  assert.equal(completion.exit, 0, 'OpenCode process failed');
  assert.equal(completion.finished, true, 'Session is not complete');
  assert.equal(completion.unchanged, true, 'Review checkout changed');
  for (const key of ['schemaVersion', 'assignment', 'phase', 'attempt', 'baseSha', 'assignmentBaseSha', 'candidateSha'])
    assert.equal(verdict[key], request[key], `Wrong ${key}`);
  assert(['PASS', 'FAIL', 'BLOCKED'].includes(verdict.verdict));
  assert(report.trim().length > 100, 'Missing substantive reviewer report');
  assert(report.includes(request.candidateSha), 'Report does not name candidate');
  assert.equal(verdict.report, 'review.md');
  for (const key of ['provider', 'model', 'apiHost']) assert.equal(verdict.reviewer[key], request.reviewer[key]);
  assert.match(verdict.reviewer.openCodeVersion, /^(?:opencode v|v)?2\./);
  assert(verdict.reviewer.modelMappingEvidence?.length > 10);
  exactSet(verdict.criteria.map(c => c.id), criteria(request.phase));
  for (const row of verdict.criteria) {
    assert(['fulfilled', 'violated', 'unverified'].includes(row.result));
    assert(row.evidence?.length > 0);
  }
  const checkIds = verdict.checks.map(c => c.id);
  assert.equal(new Set(checkIds).size, checkIds.length, 'Duplicate checks');
  for (const check of request.requiredChecks) assert(checkIds.includes(check.id), `Missing check ${check.id}`);
  assert(Array.isArray(verdict.openMandatoryFindings) && Array.isArray(verdict.missingRequiredChecks));
  if (verdict.verdict === 'PASS') {
    assert(verdict.criteria.every(c => c.result === 'fulfilled'));
    assert(verdict.checks.every(c => c.result === 'passed' && c.evidence?.length > 0));
    assert.equal(verdict.openMandatoryFindings.length, 0);
    assert.equal(verdict.missingRequiredChecks.length, 0);
    assert(verdict.counterprobes.length > 0 && verdict.counterprobes.every(c => c.description && c.evidence?.length));
  }
  return verdict.verdict;
}
function command(argv, cwd) {
  const result = spawnSync(argv[0], argv.slice(1), { cwd, encoding: 'utf8', timeout: 30000 });
  assert.equal(result.status, 0, `${argv[0]} failed: ${result.stderr}`);
  return result.stdout.trim();
}
export async function runReview(requestFile, binary) {
  const request = JSON.parse(readFileSync(requestFile, 'utf8'));
  validateRequest(request);
  assert(isAbsolute(binary));
  const version = command([binary, '--version']);
  assert.match(version, /(?:^|v)2\./);
  const help = command([binary, 'run', '--help']);
  for (const flag of ['--standalone', '--model', '--file', '--format']) assert(help.includes(flag));
  const root = command(['git', 'rev-parse', '--show-toplevel']);
  assert.equal(command(['git', 'rev-parse', 'HEAD'], root), request.candidateSha);
  assert.equal(command(['git', 'status', '--porcelain'], root), '', 'Candidate must be clean');
  assert(!existsSync(request.reviewWorktree), 'Refusing existing worktree');
  mkdirSync(request.reviewDirectory, { recursive: true, mode: 0o700 });
  for (const file of ['review.md', 'verdict.json', 'opencode.jsonl'])
    assert(!existsSync(resolve(request.reviewDirectory, file)), 'Refusing existing review output');
  command(['git', 'worktree', 'add', '--detach', request.reviewWorktree, request.candidateSha], root);
  const args = ['run', '--standalone', '--model', 'deepseek/deepseek-flash', '--format', 'json'];
  for (const file of [request.plan, request.reviewPrompt, resolve(requestFile)]) args.push('--file', file);
  args.push(`Prüfe ${request.phase} unabhängig gemäss den angehängten Dokumenten. Führe alle Pflichtgates und neue eigene Gegenproben aus. Verändere keine Kandidatendateien. Schreibe review.md und verdict.json in ${request.reviewDirectory}. Sichere deine Nachweise vor clean. Beende danach die Sitzung vollständig.`);
  const log = openSync(resolve(request.reviewDirectory, 'opencode.jsonl'), 'wx', 0o600);
  const err = openSync(resolve(request.reviewDirectory, 'opencode.stderr.log'), 'wx', 0o600);
  let exit;
  try {
    exit = await new Promise((done, reject) => {
      const child = spawn(binary, args, { cwd: request.reviewWorktree, stdio: ['ignore', log, err] });
      child.on('error', reject); child.on('close', (code, signal) => done(signal ? 128 : code));
    });
  } finally { closeSync(log); closeSync(err); }
  writeFileSync(resolve(request.reviewDirectory, 'process-exit.txt'), `${exit}\n`);
  const events = readFileSync(resolve(request.reviewDirectory, 'opencode.jsonl'), 'utf8').trim().split('\n').map(line => JSON.parse(line));
  const last = events.at(-1);
  const finished = last?.type === 'step_finish' && last.part?.reason === 'stop'
    && !events.some(e => e.type === 'error' || e.type === 'tool_use' && ['pending', 'running'].includes(e.part?.state?.status));
  const unchanged = command(['git', 'rev-parse', 'HEAD'], request.reviewWorktree) === request.candidateSha
    && command(['git', 'status', '--porcelain'], request.reviewWorktree) === ''
    && command(['git', 'rev-parse', 'HEAD'], root) === request.candidateSha
    && command(['git', 'status', '--porcelain'], root) === '';
  const verdict = JSON.parse(readFileSync(resolve(request.reviewDirectory, 'verdict.json'), 'utf8'));
  const report = readFileSync(resolve(request.reviewDirectory, 'review.md'), 'utf8');
  const result = validateVerdict(request, verdict, report, { exit, finished, unchanged });
  // Technical validation never substitutes for reading the raw gates and the review.
  writeFileSync(resolve(request.reviewDirectory, 'validation.json'), JSON.stringify({ exit, finished, unchanged, verdict: result, openCodeVersion: version }, null, 2) + '\n');
  console.log(`${request.phase} ${request.candidateSha}: ${result}; inspect ${request.reviewDirectory}`);
  if (result !== 'PASS') process.exitCode = 1;
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.length !== 4) throw new Error('Usage: node scripts/review-phase.mjs <request.json> <absolute-opencode-binary>');
  await runReview(resolve(process.argv[2]), process.argv[3]);
}
