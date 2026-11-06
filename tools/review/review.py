#!/usr/bin/env python3
"""Run and validate an independent OpenCode review of a committed candidate."""
import argparse
import hashlib
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re
import shlex
import signal
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MODEL = 'deepseek/deepseek-flash'
ENDPOINT = 'https://api.deepseek.com'
MAPPING = 'https://api-docs.deepseek.com/quick_start/pricing/'
AGENT = 'independent-review'
IDENTITY = ('assignment_id', 'spec_version', 'phase', 'assignment_base_sha', 'phase_base_sha',
            'candidate_sha', 'requirements_digest', 'review_worktree', 'opencode_version', 'provider_model')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def output(argv, cwd=ROOT, env=None):
    return subprocess.check_output(argv, cwd=cwd, env=env, text=True, timeout=60).strip()


def git(cwd, *args):
    return output(['git', *args], cwd)


def absolute(value):
    path = Path(value)
    require(path.is_absolute() and path.resolve() == path, f'Expected canonical absolute path: {path}')
    return path


def external(value, repo):
    path = absolute(value)
    require(not path.is_relative_to(repo) and not repo.is_relative_to(path), f'Expected external path: {path}')
    return path


def load_request(path, repo):
    path = external(str(path), repo)
    request = read(path)
    for key in ('assignment_id', 'spec_version', 'phase', 'checkpoint'):
        require(isinstance(request[key], str) and request[key].strip(), f'Missing {key}')
    for key in ('assignment_base_sha', 'phase_base_sha', 'candidate_sha'):
        value = request[key]
        require(re.fullmatch(r'[0-9a-f]{40}', value) and git(repo, 'rev-parse', value+'^{commit}') == value,
                f'Invalid {key}')
    head = request['candidate_sha']
    for ancestor in ('assignment_base_sha', 'phase_base_sha'):
        subprocess.run(['git', 'merge-base', '--is-ancestor', request[ancestor], head], cwd=repo, check=True)
    require(git(repo, 'rev-parse', 'HEAD') == head and not git(repo, 'status', '--porcelain=v1'),
            'Commit product changes and select the candidate first')
    evidence = external(request['evidence_directory'], repo)
    worktree = external(request['review_worktree'], repo)
    requirements = external(request['requirements_directory'], repo)
    require(all(not a.is_relative_to(b) for a, b in
                ((evidence, worktree), (worktree, evidence), (evidence, requirements), (worktree, requirements))),
            'Use separate evidence, worktree and requirement paths')
    criteria = request['criteria']
    require(criteria and all(isinstance(x, str) and x.strip() for x in criteria) and
            len(set(criteria)) == len(criteria), 'Expected unique criterion IDs')
    checks = request['required_checks']
    require(checks and len({check['id'] for check in checks}) == len(checks), 'Expected unique required checks')
    commands = []
    for check in checks:
        argv = check['argv']
        require(isinstance(argv, list) and argv and all(isinstance(v, str) and v and '\n' not in v for v in argv),
                f'Invalid check argv: {check["id"]}')
        command = shlex.join(argv)
        require(check.get('expected_exit') == 0 and command not in commands, 'Expected unique successful check')
        require(not any(re.search(r'(?:^|[.-])skip(?:ITs|Tests|tests)(?:$|=true)', v) for v in argv),
                'A test skip is not a review check')
        if argv[0] == './mvnw':
            require(check.get('reports'), 'Maven review checks need fresh XML selectors')
        commands.append(command)
    for value in request.get('read_paths', []):
        require(external(value, repo).exists(), f'Missing additional read path: {value}')
    return request


def capture_requirements(request):
    directory = absolute(request['requirements_directory'])
    names = request['requirement_files']
    require(names and len(set(names)) == len(names), 'Expected distinct requirement files')
    rows = []
    for name in sorted(names):
        relative = Path(name)
        path = (directory / relative).resolve()
        require(not relative.is_absolute() and '..' not in relative.parts and path.is_relative_to(directory)
                and path.is_file(), f'Invalid requirement file: {name}')
        rows.append(f'{sha(path.read_bytes())}  {name}\n')
    listing = ''.join(rows).encode()
    require(sha(listing) == request['requirements_digest'], 'Requirement digest mismatch')
    return listing


