#!/usr/bin/env python3
"""Audit files produced by the completed root verify; never builds or runs jobs."""
import argparse
import hashlib
import json
import re
from functools import lru_cache
from datetime import datetime, timezone
from pathlib import Path
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--full', action='store_true', help='Require every positive standard operation in the selected mode')
parser.add_argument('--mode', choices=['SCRIPT', 'JAR', 'IMAGE'], default='SCRIPT')
parser.add_argument('--scope', choices=['local', 'full'], default='local')
parser.add_argument('--run-id')
parser.add_argument('--evidence-root', type=Path, help='Isolated repository-shaped evidence copy')
parser.add_argument('--source', help='Require clean distribution from this commit (CI)')
args = parser.parse_args()
repository = Path(__file__).resolve().parents[1]
root = args.evidence_root.resolve() if args.evidence_root else repository
original_root = Path(json.loads((root / 'retention.json').read_text())['originalRoot']) if args.evidence_root else root
args.full = args.full or args.scope == 'full'
def instant_nanos(value):
    # Python 3.9 accepts only 3/6 fractional digits; Linux Instant uses up to nine.
    # Keep the original nanosecond precision for the lifetime comparison.
    match = re.fullmatch(r'(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})', value)
    assert match, 'Invalid timestamp: ' + value
    date = datetime.fromisoformat(match[1] + match[3].replace('Z', '+00:00'))
    delta = date - datetime(1970, 1, 1, tzinfo=timezone.utc)
    return (delta.days * 86400 + delta.seconds) * 1000000000 + int((match[2] or '').ljust(9, '0'))

def selected(row):
    return (args.full or row['scope'] == 'LOCAL') and (row['family'] != 'IMAGE_CONTRACT' or args.mode == 'IMAGE')

contracts = json.loads((repository / 'gretl-integration-tests/src/test/resources/qualification/scenarios.json').read_text())
expected_cases = {c['id']: c for c in contracts['testcases'] if selected(c)}
expected_scenarios = {s['id']: s for s in contracts['scenarios'] if selected(s)}
assert len(expected_cases) == len([c for c in contracts['testcases'] if selected(c)]), 'Duplicate expected test'
assert len(expected_scenarios) == len([s for s in contracts['scenarios'] if selected(s)]), 'Duplicate expected scenario'

# Fixed declarations, not a catalog reconstructed from successful executions.
for case in contracts['testcases']:
    starts = case['expectedStarts']
    assert isinstance(starts, int) and starts >= 0, 'Invalid declared start count: ' + case['id']
    if case['family'] in {'COMMON', 'SCRIPT_CONTRACT', 'IMAGE_CONTRACT'}:
        assert starts > 0, 'Process contract cannot silently become component-only: ' + case['id']
    assert sum(row['test'] == case['id'] for row in contracts['scenarios']) == starts, 'Reduced scenario contract: ' + case['id']

# The catalog is independent of results. Every declared source method must remain classified.
for source in (repository / 'gretl-integration-tests/src/test/java/ch/so/agi/gretl/it').glob('*.java'):
    for method in re.findall(r'@(?:Test|ParameterizedTest)\b(?:(?!\bvoid\b).)*?\bvoid\s+(\w+)\s*\(', source.read_text(), re.S):
        prefix = 'ch.so.agi.gretl.it.' + source.stem + '#' + method
        assert any(c['id'] == prefix or c['id'].startswith(prefix + '[') for c in contracts['testcases']), 'Unclassified source method: ' + prefix

# A process becomes qualified only through its successful JUnit invocation.
junit = {}
junit_run_ids = set()
for file in sorted((root / 'gretl-integration-tests/target').glob('*-reports/TEST-*.xml')):
    suite = ET.parse(file).getroot()
    properties = {p.get('name'):p.get('value') for p in suite.findall('properties/property')}
    if args.run_id: assert properties.get('gretl.test.runId') == args.run_id, 'Foreign JUnit run: ' + str(file)
    junit_run = properties.get('gretl.test.runId')
    assert junit_run and junit_run != 'development', 'Missing JUnit run identity: ' + str(file)
    junit_run_ids.add(junit_run)
    # Explicit matrix qualification requires the explicit candidate property. The ordinary
    # Maven entry point binds source through verified runtime/execution bytes and this run ID.
    if args.source and (args.run_id or properties.get('gretl.test.candidateSha')):
        assert properties.get('gretl.test.candidateSha') == args.source, 'Foreign JUnit candidate: ' + str(file)
    for case in suite.findall('testcase'):
        name = case.attrib['name']
        method = name.split('(', 1)[0]
        variant = re.search(r'\[(\d+)\]$', name)
        key = case.attrib['classname'] + '#' + method + ('[' + variant.group(1) + ']' if variant else '')
        assert key not in junit, 'Duplicate JUnit identity: ' + key
        assert not any(case.find(tag) is not None for tag in ('failure', 'error', 'skipped')), 'Unsuccessful JUnit: ' + key
        junit[key] = case
