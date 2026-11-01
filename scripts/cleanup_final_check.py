#!/usr/bin/env python3
"""Deterministic P3 rest inventory and unchanged-test checks (Python 3.9+)."""
import argparse
import collections
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT=Path(__file__).resolve().parents[1]
MANIFEST='docs/maintenance/cleanup/rest-inventory.json'
BASE='27aa194f47412a9ac6f0a69dbc55f5a22adf12d6'
INITIAL='8eec03184883ce9d585d46a768b647f2b7a19169'
TOKENS=re.compile(r'\b[Pp]hase[ _-]*[0-9]+\w*\b|\b\w*(?:[Pp]robe|[Ff]ixture|[Ll]egacy|[Ss]elected|[Pp]hase|[Hh]ardening)\w*\b|\b[Mm][0-7]\b')
HISTORICAL=re.compile(r'\b(?:M[0-7]|[Hh]ardening)\b|\b[Pp]hase[ _-]*[0-9]+\w*\b|GretlM0Fixture|m0-points|gretl_m0_points')
COMMENTS={
 'examples/file-size-extension/src/main/java/example/ExampleProvider.java':
  'Historical origin comment on the real externally packaged FileSize operation example; class and API names are already neutral. Its exact source is part of the frozen P0 production-source oracle, so preserve it rather than cosmetically changing the SPI example. It is not a current M0 assignment.',
 'gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/FailureInfo.java':
  'Historical implementation-layer comment only: bounded throwable storage originated in M1; redaction and rendering belong to logging. It is not an open M3 assignment. Current contracts are docs/architecture/runtime-contracts.md. Preserve productive source bytes per INV-01/P3 scope.',
 'gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/RunEnvironment.java':
  'Historical layer-origin comment on the intentionally non-controlling EventSink boundary; no new telemetry task is authorized. Runtime event/logging contracts and regression tests remain authoritative. Preserve productive source bytes per P3 scope.',
 'gretl-interlis-tasks/src/main/java/ch/so/agi/gretl/internal/shapefile/core/ShapefileWriteOptions.java':
  'Historical migration-layer comment distinguishing format core from IOX adapter option parsing, not a future Phase 7 assignment. Package-private implementation is unchanged; public Db2Shp/Shp2Db and schemas must not be cosmetically refactored.'}


def sha(data):return hashlib.sha256(data).hexdigest()


def reason(path,line,token):
    # Keep a numbered historical phrase intact, and also inspect its context:
    # callers must never turn "Phase 7" into a generic execution-phase exception.
    if HISTORICAL.search(token) or ('phase' in token.lower() and HISTORICAL.search(line)):
        if path in COMMENTS and (line.lstrip().startswith(('*','//'))):return 'historical-source-comment',COMMENTS[path]
        raise RuntimeError('Unclassified historical marker: '+path+': '+line)
    low=token.lower()
    if re.fullmatch(r'm[23]',token):
        if path.endswith(('.ili','.xtf')):return 'physical-unit','INTERLIS area/volume unit (square/cubic metre), part of the retained third-party/customer model, not a milestone.'
        if '.m2' in line:return 'maven-cache','Maven local repository/cache path .m2; changing it would alter the supported packaging/resolution contract.'
        raise RuntimeError('Unclassified M-token: '+path+': '+line)
    if 'phase' in low:return 'execution-or-error-phase','Execution/setup/cleanup/logging or failure phase (including failurePhase and qualified review phases); semantic contract rather than an implementation milestone.'
    if 'probe' in low:
        if 'FunctionProbe' in line or 'FunctionProbe' in path:return 'validator-function-probe','Local EHI IOX function integration model, used for positive/negative extension resolution; a real function probe rather than a historical milestone.'
        if 'CsvReaderProbeTest' in path:return 'csv-library-compatibility','Real EHI CSV reader compatibility test; verifies delivered reader behavior and stays active in the normal test reactor.'
        if 'geotools' in path.lower():return 'optional-geotools-compatibility','Isolated GeoTools compatibility and negative CRS/classpath probe; remains optional and absent from the standard distribution.'
        return 'behavior-probe','Synthetic behavior or library-compatibility probe in the current test/example/documented execution path; no historical milestone claim.'
    if 'fixture' in low:return 'test-fixture','Current synthetic test input, service fixture, or fixture loader. Fixtures are necessary reproducible test data; retain assertions, licensing and original model bytes.'
    if 'legacy' in low:return 'compatibility-mode','Retained backward-compatible single-file/descriptor-schema-1 or older format execution path; schemas 1/2/3 and their negative tests are required by INV-02.'
    if 'selected' in low:return 'selection-contract','Actual task/operation/module/plugin selection or schema-2/3 package selection; preserve public field/method/DSL names and associated isolation tests.'
    raise RuntimeError('Unknown marker: '+path+': '+token)