def verify_model_mapping(document, expected):
    class Table(HTMLParser):
        def __init__(self):
            super().__init__()
            self.rows = []
            self.row = []
            self.cell = None

        def handle_starttag(self, tag, attrs):
            if tag == 'tr':
                self.row = []
            elif tag in ('td', 'th'):
                self.cell = []

        def handle_data(self, data):
            if self.cell is not None:
                self.cell.append(data)

        def handle_endtag(self, tag):
            if tag in ('td', 'th') and self.cell is not None:
                self.row.append(' '.join(''.join(self.cell).split()))
                self.cell = None
            if tag == 'tr' and self.row:
                self.rows.append(self.row)

    table = Table()
    table.feed(document)
    column, versions = None, []
    for row in table.rows:
        if row[0] == 'MODEL':
            matches = [i for i, cell in enumerate(row) if re.sub(r'\(\d+\)$', '', cell) == MODEL.split('/')[1]]
            column = matches[0] if len(matches) == 1 else None
        elif row[0] == 'MODEL VERSION' and column is not None and len(row) > column:
            versions.append(row[column])
            column = None
    require(versions == [expected], 'Official model alias is changed or ambiguous')


def inspect_cli(binary, worktree, evidence, request):
    version = output([binary, '--version'], worktree)
    require(re.fullmatch(r'opencode v2\.\d+\.\d+', version), 'OpenCode 2.x is required')
    help_text = output([binary, 'run', '--help'], worktree)
    require(all(flag in help_text for flag in ('--standalone', '--model', '--format', '--agent', '--file')),
            'Missing OpenCode 2.x run options')
    (evidence/'run-help.txt').write_text(help_text + '\n')
    auth = json.loads(output([binary, 'auth', 'list', '--format', 'json'], worktree))
    require(any(provider['id'] == 'deepseek' and provider.get('connections') for provider in auth),
            'The stored direct DeepSeek connection is unavailable')
    require(MODEL in output([binary, 'models'], worktree).splitlines(), 'Required model alias is unavailable')
    req = urllib.request.Request(MAPPING, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req, timeout=30) as response:
        require(response.geturl().startswith('https://api-docs.deepseek.com/'), 'Unexpected model mapping host')
        mapping = response.read(2 * 1024 * 1024 + 1)
    require(len(mapping) <= 2 * 1024 * 1024, 'Oversized model mapping page')
    verify_model_mapping(mapping.decode('utf-8'), request['checkpoint'])
    (evidence/'model-mapping.html').write_bytes(mapping)
    return version


def tracked_digest(worktree):
    manifest = subprocess.check_output(['git', 'ls-files', '-z', '--stage'], cwd=worktree)
    digest = hashlib.sha256(manifest)
    for entry in manifest.split(b'\0'):
        if entry:
            name = os.fsdecode(entry.split(b'\t', 1)[1])
            path = worktree/name
            content = os.fsencode(os.readlink(path)) if path.is_symlink() else path.read_bytes()
            digest.update(hashlib.sha256(content).digest())
    return digest.hexdigest()


def prepare_review_worktree(repo, worktree, candidate):
    require(not worktree.exists(), 'Review worktree must be fresh')
    subprocess.run(['git', 'worktree', 'add', '--detach', worktree, candidate], cwd=repo, check=True)
    require(not git(worktree, 'status', '--porcelain=v1'), 'Review worktree is not clean')
    return tracked_digest(worktree)


