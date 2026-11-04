import fs from 'node:fs';
import path from 'node:path';
import {spawn, spawnSync} from 'node:child_process';

const evidence = '/Users/stefan/.cache/gretl-tooling-review/P4-review-1/evidence';
const jar = path.join(evidence, 'artifacts/gretl-lsp/target/gretl-lsp-6.0.0-SNAPSHOT-jar-with-dependencies.jar');
const work = fs.mkdtempSync(path.join(evidence, 'cp-workspace-folders-'));
const root = path.join(work, 'workspace');
const job = path.join(root, 'job.groovy');
const regularFile = path.join(work, 'not-a-directory');
fs.mkdirSync(root);
fs.writeFileSync(job, "action('a') {}\n");
fs.writeFileSync(regularFile, 'not a workspace folder\n');

const log = [];
function note(...parts) {
  const line = parts.join(' ');
  log.push(line);
  console.log(line);
}

const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
const version = spawnSync(java, ['-version'], {encoding: 'utf8'});
if (version.status !== 0) {
  note('JAVA_UNAVAILABLE', version.stderr || version.error?.message || '');
  fs.writeFileSync(path.join(evidence, 'counterprobe-workspace-folders.log'), log.join('\n') + '\n');
  process.exit(2);
}
note('java', (version.stderr || '').split('\n')[0]);

const stderrPath = path.join(work, 'server-stderr.log');
const stderrStream = fs.createWriteStream(stderrPath, {flags: 'w'});
const child = spawn(java, ['-jar', jar, '--stdio'], {cwd: work, stdio: ['pipe', 'pipe', 'pipe']});
child.stderr.pipe(stderrStream);

let buffer = Buffer.alloc(0);
const messages = [];
const waiters = [];
child.stdout.on('data', chunk => {
  buffer = Buffer.concat([buffer, chunk]);
  pump();
});
function pump() {
  while (true) {
    const end = buffer.indexOf('\r\n\r\n');
    if (end < 0) return;
    const header = buffer.subarray(0, end).toString('ascii');
    const match = /Content-Length:\s*(\d+)/i.exec(header);
    if (!match) throw new Error('missing Content-Length in ' + JSON.stringify(header));
    const length = Number(match[1]);
    if (buffer.length < end + 4 + length) return;
    const body = buffer.subarray(end + 4, end + 4 + length);
    buffer = buffer.subarray(end + 4 + length);
    const message = JSON.parse(body.toString('utf8'));
    messages.push(message);
    for (const waiter of [...waiters]) {
      if (waiter.predicate(message)) {
        waiter.resolve(message);
        waiters.splice(waiters.indexOf(waiter), 1);
      }
    }
  }
}
function next(predicate, timeout = 15000) {
  const found = messages.find(predicate);
  if (found) return Promise.resolve(found);
  return new Promise((resolve, reject) => {
    const waiter = {predicate, resolve};
    waiters.push(waiter);
    setTimeout(() => {
      const index = waiters.indexOf(waiter);
      if (index >= 0) { waiters.splice(index, 1); reject(new Error('timeout waiting for message')); }
    }, timeout);
  });
}
function send(method, params, id = null) {
  const object = {jsonrpc: '2.0', method, params};
  if (id !== null) object.id = id;
  const bytes = Buffer.from(JSON.stringify(object), 'utf8');
  child.stdin.write(`Content-Length: ${bytes.length}\r\n\r\n`);
  child.stdin.write(bytes);
}
let nextId = 0;
async function request(method, params) {
  const id = ++nextId;
  send(method, params, id);
  const response = await next(message => message.id === id);
  return response;
}

const result = {
  probe: 'workspace-folder update atomicity',
  candidateJar: jar,
  expected: 'An invalid added workspace folder must leave the previously valid root intact (atomic rejection).',
  observed: null,
  pass: null,
  log: null,
  serverStderr: path.join(work, 'server-stderr.log'),
};

try {
  const init = await request('initialize', {
    rootUri: new URL('file://' + root).toString(),
    locale: 'de',
    capabilities: {workspace: {workspaceEdit: {documentChanges: true}}},
    initializationOptions: {trusted: true},
  });
  note('initialize', init.error ? JSON.stringify(init.error) : 'ok');
  send('initialized', {});
  send('textDocument/didOpen', {textDocument: {uri: new URL('file://' + job).toString(), languageId: 'gretl-runtime', version: 1, text: "action('a') {}\n"}});
  send('workspace/didChangeWorkspaceFolders', {event: {
    removed: [{uri: new URL('file://' + root).toString(), name: 'workspace'}],
    added: [{uri: new URL('file://' + regularFile).toString(), name: 'invalid'}],
  }});
  const logMessage = await next(message => message.method === 'window/logMessage' && message.params?.message === 'WORKSPACE_ROOT_UNSAFE');
  note('rejection-log', JSON.stringify(logMessage.params));
  const graph = await request('gretl/graph', {protocolVersion: 1, documentId: new URL('file://' + job).toString()});
  if (graph.error) {
    result.observed = `graph error ${graph.error.code}: ${graph.error.message}`;
  } else {
    result.observed = 'graph succeeded; valid root remained registered';
  }
  result.pass = !graph.error;
  note('graph-after-invalid-update', result.observed);
  const shutdown = await request('shutdown', {});
  note('shutdown', shutdown.error ? JSON.stringify(shutdown.error) : 'ok');
  send('exit', {});
  const exitCode = await new Promise(resolve => {
    const timer = setTimeout(() => { child.kill('SIGKILL'); resolve('timeout'); }, 8000);
    child.on('exit', code => { clearTimeout(timer); resolve(code); });
  });
  result.exitCode = exitCode;
  note('exit', String(exitCode));
} catch (error) {
  result.observed = 'probe error: ' + (error?.stack || error);
  result.pass = false;
  note(result.observed);
  child.kill('SIGTERM');
} finally {
  const logPath = path.join(evidence, 'counterprobe-workspace-folders.log');
  result.log = logPath;
  fs.writeFileSync(logPath, log.join('\n') + '\n');
  fs.writeFileSync(path.join(evidence, 'counterprobe-workspace-folders.json'), JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify(result, null, 2));
}
process.exit(result.pass ? 0 : 1);