def scope(path):
    if path.startswith(('docs/maintenance/cleanup/','docs/history/')):return 'protected-history-or-review-evidence'
    if path.endswith('.java') and '/src/main/java/' in path:return 'production-java' if path.startswith('gretl-') else 'example-extension-java'
    if path.endswith('.java') and '/src/test/java/' in path:return 'test-java'
    if path.endswith(('.ili','.xtf')):return 'interlis-or-xtf'
    if '/src/test/resources/' in path:return 'test-resource'
    if path.startswith('website/'):return 'website'
    if path.startswith('docs/') or path in ('README.md','AGENTS.md'):return 'documentation'
    if path.startswith(('examples/','gretl-dist/src/main/dist/')):return 'published-example-or-distribution'
    if path.endswith(('pom.xml','.properties')) or path in ('mvnw','mvnw.cmd'):return 'build-or-localization'
    return None


def scan(root):
    paths=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=root).decode().split('\0')[:-1]
    rows=[]
    for name in sorted(set(paths)):
        p=root/name;kind=scope(name)
        if not kind or name==MANIFEST or not p.is_file():continue
        data=p.read_bytes();matches=[]
        if kind!='protected-history-or-review-evidence' and re.search(r'M[0-7]',p.stem):
            if name in {'gretl-integration-tests/src/test/resources/jobs/formats/Av2geobau/DM01AVCH24LV95D.ili',
                        'gretl-integration-tests/src/test/resources/jobs/formats/Av2geobauFileSet/DM01AVCH24LV95D.ili'}:
                matches.append(dict(line=0,token=p.stem,context=p.name,classification='cadastral-model-identity',
                    reason='Official cadastral DM01 model identifier, not milestone M0. Customer/third-party model bytes and names are preserved and compared with the initial Git baseline.'))
            else:raise RuntimeError('Historical filename: '+name)
        try:contents=data.decode('utf-8') if b'\0' not in data else ''
        except UnicodeDecodeError:contents=data.decode('latin1') if p.suffix.lower() in ('.ili','.xtf') else ''
        if kind!='protected-history-or-review-evidence':
            for number,line in [(0,Path(name).name)]+list(enumerate(contents.splitlines(),1)):
                for token in sorted(set(m.group(0) for m in TOKENS.finditer(line))):
                    category,why=reason(name,line,token)
                    matches.append(dict(line=number,token=token,context=line,classification=category,reason=why))
        rows.append(dict(path=name,kind=kind,sha256=sha(data),bytes=len(data),
            disposition='RETAIN_IMMUTABLE_EVIDENCE' if kind=='protected-history-or-review-evidence' else 'KEEP_CURRENT',matches=matches))
    return dict(schemaVersion=1,initialBaselineSha=INITIAL,phaseBaseSha=BASE,
      selfReference=dict(path=MANIFEST,reason='The inventory cannot hash itself; its entire bytes are bound by the candidate Git tree. Future proof-only review pairs are independently verified by their provenance and source-text digest.'),
      interpretation='No production Java byte changes. Four historical source comments remain explicitly classified, not new work orders. Models with m2/m3 units and Maven .m2 caches are semantic. Protected specification, original baselines, license/provenance data and verbatim independent reviews are hashed and retained, not rewritten.',files=rows)