def policy(request):
    evidence = request['evidence_directory']
    rules = [dict(action='*', resource='*', effect='deny')]
    def allow(action, resource):
        rules.append(dict(action=action, resource=resource, effect='allow'))
    for action in ('read', 'glob', 'grep'):
        allow(action, '*')
    for path in [evidence, request['requirements_directory'], *request.get('read_paths', [])]:
        allow('external_directory', path + '/*')
    allow('edit', evidence + '/*')
    assignment = request['assignment_base_sha']+'..'+request['candidate_sha']
    phase = request['phase_base_sha']+'..'+request['candidate_sha']
    for command in ('pwd', 'git status --porcelain=v1', 'git rev-parse HEAD', 'git ls-files',
                    'git ls-files -z', 'java -version', './mvnw -version',
                    *(f'git diff{option} {span}' for option in ('',' --name-status') for span in {assignment,phase}),
                    *(shlex.join(c['argv']) for c in request['required_checks']),
                    f'python3 {evidence}/*.py', f'node {evidence}/*.mjs'):
        allow('shell', command)
    for pattern in ('**/auth.json', '**/*credential*', '**/.env*', '.env*', '**/opencode.db*', '**/.git', '**/.git/*'):
        rules.append(dict(action='read', resource=pattern, effect='deny'))
    return dict(model=MODEL, providers={'deepseek': {'settings': {'baseURL': ENDPOINT},
                                                     'models': {'deepseek-flash': {'modelID': 'deepseek-flash'}}}},
                permissions=rules, agents={AGENT: dict(description='Independent candidate reviewer', mode='primary',
                    model=MODEL, permissions=rules, system='Review independently. Write only review evidence and '
                    'your own probes in the designated external evidence directory. No product or test edits, '
                    'secrets, changes to Git or other worktrees, or publication, including through probe scripts.') } )


def launch_review(binary, request, evidence, worktree, timeout):
    ignore = evidence/'review-infrastructure.ignore'
    ignore.write_text('/.opencode/\n')
    env = dict(os.environ, PWD=str(worktree), PYTHONDONTWRITEBYTECODE='1')
    slot = int(env.get('GIT_CONFIG_COUNT', '0'))
    env.update({'GIT_CONFIG_COUNT': str(slot+1), f'GIT_CONFIG_KEY_{slot}': 'core.excludesFile',
                f'GIT_CONFIG_VALUE_{slot}': str(ignore)})
    config = policy(request)
    (worktree/'.opencode').mkdir()
    write(worktree/'.opencode/opencode.json', config)
    write(evidence/'effective-policy.json', config)
    prompt = f"""Review the exact candidate independently. Read {evidence}/request.json and all external requirement_files.
Inspect the assignment and phase diffs. Execute every required argv as its own shell call, with no pipes, redirects or
additional commands. Use the exact command shown next to each argv. Inspect fresh raw reports. Invent and execute
a relevant new probe you write in {evidence}.
Save the original review.md and verdict.json there. In verdict.json copy identity fields {IDENTITY}; provide session_id,
verdict, criteria (id/status/evidence; status fulfilled if met), executed_checks and counterprobes (exact command as
executed, exit_code, assertion, evidence paths); each counterprobe includes authored_by_reviewer=true, executed=true,
hypothesis, input and expectation. Provide reused_evidence, findings, required_not_executed, and integrity. Cite fresh
files generated by the reported commands, or opencode.jsonl for checks with only stdout evidence.
Check the final git status. Finish the session. No product or test edits."""
    argv = [str(binary), 'run', '--standalone', '--agent', AGENT, '--model', MODEL, '--format', 'json',
            '--title', f'{request["assignment_id"]} {request["phase"]} review', '--file', str(evidence/'request.json'), prompt]
    process = dict(argv=argv, cwd=str(worktree), started=time.time())
    with (evidence/'opencode.jsonl').open('wb') as stdout, (evidence/'opencode.stderr.log').open('wb') as stderr:
        child = subprocess.Popen(argv, cwd=worktree, env=env, stdout=stdout, stderr=stderr, start_new_session=True)
        process['pid'] = child.pid
        write(evidence/'process.json', process)
        try:
            process['exit_code'] = child.wait(timeout=timeout)
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            os.killpg(child.pid, signal.SIGTERM)
            try:
                child.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(child.pid, signal.SIGKILL)
                child.wait()
            process.update(interrupted=True, exit_code=child.returncode)
        finally:
            process['ended'] = time.time()
            write(evidence/'process.json', process)
    require(process['exit_code'] == 0 and not process.get('interrupted'), 'Reviewer process did not complete')
    events = [json.loads(line) for line in (evidence/'opencode.jsonl').read_text().splitlines()]
    ids = {event['sessionID'] for event in events if 'sessionID' in event}
    require(len(ids) == 1 and not any(event['type']=='error' for event in events), 'Invalid review stream')
    with (evidence/'session-export.json').open('wb') as stream:
        subprocess.run([binary, 'session', 'export', ids.pop()], cwd=worktree, env=env, stdout=stream,
                       timeout=120, check=True)
    return process, events, read(evidence/'session-export.json')['info']


