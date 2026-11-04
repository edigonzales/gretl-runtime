import fs from 'node:fs';
import path from 'node:path';
import { spawn, spawnSync } from 'node:child_process';

const evidence = '/Users/stefan/.cache/gretl-tooling-review/P4-review-2/evidence';
const jar = path.join(evidence, 'artifacts/gretl-lsp/target/gretl-lsp-6.0.0-SNAPSHOT-jar-with-dependencies.jar');
const work = fs.mkdtempSync(path.join(evidence, 'cp-inflight-invalid-root-'));
const root = path.join(work, 'workspace');
const job = path.join(root, 'job.groovy');
const invalid = path.join(work, 'not-a-directory');
const log = [];
const result = {
  probe: 'in-flight query across an atomically rejected mixed workspace-folder update',
  candidateJar: jar,
  expected: 'The invalid added folder must be rejected before the valid root is removed; a concurrent graph response must not replace the retained plan/snapshot, and shutdown/exit must end the process with stdin still open.',
  observed: null,
  pass: null,
  exitCode: null,
  serverStderr: path.join(work, 'server-stderr.log'),
  logPath: path.join(evidence, 'own-counterprobe-inflight-invalid-root.log'),
  resultPath: path.join(evidence, 'own-counterprobe-inflight-invalid-root.json'),
};
function note(...parts) {
  const line = parts.join(' ');
  log.push(line);
  console.log(line);
}
function finish() {
  try {
    fs.writeFileSync(result.logPath, log.join('\n') + '\n');
    fs.writeFileSync(result.resultPath, JSON.stringify(result, null, 2) + '\n');
  } catch (error) {
    console.error('failed to write evidence', error);
  }
}
function launcherJava() {
  const javaHome = process.env.JAVA_HOME;
  if (javaHome) return path.join(javaHome, 'bin', 'java');
  const which = spawnSync('which', ['java'], { encoding: 'utf8' });
  if (which.status === 0) return which.stdout.trim();
  return 'java';
}

fs.mkdirSync(root);
fs.writeFileSync(invalid, 'not a workspace folder\n');
const source = [
  "parameter('arg',description:'A',defaultValue:'x')",
  "task('p', HttpDownload) { url='https://example.invalid/a'; output=workPath('a.zip') }",
  "task('c', Extract, dependsOn:'p') { archive=result('p').file; outputDirectory=workPath('out') }",
  ...Array.from({ length: 300 }, (_, i) => `action('a${i}') {}`),
  "defaults 'c'",
  '',
].join('\r\n');
fs.writeFileSync(job, source);
const rootUri = new URL('file://' + root).toString();
const jobUri = new URL('file://' + job).toString();
note('fixture', JSON.stringify({ rootUri, jobBytes: Buffer.byteLength(source), actions: 300 }));

const java = launcherJava();
const version = spawnSync(java, ['-version'], { encoding: 'utf8' });
note('java', (version.stderr || version.stdout || '').split('\n').find(Boolean) || java);
if (version.status !== 0) {
  result.observed = 'java -version failed';
  result.pass = false;
  finish();
  process.exit(2);
}

