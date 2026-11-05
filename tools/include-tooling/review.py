#!/usr/bin/env python3
"""Small include-tooling handoff/launcher. No product fixes or inherited old gates."""
import argparse
import datetime as dt
import hashlib
import json
import fnmatch
import shlex
import os
from pathlib import Path
import signal
import sys

if sys.flags.optimize:
    raise SystemExit("Optimized Python disables review assertions; refusing execution")
import subprocess as sp
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SPEC = Path('docs/implementation/include-tooling')
CRITERIA = {
    'P0': ['W01', 'W02', 'W03', 'W04'],
    'P1': ['IT01', 'IT02', 'IT03', 'IT04', 'IT06', 'IT23'],
    'P2': [f'IT{i:02}' for i in range(3, 11)] + ['IT15', 'IT21', 'IT23', 'IT24'],
    'P3': ['IT04', 'IT05', 'IT06'] + [f'IT{i:02}' for i in range(11, 17)] + ['IT22', 'IT23'],
    'P4': [f'IT{i:02}' for i in range(1, 25)],
}

def run(argv, cwd=ROOT, **kw):
    return sp.run(argv, cwd=cwd, check=True, text=True, **kw)

def output(argv, cwd=ROOT):
    return run(argv, cwd, stdout=sp.PIPE).stdout.strip()