assert set(expected_cases) == set(junit), {'missingJUnit': sorted(set(expected_cases)-set(junit)), 'unclassifiedJUnit': sorted(set(junit)-set(expected_cases))}
assert len(junit_run_ids) == 1, 'Mixed JUnit suite runs'
seen = set()
run_ids = set()

def read(path):
    return json.loads(path.read_text())

@lru_cache(maxsize=None)
def sha(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()

@lru_cache(maxsize=None)
def verify_manifest(directory):
    files = {}
    for line in (directory / 'SHA256SUMS').read_text().splitlines():
        checksum, relative = line.split(maxsplit=1)
        relative = relative.lstrip('*')
        path = directory / relative
        assert not Path(relative).is_absolute() and '..' not in Path(relative).parts, relative
        assert relative not in files, 'Duplicate distribution entry'
        files[relative] = checksum
        assert path.is_file() and not path.is_symlink() and sha(path) == checksum, str(path)
    actual = {str(p.relative_to(directory)) for p in directory.rglob('*') if p.is_file() and p.name != 'SHA256SUMS'}
    assert not any(p.is_symlink() for p in directory.rglob('*')), 'Distribution symlink'
    assert actual == set(files), 'Distribution inventory mismatch'
    return files

dist = root / 'gretl-dist/target/dist'
catalog = read(dist / 'package-catalog.json')
if args.source:
    assert catalog['sourceRevision'] == args.source, catalog['sourceRevision']
    assert catalog['sourceDirty'] is False, 'Distribution was built from a dirty checkout'
for artifact in catalog['artifacts']:
    assert sha(dist / artifact['file']) == artifact['sha256'], artifact
verify_manifest(dist)

executions = []
positive = set()
for file in sorted(p for name in ('job-evidence', 'project-script-evidence') for p in (root / 'gretl-integration-tests/target' / name).rglob('execution.json')):
    row = read(file)
    scenario_id = row.get('scenarioId')
    assert scenario_id in expected_scenarios, 'Unexpected scenario: ' + str(scenario_id)
    assert scenario_id not in seen, 'Duplicate scenario: ' + scenario_id
    seen.add(scenario_id)
    contract = expected_scenarios[scenario_id]
    assert row['testCaseId'] == contract['test'] and row['testCaseId'] in junit, str(file)
    assert row['testFamily'] == contract['family'], str(file)
    expected_mode = args.mode if args.mode in contract['modes'] else contract['modes'][0]
    assert row['mode'] == expected_mode, (str(file), row['mode'], expected_mode)
    run_ids.add(row['suiteRunId'])
    assert row['suiteRunId'] in junit_run_ids, 'Process/JUnit run mismatch: ' + str(file)
    if args.run_id: assert row['suiteRunId'] == args.run_id, str(file)
    assert row['process']['state'] in contract['processStates'], (str(file), row['process'], contract)
    assert row['mode'] in {'JAR', 'SCRIPT', 'IMAGE'}, str(file)
    assert row['schemaVersion'] == 2 and row['process']['state'] != 'STARTED', str(file)
    if row['process']['state'] in {'PREPARE_FAILED', 'LAUNCH_FAILED'}:
        # Explicit harness negatives are not successful job executions.
        assert contract['report'] == 'ABSENT' and not (file.parent / 'reports/report.json').exists(), str(file)
        continue
    assert row['sourceRevision'] == catalog['sourceRevision'], str(file)
    assert row['sourceDirty'] == catalog['sourceDirty'], str(file)
    assert row['process']['forciblyKilled'] == contract.get('forciblyKilled', False), str(file)
    assert row['process']['exitCode'] == row['exitCode'], str(file)
    runtime = row['runtime']
    runtime_root = Path(runtime['root'])
    runtime_bytes = root / runtime_root.relative_to(original_root) if args.evidence_root and runtime_root.is_relative_to(original_root) else runtime_root
    if args.evidence_root and not runtime_root.is_relative_to(original_root): runtime_bytes = root / '__external_unretained_runtime__'
    if runtime_bytes.exists():
        assert sha(runtime_bytes / 'SHA256SUMS') == runtime['distributionManifestSha256'], str(file)
        verify_manifest(runtime_bytes)
    else:
        # The unpacked-ZIP contract deletes its private install after verifying it.
        # It has exactly the canonical manifest; derived extension installs remain available.
        assert runtime['distributionManifestSha256'] == sha(dist / 'SHA256SUMS'), str(file)
    command = read(file.parent / 'command.json')
    inventory = read(file.parent / 'input-manifest.json')
    assert inventory['treeSha256'] == row['input']['treeSha256'], str(file)
    canonical = ['gretl-input-v1', [[f['path'], f['size'], f['sha256']] for f in inventory['files']]]
    assert hashlib.sha256(json.dumps(canonical, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest() == inventory['treeSha256'], str(file)
    source = file.parent / 'source'
    source_identity = original_root / source.relative_to(root) if args.evidence_root else source
    if row['mode'] in {'SCRIPT', 'IMAGE'}:
        assert row['executionKind'] == 'SCRIPT' and 'jobJar' not in row, str(file)
        assert not (file.parent / 'job.jar').exists() and not (file.parent / 'package.stdout.log').exists(), str(file)
        for entry in inventory['files'] if source.exists() or not args.evidence_root else []:
            actual = source / entry['path']
            assert actual.resolve().is_relative_to(source.resolve()), str(file)
            assert actual.stat().st_size == entry['size'] and sha(actual) == entry['sha256'], str(file)
    if row['mode'] == 'SCRIPT':
        assert 'image' not in row, str(file)
        assert Path(command[0]) == runtime_root / 'bin/gretl' and command[1] in {'run', 'check', 'plan'}, str(file)
        assert Path(command[2]) == (source_identity / row['input']['relativePath']).resolve(), str(file)
    if row['mode'] == 'IMAGE':
        proof = read(file.parent / 'image-proof.json')
        candidate = read(root / 'gretl-dist/target/image/candidate.json')
        for key in ('id','sourceRevision','sourceDirty','distributionManifestSha256','os','architecture','baseReference','entrypoint','user','files'):
            assert proof[key] == candidate[key], 'IMAGE candidate metadata mismatch: ' + key
        expected_base = next(line.split()[1] for line in (repository / 'gretl-dist/src/main/docker/Dockerfile').read_text().splitlines() if line.startswith('FROM '))
        assert proof['baseReference'] == expected_base and re.search(r'@sha256:[a-f0-9]{64}$', expected_base), 'Wrong IMAGE base reference'
        extracted = root / 'gretl-dist/target/image/extracted'
        verify_manifest(extracted)
        assert sha(extracted / 'SHA256SUMS') == runtime['distributionManifestSha256'], 'Wrong actual image distribution bytes'
        assert {f['path']:f['sha256'] for f in proof['files']} == verify_manifest(dist), 'Wrong image file inventory'
        assert proof['id'] == candidate['id'] == row['image']['id'], 'Wrong IMAGE identity'
        assert proof['sourceRevision'] == catalog['sourceRevision'] and proof['sourceDirty'] == catalog['sourceDirty'], str(file)
        assert proof['distributionManifestSha256'] == runtime['distributionManifestSha256'], str(file)
        assert proof['os'] == row['image']['os'] == 'linux' and proof['architecture'] == row['image']['architecture'] == candidate['architecture'], str(file)
        assert proof['entrypoint'] == ['/opt/gretl/bin/gretl'] and proof['user'] == '10001:0', str(file)
        assert command[:2] == ['docker','run'] and '--entrypoint' not in command and '-jar' not in command and 'package' not in command, 'IMAGE is not external SCRIPT'
        index = command.index(proof['id'])
        assert command[index+1] in {'run','check','plan'} and command[index+2] == str(Path('/job') / row['input']['relativePath']), str(file)
        assert '--read-only' in command and '--pull=never' in command, str(file)
        mounts = [command[i+1] for i,x in enumerate(command) if x == '--mount']
        assert any('source='+str(source_identity)+',target=/job,readonly' in m for m in mounts), 'Source mount is not read-only'
        container = proof['container']
        assert container['id'] == (file.parent/'container.id').read_text().strip(), str(file)
        assert re.fullmatch('[a-f0-9]{64}',container['id']) and container['image'] == proof['id'], str(file)
        assert container['running'] is False and proof['removed'] is True and container['oomKilled'] is False, str(file)
        assert container['exitCode'] == row['exitCode'] and container['readOnlyRoot'] is True, str(file)
        assert container['user'] == command[command.index('--user')+1] and not container['user'].startswith('0:'), str(file)

    if row['mode'] == 'JAR':
        assert row['executionKind'] == 'PRECOMPILED_JOB' and '-jar' in command, str(file)
        jar = Path(row['jobJar']['path'])
        if args.evidence_root or not jar.exists():
            jar = file.parent / Path(row['jobJar']['path']).name  # actual changed or detached execution artifact
        assert sha(jar) == row['sha256'] == row['jobJar']['sha256'], str(jar)
        assert command[command.index('-jar') + 1] == row['jobJar']['path'], str(file)
        assert (file.parent / 'package-inventory.json').is_file(), str(file.parent)
    report_path = Path(row['report'])
    retained_report = file.parent / ('explicit-reports' if report_path.parent.name == 'explicit-reports' else 'reports') / report_path.name
    if args.evidence_root:
        report_path = root / report_path.relative_to(original_root) if report_path.is_relative_to(original_root) else retained_report
    if not args.evidence_root and not report_path.exists() and (file.parent / 'reports/report.json').exists():
        report_path = file.parent / 'reports/report.json'  # detached execution archive
    reports = [report_path] if report_path.is_file() else []
    if contract['report'] == 'OPTIONAL_FORCED_TERMINATION':
        assert contract.get('forciblyKilled') is True and row['process']['forciblyKilled'] is True, str(file)
    else: assert bool(reports) == (contract['report'] == 'REQUIRED'), (str(file), contract['report'])
    operations = []
    if reports:
        report = read(reports[0])
        identity = row['reportIdentity']
        assert sha(report_path) == identity['sha256'], 'Report bytes changed: ' + str(file)
        assert report['identity']['runId'] == identity['runId'] and report['identity']['commandId'] == identity['commandId'], str(file)
        assert instant_nanos(row['process']['startedAt']) <= instant_nanos(report['execution']['startedAt']) <= instant_nanos(report['execution']['finishedAt']) <= instant_nanos(row['process']['finishedAt']), 'Report outside process lifetime: ' + str(file)
        event = file.parent / 'reports' / (identity['runId'] + '.events.jsonl')
        assert event.is_file(), 'Missing matching events: ' + str(file)
        events = [json.loads(line) for line in event.read_text().splitlines() if line.strip()]
        assert events and all(e.get('run_id', identity['runId']) == identity['runId'] for e in events), 'Foreign events: ' + str(file)
        assert sum(e.get('event_name') == 'gretl.run.finished' for e in events) == 1, 'Missing or duplicate run completion: ' + str(file)
        row['reportExitCode'] = report['execution']['exitCode']
        if not row['aborted'] and not row['timedOut']:
            assert row['reportExitCode'] == row['exitCode'], str(file)
        operations = [{k: invocation[k] for k in ('operationId', 'status')}
                      for invocation in report['execution'].get('invocations', [])]
        if row['mode'] == args.mode and not row['process']['forciblyKilled']:
            positive.update(i['operationId'] for i in operations if i['status'] == 'SUCCEEDED')
    row['directory'] = str(file.parent.relative_to(root))
    row['operations'] = operations
    executions.append(row)
assert seen == set(expected_scenarios), {'missingScenarios': sorted(set(expected_scenarios)-seen)}
assert len(run_ids) == 1, 'Mixed suite runs'
assert executions and any(row['mode'] == args.mode for row in executions), 'No selected-mode job executions'
expected = {o['id'] for o in read(repository / 'docs/history/contracts/operation-baseline.json')}
if args.full:
    assert expected <= positive, {'missingPositiveOperations': sorted(expected - positive), 'mode': args.mode}

namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
modules = [m.text for m in ET.parse(root / 'pom.xml').getroot().findall('m:modules/m:module', namespace)]
if args.full:
    modules.append('gretl-geotools-probe')
tests = dict(tests=0, failures=0, errors=0, skipped=0)
for file in sorted(p for module in modules for p in (root / module / 'target').glob('*-reports/TEST-*.xml')):
    suite = ET.parse(file).getroot()
    assert not any(suite.findall('.//' + tag) for tag in ('failure', 'error', 'skipped')), str(file)
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
               tests=tests, mode=args.mode, requiredPositiveOperations=sorted(expected & positive), suiteRunId=next(iter(run_ids)), scenarioCount=len(seen), testCaseCount=len(junit), scenarioCatalogSha256=sha(repository / 'gretl-integration-tests/src/test/resources/qualification/scenarios.json'), successfulOperations=sorted(positive), full=args.full,
               distributionCatalogSha256=sha(dist / 'package-catalog.json'),
               archives=[dict(file=p.name, sha256=sha(p)) for p in sorted((root / 'gretl-dist/target').glob('*-bin.*'))],
               coverage=coverage, coverageLimit='Separate launcher and java -jar processes are not instrumented')
(output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps({k: v for k, v in summary.items() if k != 'coverage'}, indent=2))