def test_preservation(root):
    prefix='gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/'
    old=prefix+'HardeningLocalJobIT.java';new=prefix+'RuntimeSafetyLocalJobIT.java'
    raw=subprocess.check_output(['git','show',BASE+':'+old],cwd=root)
    expected=raw.replace(b'HardeningLocalJobIT',b'RuntimeSafetyLocalJobIT').replace(b'hardening',b'runtime-safety')
    if (root/new).read_bytes()!=expected:raise RuntimeError('RuntimeSafety test differs beyond exact name substitutions')
    fixtures='gretl-integration-tests/src/test/resources/jobs/'
    for name in ('job.groovy','job-package.json'):
        raw=subprocess.check_output(['git','show',BASE+':'+fixtures+'hardening/'+name],cwd=root)
        if (root/fixtures/'runtime-safety'/name).read_bytes()!=raw.replace(b'hardening',b'runtime-safety'):
            raise RuntimeError('Safety fixture changed beyond exact name substitution')
    baseline=json.loads((root/'docs/maintenance/cleanup/inventory.json').read_text())
    for row in baseline['productionJava']:
        if sha((root/row['path']).read_bytes())!=row['sha256']:raise RuntimeError('Product Java mutation: '+row['path'])
    models=[p for p in subprocess.check_output(['git','ls-tree','-r','--name-only',INITIAL],cwd=root,text=True).splitlines() if p.endswith(('.ili','.xtf'))]
    for path in models:
        data=subprocess.check_output(['git','show',INITIAL+':'+path],cwd=root)
        target=path.replace('GretlM0Fixture','GretlValidationExample')
        if path.startswith('gretl-dist/src/main/dist/examples/ili-validator/'):
            data=data.replace(b'GretlM0Fixture',b'GretlValidationExample')
        if not (root/target).is_file() or (root/target).read_bytes()!=data:raise RuntimeError('Unexpected model/XTF mutation: '+path)
    # Every existing modified Java test retains at least all assertion sites.
    changes=subprocess.check_output(['git','diff','--name-only','--diff-filter=M',BASE],cwd=root,text=True).splitlines()
    assertions={}
    for path in changes:
        if '/src/test/java/' not in path or not path.endswith('.java'):continue
        original=subprocess.check_output(['git','show',BASE+':'+path],cwd=root,text=True)
        before=len(re.findall(r'\bassert\w*\s*\(',original));after=len(re.findall(r'\bassert\w*\s*\(',(root/path).read_text()))
        if after<before:raise RuntimeError('Removed assertion site: '+path)
        assertions[path]=dict(before=before,after=after)
    return dict(exactRuntimeSafetyRename=True,exactSafetyFixtureRenames=True,productionJavaUnchanged=len(baseline['productionJava']),
                modelAndXtfFilesCompared=len(models),onlyModelChange='GretlM0Fixture -> GretlValidationExample',modifiedTestAssertions=assertions)


def check(root):
    expected=json.loads((root/MANIFEST).read_text());actual=scan(root)
    # The only permissible post-review additions are technically validated proof
    # pairs. Their self-classification is checked against the proof-only commit.
    known={r['path'] for r in expected['files']}
    additions=[r for r in actual['files'] if r['path'] not in known]
    if additions:
        import cleanup_inventory
        classes=cleanup_inventory.verified_proof_classes({r['path'] for r in actual['files']})
        if any(r['path'] not in classes for r in additions):raise RuntimeError('Uninventoried current file')
        actual['files']=[r for r in actual['files'] if r['path'] in known]
    if actual!=expected:raise RuntimeError('Rest inventory stale: regenerate explicitly, inspect new markers and re-review candidate')
    historical={(r['path'],m['line']) for r in expected['files'] for m in r['matches'] if m['classification']=='historical-source-comment'}
    if {p for p,line in historical}!=set(COMMENTS) or len(historical)!=5:
        raise RuntimeError('Historical source-comment classification must cover exactly the four documented files and five lines')
    return dict(status='PASS',sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
      manifestSha256=sha((root/MANIFEST).read_bytes()),filesByKind=dict(collections.Counter(r['kind'] for r in expected['files'])),
      markerClassifications=dict(collections.Counter(m['classification'] for r in expected['files'] for m in r['matches'])),
      historicalSourceComments=dict(files=len({p for p,line in historical}),lines=len(historical),locations=[dict(path=p,line=line) for p,line in sorted(historical)]),
      testPreservation=test_preservation(root),postReviewProofFiles=[r['path'] for r in additions])


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--write',action='store_true');args=p.parse_args()
    if args.write:(ROOT/MANIFEST).write_text(json.dumps(scan(ROOT),indent=2,ensure_ascii=False)+'\n')
    print(json.dumps(check(ROOT),indent=2,ensure_ascii=False))