def write(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')

def digest(root):
    return ''.join(hashlib.sha256((root / p).read_bytes()).hexdigest() + '  ' + str(p) + '\n'
                   for p in sorted(SPEC / f for f in ('CODEX_STARTPROMPT.md', 'EXECUTION_PLAN.md', 'OPENCODE_REVIEW_PROMPT.md')))

def launch(a):
    sha = output(['git', 'rev-parse', a.candidate + '^{commit}'])
    assert not output(['git', 'status', '--porcelain', '--untracked-files=no']), 'Commit tracked changes first'
    ev = a.evidence.resolve()
    assert not ev.exists(), 'Evidence must be new'
    assert not ev.is_relative_to(ROOT), 'External evidence required'
    ev.mkdir(parents=True)
    wt = ev.parent / (ev.name + '-product')
    assert not wt.exists()
    run(['git', 'worktree', 'add', '--detach', str(wt), sha])
    listing = digest(wt)
    (ev / 'requirements.sha256').write_text(listing)
    version = output([str(a.binary), '--version'])
    assert version.startswith('opencode v2.'), version
    run([str(a.binary), 'run', '--help'], stdout=(ev / 'run-help.txt').open('w'))
    # Only integration metadata, never credential contents.
    run([str(a.binary), 'auth', 'list', '--format', 'json'], stdout=(ev / 'auth-integrations.json').open('w'))
    run([str(a.binary), 'models'], stdout=(ev / 'models.txt').open('w'))
    assert 'deepseek/deepseek-flash' in (ev / 'models.txt').read_text().splitlines()
    import urllib.request
    with urllib.request.urlopen('https://api-docs.deepseek.com/quick_start/pricing/', timeout=30) as response:
        mapping = response.read()
    (ev / 'model-mapping.html').write_bytes(mapping)
    assert b'DeepSeek-V4.1-Flash-1017' in mapping and b'deepseek-flash' in mapping
    handoff = dict(assignment_id='include-tooling', spec_version='1.0', phase=a.phase,
                   assignment_base_sha=a.assignment_base, phase_base_sha=a.phase_base,
                   head_sha=sha, reviewed_sha=sha, review_worktree=str(wt),
                   requirements_digest=hashlib.sha256(listing.encode()).hexdigest(),
                   criteria=CRITERIA[a.phase], evidence_directory=str(ev),
                   implementer_evidence=str(a.implementer_evidence.resolve()),
                   opencode_version=version.removeprefix('opencode v'), provider_model='deepseek/deepseek-flash',
                   model_mapping_evidence=str(ev / 'model-mapping.html'), test_command=a.test_command,extra_test_commands=a.extra_test_command,reference_evidence=[str(p.resolve()) for p in a.reference_evidence])
    if a.phase=='P4':
        assert a.native_evidence and a.native_evidence.resolve().is_dir(), 'P4 needs installed native evidence'
        handoff['native_driver']=dict(kind='cua-file-bridge',requests=str(ev/'native-requests.jsonl'),responses=str(ev/'native-responses.jsonl'),
                                      evidence=str(a.native_evidence.resolve()),instructions=str(ev/'native-instructions.md'))
        (ev/'native-instructions.md').write_text(
            'Own native counterprobe required. Read installed identity and existing native evidence yourself. '
            'The orchestrator provides a mechanical CUA gateway for this one isolated Code test window. '
            'You choose the hypothesis, actions, observations and assertions yourself; the gateway supplies no verdict. '
            'No AppleScript, CDP, synthetic provider API or shell UI actions. Author an external Python probe that appends '
            'one JSON line to native-requests.jsonl and waits for its matching id in native-responses.jsonl. '
            'Request schema: {"id":"unique-short-id","actions":[{"kind":"pressKey","key":"super+shift+p"},'
            '{"kind":"paste","text":"GRETL: Select Source Context"}],"screenshot":true}. '
            'Other action kinds: click with target (observed AX index or [x,y]); typeText with text. '
            'At most eight deterministic actions per request; after each request inspect fresh AX state before choosing new indexes. '
            'An empty actions list requests initial observation. Responses name nonempty AX and optional PNG files, candidate and actual app path. '
            'Stay in the named isolated synthetic workspace/profile. No product fixes, foreign windows, terminal commands or publication. '
            'Inspect actual visible results and save your own assertion report. Mark the executed counterprobe native_user_path=true; '
            'include native-responses.jsonl among its evidence. Run your probe through shell with a sufficient bounded timeout, '
            'then finish all owned children and the review session.\n')
    write(ev / 'handoff.json', handoff)
    permissions = [dict(action='*', resource='*', effect='deny')]
    def allow(action, resource):
        permissions.append(dict(action=action, resource=resource, effect='allow'))
    for action in ('read', 'glob', 'grep'):
        allow(action, '*')
    for path in (ev, a.implementer_evidence.resolve(), a.maven_repo.resolve(), *(p.resolve() for p in a.reference_evidence), *([a.native_evidence.resolve()] if a.native_evidence else [])):
        allow('external_directory', str(path) + '/*')
    allow('edit', str(ev) + '/*')
    for cmd in ('pwd', 'git status *', 'git diff *', 'git show *', 'git rev-parse *', 'git log *',
                'git worktree list*', 'rg *', 'ls *', 'cat *', 'sed *', 'head *', 'tail *', 'wc *',
                'java -version', 'javac -version', './mvnw -version', a.test_command,
                f'python3 {ev}/*.py', f'node {ev}/*.mjs',
                f'javac -cp * -d {ev}/* {ev}/*.java', f'java -cp * *IndependentProbe*'):
        allow('shell', cmd)
    for cmd in a.extra_test_command:
        allow('shell', cmd)
    for pattern in ('*.env', '*.env.*', '*/auth.json', '*credential*'):
        permissions.append(dict(action='read', resource=pattern, effect='deny'))
    agent = dict(description='Independent include-tooling reviewer', mode='primary', permissions=permissions,
                 system='Read current include-tooling plan and review prompt. No product/test fixes, secrets, foreign worktrees or publication. '
                        'Run targeted tests and your own new counterprobe. Write reports/probes only in external evidence. '
                        'Do not ask user for ordinary review steps. No root full gates. Assess full phase against unchanged phase base.')
    config = dict(model=handoff['provider_model'], providers={'deepseek': {'settings': {'baseURL': 'https://api.deepseek.com'}}},
                  agents={'include-tooling-review': agent})
    (wt / '.opencode').mkdir(exist_ok=True)
    write(wt / '.opencode/opencode.json', config)
    write(ev / 'effective-policy.json', config)
    prompt = (f'Review {a.phase} at exact SHA {sha}. Read {ev}/handoff.json, requirements.sha256, '
              f'{SPEC}/EXECUTION_PLAN.md, OPENCODE_REVIEW_PROMPT.md and review/{a.phase}-request.md yourself. '
              f'Run test_command and all extra_test_commands in handoff yourself in {wt}; save real test logs and inspect fresh XMLs with >0 tests and zero skips/errors/failures. '
              f'Invent and execute your own relevant new counterprobe under {ev}; no implementer-supplied counterprobe. '
              f'Write ORIGINAL review.md and verdict.json under {ev}, following plan section 9.4 exactly. '
              'Copy handoff identity fields, supply actual session_id; criteria is a list of objects with id/status/evidence; '
              'executed_checks and counterprobes each need actual command, exit_code, assertion, evidence. '
              'Counterprobes also need authored_by_reviewer=true and executed=true. '
              'Read this adapter validate first: each reported command must exactly match a completed successful shell event. '
              'Run required Maven/npm commands directly through shell, not as inner subprocesses of a wrapper. '
              'Evidence paths must be nonempty regular files, never directories. '
              'No prefilled judgment. Give findings and required_not_executed honestly. '
              'Read current public behavior and full phase scope. Check tracked bytes before/after. '
              + (f'For P4 read {ev}/native-instructions.md and perform your own new native counterprobe via that CUA gateway. ' if a.phase=='P4' else '')
              + 'Finish the session.')
    argv = [str(a.binary), 'run', '--standalone', '--agent', 'include-tooling-review', '--model', handoff['provider_model'],
            '--format', 'json', '--file', str(ev / 'handoff.json'), prompt]
    meta = dict(argv=argv, cwd=str(wt), started_at=dt.datetime.now(dt.timezone.utc).isoformat())
    with (ev / 'opencode.jsonl').open('w') as out, (ev / 'opencode.stderr.log').open('w') as err:
        proc = sp.Popen(argv, cwd=wt, env={**os.environ, 'PWD': str(wt)}, stdout=out, stderr=err, start_new_session=True)
        meta['pid'] = proc.pid
        write(ev / 'process.json', meta)
        try:
            meta['exit_code'] = proc.wait(timeout=a.timeout)
        except sp.TimeoutExpired:
            meta['timed_out'] = True
            os.killpg(proc.pid, signal.SIGTERM)
            try:
                proc.wait(timeout=10)
            except sp.TimeoutExpired:
                os.killpg(proc.pid, signal.SIGKILL)
                proc.wait()
            meta['exit_code'] = proc.returncode
    meta['ended_at'] = dt.datetime.now(dt.timezone.utc).isoformat()
    meta['tracked_status'] = output(['git', 'status', '--porcelain', '--untracked-files=no'], wt)
    write(ev / 'process.json', meta)
    print(json.dumps(meta))

def validate_reports(wt, command, started_at):
    reports = list(wt.glob('*/target/surefire-reports/TEST-*.xml')) + list(wt.glob('*/target/failsafe-reports/TEST-*.xml'))
    assert reports, 'Missing test reports'
    names=[]
    for report in reports:
        attrs=ET.parse(report).getroot().attrib
        assert int(attrs['tests']) > 0 and all(int(attrs[k]) == 0 for k in ('failures','errors','skipped')), report
        assert report.stat().st_mtime >= dt.datetime.fromisoformat(started_at).timestamp(), 'Stale report: '+str(report)
        names.append(attrs['name'])
    patterns=[]
    for arg in shlex.split(command):
        for prefix in ('-Dtest=', '-Dit.test='):
            if arg.startswith(prefix):
                patterns.extend(p.split('#')[0] for p in arg[len(prefix):].split(','))
    assert patterns, 'Explicit target tests required'
    for pattern in patterns:
        assert any(fnmatch.fnmatchcase(n,pattern) or fnmatch.fnmatchcase(n.split('.')[-1],pattern) for n in names), 'Missing selected test: '+pattern


def validate(ev, binary):
    h = json.loads((ev / 'handoff.json').read_text())
    v = json.loads((ev / 'verdict.json').read_text())
    p = json.loads((ev / 'process.json').read_text())
    assert p['exit_code'] == 0 and p.get('ended_at') and not p.get('timed_out') and not p['tracked_status']
    wt = Path(h['review_worktree'])
    assert output(['git', 'rev-parse', 'HEAD'], wt) == h['head_sha']
    assert not output(['git', 'status', '--porcelain', '--untracked-files=no'], wt)
    for k in ('assignment_id', 'spec_version', 'phase', 'assignment_base_sha', 'phase_base_sha', 'head_sha', 'reviewed_sha',
              'review_worktree', 'requirements_digest', 'opencode_version', 'provider_model'):
        assert v[k] == h[k], k
    assert h['assignment_id'] == 'include-tooling' and h['spec_version'] == '1.0'
    assert digest(wt) == (ev / 'requirements.sha256').read_text()
    assert hashlib.sha256(digest(wt).encode()).hexdigest() == h['requirements_digest']
    assert sorted(x['id'] for x in v['criteria']) == sorted(CRITERIA[h['phase']])
    assert all(x['status'] == 'fulfilled' and x['evidence'] for x in v['criteria'])
    assert v['verdict'] == 'ABGENOMMEN' and not v['required_not_executed']
    assert not any(f['severity'] in ('MAJOR', 'BLOCKER') for f in v['findings'])
    assert (ev / 'review.md').stat().st_size > 100
    events = [json.loads(line) for line in (ev / 'opencode.jsonl').read_text().splitlines()]
    assert not any(e['type'] == 'error' for e in events)
    assert {e['sessionID'] for e in events if 'sessionID' in e} == {v['session_id']}
    # CLI output to a pipe may truncate large exports; a regular file drains fully.
    export = ev / 'session-export.json'
    with export.open('w') as stream:
        run([str(binary), 'session', 'export', v['session_id']], wt, stdout=stream)
    session = json.loads(export.read_text())
    info = session['info']
    assert info['id'] == v['session_id'] and info['location']['directory'] == str(wt)
    assert info.get('outcome') == 'succeeded' and info['time'].get('idle'), 'Reviewer session unfinished'
    assert info['model']['providerID'] + '/' + info['model']['id'] == h['provider_model']
    assert info['agent'] == 'include-tooling-review'
    states = [e['part']['state'] for e in events if e.get('part', {}).get('tool') == 'shell'
              and e['part']['state'].get('status') == 'completed']
    assert v['executed_checks'] and v['counterprobes']
    for required in [h['test_command']] + h.get('extra_test_commands', []):
        assert any(shlex.split(c['command']) == shlex.split(required) for c in v['executed_checks']), 'Missing required command: '+required
    for c in v['executed_checks'] + v['counterprobes']:
        assert c['exit_code'] == 0 and c['assertion'] and c['evidence']
        assert any(s.get('input', {}).get('command') == c['command'] and
                   s.get('metadata', {}).get('metadata', {}).get('exit') == 0 for s in states), c['command']
        for path in c['evidence']:
            file = Path(path) if Path(path).is_absolute() else ev / path
            assert file.is_file() and file.stat().st_size > 0
    assert all(c['authored_by_reviewer'] and c['executed'] for c in v['counterprobes'])
    if h['phase']=='P4':
        driver=h['native_driver'];receipts=Path(driver['responses'])
        identity=json.loads((Path(driver['evidence'])/'identity.json').read_text())
        assert identity['product']['sourceCommit']==h['head_sha'] and identity['product']['sourceDirty'] is False
        assert hashlib.sha256(Path(identity['product']['vsix']).read_bytes()).hexdigest()==identity['product']['sha256']
        observations=[json.loads(line) for line in receipts.read_text().splitlines()]
        assert observations and any(x['request'].get('actions') for x in observations), 'Missing independent native actions'
        assert all(x['candidate']==h['head_sha'] and x.get('ok') and x['app']==identity['executable'] and Path(x['state']).is_file() for x in observations)
        assert any(x.get('screenshot') and Path(x['screenshot']).stat().st_size>0 for x in observations), 'Missing native screenshot'
        assert any(c.get('native_user_path') and str(receipts) in c['evidence'] for c in v['counterprobes']), 'Missing native reviewer probe'
    validate_reports(wt, h['test_command'], p['started_at'])
    write(ev / 'validated.json', dict(accepted=True, candidate=h['head_sha'], phase=h['phase'], session=v['session_id']))
    print('ABGENOMMEN', h['phase'], h['head_sha'])

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=['run', 'validate'])
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--binary', type=Path, required=True)
    parser.add_argument('--phase', choices=CRITERIA)
    parser.add_argument('--candidate', default='HEAD')
    parser.add_argument('--assignment-base')
    parser.add_argument('--phase-base')
    parser.add_argument('--maven-repo', type=Path)
    parser.add_argument('--implementer-evidence', type=Path)
    parser.add_argument('--test-command')
    parser.add_argument('--extra-test-command', action='append', default=[])
    parser.add_argument('--native-evidence', type=Path)
    parser.add_argument('--reference-evidence', type=Path, action='append', default=[])
    parser.add_argument('--timeout', type=int, default=1200)
    args = parser.parse_args()
    if args.operation == 'run':
        assert all((args.phase, args.assignment_base, args.phase_base, args.maven_repo, args.implementer_evidence, args.test_command))
        launch(args)
    else:
        validate(args.evidence.resolve(), args.binary)