const child = spawn(java, ['-jar', jar, '--stdio'], {
  cwd: work,
  stdio: ['pipe', 'pipe', 'pipe'],
});
const stderrStream = fs.createWriteStream(result.serverStderr, { flags: 'w' });
child.stderr.pipe(stderrStream);
let buffer = Buffer.alloc(0);
const messages = [];
const waiters = [];
function pump() {
  while (true) {
    const end = buffer.indexOf('\r\n\r\n');
    if (end < 0) return;
    const header = buffer.subarray(0, end).toString('ascii');
    const match = /Content-Length:\s*(\d+)/i.exec(header);
    if (!match) throw new Error('missing Content-Length: ' + JSON.stringify(header));
    const length = Number(match[1]);
    if (buffer.length < end + 4 + length) return;
    const body = buffer.subarray(end + 4, end + 4 + length);
    buffer = buffer.subarray(end + 4 + length);
    let message;
    try {
      message = JSON.parse(body.toString('utf8'));
    } catch (error) {
      throw new Error('non-JSON stdout frame: ' + error.message);
    }
    messages.push(message);
    for (const waiter of [...waiters]) {
      if (waiter.predicate(message)) {
        waiter.resolve(message);
        waiters.splice(waiters.indexOf(waiter), 1);
      }
    }
  }
}
child.stdout.on('data', (chunk) => {
  buffer = Buffer.concat([buffer, chunk]);
  try { pump(); } catch (error) { for (const waiter of waiters.splice(0)) waiter.reject(error); }
});
function next(predicate, timeout = 30_000) {
  const found = messages.find(predicate);
  if (found) return Promise.resolve(found);
  return new Promise((resolve, reject) => {
    let timer;
    const waiter = {
      predicate,
      resolve: (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      reject,
    };
    waiters.push(waiter);
    timer = setTimeout(() => {
      const index = waiters.indexOf(waiter);
      if (index >= 0) waiters.splice(index, 1);
      reject(new Error('timeout waiting for wire message'));
    }, timeout);
  });
}
function send(method, params, id = null) {
  const object = { jsonrpc: '2.0', method, params };
  if (id !== null) object.id = id;
  const bytes = Buffer.from(JSON.stringify(object), 'utf8');
  child.stdin.write(`Content-Length: ${bytes.length}\r\n\r\n`);
  child.stdin.write(bytes);
}
let nextId = 0;
async function request(method, params) {
  const id = ++nextId;
  send(method, params, id);
  const response = await next((message) => message.id === id);
  if (response.error) throw new Error(`${method} error ${response.error.code}: ${response.error.message}`);
  return response.result;
}
function waitLog(message) {
  return next((item) => item.method === 'window/logMessage' && item.params?.message === message);
}
function waitExit(timeout = 10_000) {
  return new Promise((resolve) => {
    if (!child.exitCode && child.signalCode === null) {
      const timer = setTimeout(() => resolve('timeout'), timeout);
      child.once('exit', (code, signal) => {
        clearTimeout(timer);
        resolve({ code, signal });
      });
    } else {
      resolve({ code: child.exitCode, signal: child.signalCode });
    }
  });
}

try {
  const init = await request('initialize', {
    rootUri,
    locale: 'de',
    capabilities: {
      workspace: { workspaceEdit: { documentChanges: true } },
      textDocument: { codeAction: { codeActionLiteralSupport: { codeActionKind: { valueSet: ['quickfix'] } } } },
    },
    initializationOptions: { trusted: true },
  });
  note('initialize', init.capabilities?.positionEncoding || 'ok');
  send('initialized', {});
  send('textDocument/didOpen', {
    textDocument: { uri: jobUri, languageId: 'gretl-runtime', version: 1, text: source },
  });

  const graph = await request('gretl/graph', { protocolVersion: 1, documentId: jobUri });
  const snapshot = graph.context.snapshotId;
  note('initial-snapshot', snapshot);
  const plan = await request('gretl/planEdit', {
    protocolVersion: 1,
    documentId: jobUri,
    expectedSnapshotId: snapshot,
    requireOpenDocuments: true,
    intent: { kind: 'RenameSymbol', target: { namespace: 'TASK', qualifiedName: 'p' }, name: 'producer' },
  });
  note('retained-plan', plan.planId);

  // Start a query against the existing snapshot, then immediately send the mixed
  // removal/invalid-addition event. The event must fail before mutating ownership.
  const inFlightId = ++nextId;
  send('gretl/graph', { protocolVersion: 1, documentId: jobUri, expectedSnapshotId: snapshot }, inFlightId);
  send('workspace/didChangeWorkspaceFolders', {
    event: {
      removed: [{ uri: rootUri, name: 'root' }],
      added: [{ uri: new URL('file://' + invalid).toString(), name: 'invalid' }],
    },
  });
  const rejection = await waitLog('WORKSPACE_ROOT_UNSAFE');
  note('rejection', rejection.params.message);

  const inFlight = await next((message) => message.id === inFlightId);
  if (inFlight.error) note('in-flight-graph', `rejected ${inFlight.error.code}: ${inFlight.error.message}`);
  else {
    const actual = inFlight.result.context.snapshotId;
    note('in-flight-graph', 'completed with snapshot ' + actual);
    if (actual !== snapshot) throw new Error('invalid folder update changed snapshot identity');
  }

  const after = await request('gretl/graph', { protocolVersion: 1, documentId: jobUri });
  if (after.context.snapshotId !== snapshot) throw new Error('invalid folder update changed the retained snapshot');
  const validation = await request('gretl/validateEdit', {
    protocolVersion: 1,
    planId: plan.planId,
    expectedSnapshotId: snapshot,
  });
  if (!validation.current) throw new Error('invalid folder update invalidated the retained plan');
  note('post-rejection', 'same snapshot and plan retained');

  // Shutdown and exit while stdin remains open. The launcher must terminate the
  // actual child and complete the transport cleanup.
  const shutdown = await request('shutdown', {});
  void shutdown;
  send('exit', {});
  const exit = await waitExit();
  result.exitCode = exit;
  note('exit', JSON.stringify(exit));
  if (typeof exit !== 'object' || exit.code !== 0) throw new Error('server did not exit 0 with stdin open');
  result.observed = 'Invalid mixed folder update was rejected before mutation; concurrent graph did not replace the retained snapshot/plan; shutdown/exit completed with stdin open.';
  result.pass = true;
} catch (error) {
  result.observed = 'probe error: ' + (error?.stack || error);
  result.pass = false;
  note(result.observed);
  child.kill('SIGTERM');
  const exit = await waitExit(3000);
  if (result.exitCode === null) result.exitCode = exit;
} finally {
  if (child.exitCode === null && child.signalCode === null) {
    child.kill('SIGTERM');
    await waitExit(3000);
  }
  finish();
  console.log(JSON.stringify(result, null, 2));
}
process.exit(result.pass ? 0 : 1);
