#!/usr/bin/env python3
"""Run the documented synthetic finalizer examples and unchanged saved GUI sources."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--distribution', type=Path, required=True)
    parser.add_argument('--gui-fixture', type=Path, required=True)
    parser.add_argument('--gui-manifest', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    args = parser.parse_args()
    dist = args.distribution.resolve()
    evidence = args.evidence.resolve()
    evidence.mkdir(parents=True, exist_ok=False)
    launcher = dist / 'bin/gretl'
    assert launcher.is_file()
    commands = []

    def run(label, argv, cwd=ROOT, code=0):
        result = subprocess.run([str(a) for a in argv], cwd=cwd, capture_output=True, timeout=90)
        (evidence / (label + '.stdout.log')).write_bytes(result.stdout)
        (evidence / (label + '.stderr.log')).write_bytes(result.stderr)
        commands.append(dict(label=label, argv=[str(a) for a in argv], cwd=str(cwd), exit_code=result.returncode))
        (evidence / 'commands.json').write_text(json.dumps(commands, indent=2) + '\n')
        assert result.returncode == code, (label, result.returncode, result.stderr.decode(errors='replace'))
        return result

    def config(label):
        path = evidence / (label + '-config.json')
        path.write_text(json.dumps(dict(schemaVersion=1, runtime=dict(workRoot=str(evidence / (label + '-work-root')), reportRoot=str(evidence / (label + '-reports'))), secrets={}, taskConfig={}), indent=2))
        return path

    def report(label):
        return evidence / (label + '.json')

    def execution(label):
        return json.loads(report(label).read_text())['execution']

    def task(execution, name):
        return next(t for t in execution['tasks'] if t['taskId'] == name)

    def verify_demo(label, mode):
        out = evidence / (label + '-output')
        e = execution(label)
        assert e['status'] == ('SUCCEEDED' if mode == 'success' else 'FAILED')
        expected = ['importData', 'secondOwner', 'prepareFinalizer', 'release', 'record', 'cleanup']
        if mode == 'fail':
            expected.remove('secondOwner')
            assert task(e, 'secondOwner')['status'] == 'NOT_RUN'
            assert task(e, 'secondOwner')['reason'] == 'FAIL_FAST'
            errors = json.loads(report(label).read_text())['errors']
            assert next(error for error in errors if error['id'] == e['primaryErrorRef'])['message'] == 'synthetic-owner-failure'
        else:
            assert not e.get('primaryErrorRef')
        assert (out / 'trace.txt').read_text().splitlines() == expected
        assert gzip.decompress((out / 'finalized.gz').read_bytes()) == (ROOT / 'examples/task-finalizers/input.txt').read_bytes()
        assert (out / 'owner-closed.txt').read_text() == 'closed'
        assert (out / 'record.txt').read_text() == 'task-bound'
        assert (out / 'cleanup.txt').read_text() == 'global'
        for name in ('prepareFinalizer', 'release', 'record'):
            assert task(e, name)['status'] == 'SUCCEEDED'
        f = task(e, 'release')['finalization']
        assert f['declaredOwners'] == ['importData', 'secondOwner']
        assert f['activatingOwners'] == (['importData', 'secondOwner'] if mode == 'success' else ['importData'])
        assert f['executedRoles'] == ['FINALIZER']
        assert task(e, 'prepareFinalizer')['finalization']['executedRoles'] == ['FINALIZER_PREREQUISITE']
        assert sum(t['taskId'] == 'release' for t in e['tasks']) == 1

    # Commands documented for both supported working directories, with isolated runtime storage.
    for context, cwd in [('checkout', ROOT), ('distribution', dist)]:
        source = Path('examples/task-finalizers/job.groovy')
        for command in ('check', 'plan'):
            run(context + '-' + command, [launcher, command, source], cwd)
        for mode in ('success', 'fail'):
            label = context + '-' + mode
            run(label, [launcher, 'run', source, '--param', 'output=' + str(evidence / (label + '-output')), '--param', 'mode=' + mode, '--report', report(label), '--work-dir', evidence / (label + '-work'), '--config', config(label)], cwd, 0 if mode == 'success' else 10)
            verify_demo(label, mode)

    # The README's real package/launch path. Source-free JAR boundaries are also qualified by FinalizerJobIT.
    jar = evidence / 'job.jar'
    run('package', [launcher, 'package', ROOT / 'examples/task-finalizers/job-package.json', '--output', jar])
    label = 'jar-success'
    run(label, ['java', '-jar', jar, '--param', 'output=' + str(evidence / (label + '-output')), '--report', report(label), '--work-dir', evidence / (label + '-work'), '--config', config(label), '--keep-work', 'always'], evidence)
    verify_demo(label, 'success')

    # Syntax-check the actual explanatory Markdown snippet, rather than a second handwritten copy.
    guide = (ROOT / 'docs/implementation/task-finalizers/README.md').read_text()
    snippet = guide.split('```groovy\n', 1)[1].split('```', 1)[0]
    snippet_file = evidence / 'guide-snippet.groovy'
    snippet_file.write_text(snippet)
    run('guide-snippet-check', [launcher, 'check', snippet_file])

    fixture = args.gui_fixture.resolve()
    manifest = json.loads(args.gui_manifest.read_text())
    assert {str(p.relative_to(fixture)) for p in fixture.rglob('*') if p.is_file()} == set(manifest)
    assert all(sha(fixture / name) == digest for name, digest in manifest.items())
    staged = evidence / 'gui-input'
    shutil.copytree(fixture, staged)
    # Runtime input provisioning is separate from Groovy source edits. In the GUI fixture,
    # the saved data bytes are under parts/; file('input.txt') uses the single-job root.
    assert not (staged / 'input.txt').exists()
    shutil.copyfile(staged / 'parts/input.txt', staged / 'input.txt')
    provision = dict(source='parts/input.txt', runtimeResource='input.txt', sha256=sha(staged / 'input.txt'), reason="file('input.txt') resolves at the single-job root; no Groovy byte is changed")
    (evidence / 'gui-input-provisioning.json').write_text(json.dumps(provision, indent=2) + '\n')
    for name, digest in manifest.items():
        assert sha(staged / name) == digest
    label = 'saved-gui'
    run(label, [launcher, 'run', staged / 'job.groovy', '--report', report(label), '--work-dir', evidence / (label + '-work'), '--config', config(label), '--keep-work', 'always'], evidence)
    e = execution(label)
    assert e['status'] == 'SUCCEEDED' and not e.get('primaryErrorRef')
    assert [(t['taskId'], t['status']) for t in e['tasks']] == [('owner', 'SUCCEEDED'), ('closed', 'SUCCEEDED')]
    assert task(e, 'owner')['finalization']['executedRoles'] == ['NORMAL']
    assert task(e, 'closed')['finalization']['activatingOwners'] == ['owner']
    assert task(e, 'closed')['finalization']['executedRoles'] == ['FINALIZER']
    data = json.loads(report(label).read_text())
    events = [json.loads(line) for line in Path(data['logging']['eventFilePath']).read_text().splitlines()]
    assert [v['attributes']['cleanup.name'] for v in events if v['event_name'] == 'gretl.cleanup.finished'] == ['end']
    assert not any(v['event_name'] == 'gretl.cleanup.failed' for v in events)
    finished = next(i for i, v in enumerate(events) if v['event_name'] == 'gretl.task.finished' and v.get('task_id') == 'closed')
    cleanup = next(i for i, v in enumerate(events) if v['event_name'] == 'gretl.cleanup.started')
    assert finished < cleanup
    for name in ('owner.gz', 'finalized.gz'):
        outputs = list((evidence / (label + '-work')).rglob(name))
        assert len(outputs) == 1, (name, outputs)
        assert gzip.decompress(outputs[0].read_bytes()) == (fixture / 'parts/input.txt').read_bytes()
    assert all(sha(staged / name) == digest and sha(fixture / name) == digest for name, digest in manifest.items())
    (evidence / 'summary.json').write_text(json.dumps(dict(result='PASS', sourceCommit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(), sourceDirty=bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip()), guiManifest=str(args.gui_manifest.resolve()), guiSources=manifest, guiInputProvisioning=provision, processes=len(commands), jarSha256=sha(jar), distributionCatalogSha256=sha(dist / 'package-catalog.json'), distributionSource=json.loads((dist / 'package-catalog.json').read_text())['sourceRevision']), indent=2) + '\n')
    print('PASS checkout/distribution success+failure, JAR, guide syntax and unchanged saved GUI SCRIPT')

if __name__ == '__main__':
    main()