def validate_reports(worktree, patterns, started, ended):
    counts = {}
    for pattern in patterns:
        require(not Path(pattern).is_absolute() and '..' not in Path(pattern).parts, 'Invalid XML report selector')
        reports = sorted(worktree.glob(pattern))
        require(reports, f'Missing selected reports: {pattern}')
        for path in reports:
            require(path.resolve().is_relative_to(worktree) and started <= path.stat().st_mtime <= ended + 1,
                    f'Non-current report: {path}')
            suite = ET.parse(path).getroot()
            total = int(suite.attrib['tests'])
            require(total > 0 and len(suite.findall('testcase')) == total and
                    all(int(suite.attrib[k]) == 0 for k in ('errors', 'failures', 'skipped')) and
                    all(case.find(tag) is None for case in suite.findall('testcase') for tag in ('error','failure','skipped')),
                    f'Empty, failing or skipped tests: {path}')
            counts[str(path.relative_to(worktree))] = total
    return counts


def validate_verdict(request, verdict, process, events, session, evidence, worktree):
    for key in IDENTITY:
        require(verdict[key] == request[key], f'Verdict identity mismatch: {key}')
    require(process['exit_code'] == 0 and not process.get('interrupted') and process['cwd'] == str(worktree),
            'Invalid process completion')
    require(events and not any(event['type'] == 'error' for event in events)
            and events[-1]['type']=='step_finish' and events[-1]['part']['reason']=='stop',
            'No final stop event')
    sid = session['id']
    require({e['sessionID'] for e in events if 'sessionID' in e} == {sid} and verdict['session_id'] == sid and
            not session.get('parentID') and session['location']['directory'] == str(worktree) and
            process['started'] <= session['time']['created']/1000 <= process['ended'] and
            session.get('outcome') == 'succeeded' and session['time'].get('idle') and
            session['agent'] == AGENT and session['model']['providerID']+'/'+session['model']['id'] == MODEL,
            'Session identity or completion mismatch')
    require(verdict['verdict'] == 'ABGENOMMEN' and not verdict['required_not_executed'],
            'Independent review did not accept: '+verdict['verdict'])
    criteria = verdict['criteria']
    require(sorted(row['id'] for row in criteria) == sorted(request['criteria']) and
            all(row['status'] == 'fulfilled' and row['evidence'] for row in criteria),
            'Criteria missing, duplicated, unfulfilled or unsupported')
    require(not any(f['severity'] in ('BLOCKER','MAJOR') for f in verdict['findings']) and
            (evidence/'review.md').stat().st_size > 100 and verdict['integrity'],
            'Missing original report, integrity assessment or unresolved finding')
    tools = [event['part'] for event in events if event['type']=='tool_use' and
             event['part']['state']['status']=='completed']
    def completed(check):
        matches = [part['state'] for part in tools if part['tool']=='shell' and
                   part['state']['input']['command']==check['command'] and
                   part['state']['metadata']['metadata'].get('exit')==check['exit_code']]
        require(matches and check['assertion'] and check['evidence'], 'No matching real check: '+check['command'])
        state = matches[-1]
        for value in check['evidence']:
            path = (evidence/value).resolve()
            require((path.is_relative_to(evidence) or path.is_relative_to(worktree)) and path.is_file() and
                    path.stat().st_size > 0 and process['started'] <= path.stat().st_mtime <= process['ended']+1,
                    f'Missing or stale check evidence: {path}')
        return state
    checks = verdict['executed_checks']
    for check in checks:
        completed(check)
    results = {}
    for item in request['required_checks']:
        matches = [c for c in checks if c['command']==shlex.join(item['argv']) and c['exit_code']==item['expected_exit']]
        require(len(matches)==1, 'Missing or ambiguous check: '+item['id'])
        state = completed(matches[0])
        results[item['id']] = validate_reports(worktree, item.get('reports', []),
                                               state['time']['start']/1000, state['time']['end']/1000)
        for expression in item.get('output_matches', []):
            require(re.search(expression, state['output'], re.M), 'Missing expected output: '+item['id'])
    require(verdict['counterprobes'], 'No new independent counterprobe')
    for probe in verdict['counterprobes']:
        state = completed(probe)
        argv = shlex.split(probe['command'])
        require(len(argv) >= 2 and argv[0] in ('python3','node') and probe['exit_code']==0 and
                probe['executed'] is True and probe['authored_by_reviewer'] is True and
                all(probe.get(k) for k in ('hypothesis','input','expectation')), 'Invalid counterprobe')
        path = absolute(argv[1])
        require(path.is_relative_to(evidence) and path.is_file() and
                any(part['tool']=='write' and part['state']['input'].get('path')==str(path) and
                    part['state']['time']['end'] <= state['time']['start'] for part in tools),
                'No reviewer-authored probe before its execution')
    return dict(verdict='ABGENOMMEN', session_id=sid, tests=results,
                **{key:request[key] for key in IDENTITY})


