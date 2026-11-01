#!/usr/bin/env python3
"""Audit files produced by the completed root verify; never builds or runs jobs."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--full', action='store_true', help='Require every positive standard operation in the selected mode')
parser.add_argument('--mode', choices=['SCRIPT', 'JAR'], default='SCRIPT')
parser.add_argument('--source', help='Require clean distribution from this commit (CI)')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]

def read(path):
    return json.loads(path.read_text())

def sha(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()

dist = root / 'gretl-dist/target/dist'
catalog = read(dist / 'package-catalog.json')
if args.source:
    assert catalog['sourceRevision'] == args.source, catalog['sourceRevision']
    assert catalog['sourceDirty'] is False, 'Distribution was built from a dirty checkout'
for artifact in catalog['artifacts']:
    assert sha(dist / artifact['file']) == artifact['sha256'], artifact
for line in (dist / 'SHA256SUMS').read_text().splitlines():
    checksum, file = line.split(maxsplit=1)
    assert sha(dist / file.lstrip('*')) == checksum, file

executions = []
positive = set()
for file in sorted((root / 'gretl-integration-tests/target/job-evidence').glob('*/execution.json')):
    row = read(file)
    assert row['mode'] in {'JAR', 'SCRIPT'}, 'IMAGE requires its real qualification gate'
    assert row['schemaVersion'] == 2 and row['process']['state'] != 'STARTED', str(file)
    if row['process']['state'] in {'PREPARE_FAILED', 'LAUNCH_FAILED'}:
        # Explicit harness negatives are not successful job executions.
        assert row['scenario'] == 'source-contract', str(file)
        continue
    assert row['sourceRevision'] == catalog['sourceRevision'], str(file)
    assert row['sourceDirty'] == catalog['sourceDirty'], str(file)
    assert not row['process']['forciblyKilled'], str(file)
    assert row['process']['exitCode'] == row['exitCode'], str(file)
    runtime = row['runtime']
    runtime_root = Path(runtime['root'])
    if runtime_root.exists():
        assert sha(runtime_root / 'SHA256SUMS') == runtime['distributionManifestSha256'], str(file)
        for line in (runtime_root / 'SHA256SUMS').read_text().splitlines():
            checksum, relative = line.split(maxsplit=1)
            assert sha(runtime_root / relative) == checksum, str(file)
    else:
        # The unpacked-ZIP contract deletes its private install after verifying it.
        # It has exactly the canonical manifest; derived extension installs remain available.
        assert runtime['distributionManifestSha256'] == sha(dist / 'SHA256SUMS'), str(file)
    command = read(file.parent / 'command.json')
    inventory = read(file.parent / 'input-manifest.json')
    assert inventory['treeSha256'] == row['input']['treeSha256'], str(file)
    canonical = ['gretl-input-v1', [[f['path'], f['size'], f['sha256']] for f in inventory['files']]]
    assert hashlib.sha256(json.dumps(canonical, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest() == inventory['treeSha256'], str(file)
    if row['mode'] == 'SCRIPT':
        assert row['executionKind'] == 'SCRIPT' and 'jobJar' not in row and 'image' not in row, str(file)
        assert Path(command[0]) == runtime_root / 'bin/gretl' and command[1] in {'run', 'check', 'plan'}, str(file)
        source = file.parent / 'source'
        assert Path(command[2]) == (source / row['input']['relativePath']).resolve(), str(file)
        assert not (file.parent / 'job.jar').exists() and not (file.parent / 'package.stdout.log').exists(), str(file)
        for entry in inventory['files']:
            actual = source / entry['path']
            assert actual.resolve().is_relative_to(source.resolve()), str(file)
            assert actual.stat().st_size == entry['size'] and sha(actual) == entry['sha256'], str(file)

    if row['mode'] == 'JAR':
        assert row['executionKind'] == 'PRECOMPILED_JOB' and '-jar' in command, str(file)
        jar = Path(row['jobJar']['path'])
        if not jar.exists():
            jar = file.parent / 'job.jar'  # retained byte-identical detached execution copy
        assert sha(jar) == row['sha256'] == row['jobJar']['sha256'], str(jar)
        assert command[command.index('-jar') + 1] == row['jobJar']['path'], str(file)
        assert (file.parent / 'package-inventory.json').is_file(), str(file.parent)
    report_path = Path(row['report'])
    if not report_path.exists() and (file.parent / 'reports/report.json').exists():
        report_path = file.parent / 'reports/report.json'  # detached execution archive
    reports = [report_path] if report_path.is_file() else []
    operations = []
    if reports:
        report = read(reports[0])
        row['reportExitCode'] = report['execution']['exitCode']
        if not row['aborted'] and not row['timedOut']:
            assert row['reportExitCode'] == row['exitCode'], str(file)
        operations = [{k: invocation[k] for k in ('operationId', 'status')}
                      for invocation in report['execution'].get('invocations', [])]
        if row['mode'] == args.mode:
            positive.update(i['operationId'] for i in operations if i['status'] == 'SUCCEEDED')
    row['directory'] = str(file.parent.relative_to(root))
    row['operations'] = operations
    executions.append(row)
assert executions and any(row['mode'] == args.mode for row in executions), 'No selected-mode job executions'
expected = {o['id'] for o in read(root / 'docs/history/contracts/operation-baseline.json')}
if args.full:
    assert expected <= positive, sorted(expected - positive)

namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
modules = [m.text for m in ET.parse(root / 'pom.xml').getroot().findall('m:modules/m:module', namespace)]
if args.full:
    modules.append('gretl-geotools-probe')
tests = dict(tests=0, failures=0, errors=0, skipped=0)
for file in sorted(p for module in modules for p in (root / module / 'target').glob('*-reports/TEST-*.xml')):
    suite = ET.parse(file).getroot()
    for key in tests:
        tests[key] += int(suite.get(key, '0'))
assert tests['tests'] and not tests['failures'] and not tests['errors'] and not tests['skipped'], tests
coverage = []
for file in sorted(root / module / 'target/site/jacoco/jacoco.xml' for module in modules):
    if not file.is_file():
        continue
    report = ET.parse(file).getroot()
    coverage.append(dict(module=file.parts[-5], counters=[dict(c.attrib) for c in report.findall('counter')]))
assert coverage, 'No measured coverage'
output = root / 'gretl-integration-tests/target/qualification'
output.mkdir(exist_ok=True)
(output / 'executions.json').write_text(json.dumps(executions, indent=2) + '\n')
summary = dict(sourceRevision=catalog['sourceRevision'], sourceDirty=catalog['sourceDirty'],
               tests=tests, mode=args.mode, successfulOperations=sorted(positive), full=args.full,
               distributionCatalogSha256=sha(dist / 'package-catalog.json'),
               archives=[dict(file=p.name, sha256=sha(p)) for p in sorted((root / 'gretl-dist/target').glob('*-bin.*'))],
               coverage=coverage, coverageLimit='Separate launcher and java -jar processes are not instrumented')
(output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps({k: v for k, v in summary.items() if k != 'coverage'}, indent=2))