def run_review(request_path, binary, timeout, repo=ROOT):
    request = load_request(request_path, repo)
    original = request_path.read_bytes()
    listing = capture_requirements(request)
    evidence, worktree = map(Path, (request['evidence_directory'], request['review_worktree']))
    evidence.mkdir(parents=True, mode=0o700)
    before = prepare_review_worktree(repo, worktree, request['candidate_sha'])
    version = inspect_cli(binary, worktree, evidence, request)
    request.update(opencode_version=version, provider_model=MODEL, endpoint=ENDPOINT)
    for check in request['required_checks']:
        check['command'] = shlex.join(check['argv'])
    (evidence/'requirements.sha256').write_bytes(listing)
    write(evidence/'request.json', request)
    request_snapshot = (evidence/'request.json').read_bytes()
    try:
        process, events, session = launch_review(binary, request, evidence, worktree, timeout)
    finally:
        require(git(worktree, 'rev-parse', 'HEAD') == request['candidate_sha'] and
                not git(worktree, 'status', '--porcelain=v1','--untracked-files=no') and
                tracked_digest(worktree) == before, 'Review candidate changed')
        require(capture_requirements(request) == listing and request_path.read_bytes() == original and
                (evidence/'request.json').read_bytes() == request_snapshot, 'Review requirements or request changed')
    report = validate_verdict(request, read(evidence/'verdict.json'), process, events, session, evidence, worktree)
    write(evidence/'validation.json', report)
    print(json.dumps(report, ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('request', type=Path)
    parser.add_argument('--opencode', required=True, type=Path, help='Absolute path of installed OpenCode 2.x CLI')
    parser.add_argument('--timeout', type=int, default=2400)
    args = parser.parse_args()
    try:
        run_review(absolute(str(args.request)), absolute(str(args.opencode)), args.timeout)
    except (ValueError, KeyError, OSError, ET.ParseError, subprocess.SubprocessError) as error:
        parser.exit(1, f'Review not accepted: {error}\n')


if __name__ == '__main__':
    main()
