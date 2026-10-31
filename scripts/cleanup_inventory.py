#!/usr/bin/env python3
"""Freeze cleanup baseline from Git; never refresh an existing inventory implicitly."""
import argparse
import collections
import hashlib
import json
import re
import subprocess
import sys
from urllib.parse import urlsplit, unquote
from pathlib import Path
from verify_cleanup_docs import documented, tracked, audit

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/maintenance/cleanup/inventory.json'


ASSESSMENT = ROOT / 'docs/maintenance/cleanup/content-assessment.json'


def content_assessments(baseline):
    """Reviewed per-file content decisions supplement the immutable first snapshot.

    No inference from directory names. Unknown files and stale evidence fail closed.
    P2 actions still have to recheck the actual consumers before removing anything.
    """
    value=json.loads(ASSESSMENT.read_text())
    if value.get('baselineSha') != baseline['initialBaselineSha']:
        raise SystemExit('Content assessment baseline mismatch')
    rows=value.get('documents',[])
    by_path={row['path']:row for row in rows}
    expected={row['path']:row for row in baseline['documents']}
    if len(rows)!=len(by_path) or set(by_path)!=set(expected):
        raise SystemExit('Content assessment missing, duplicate or unknown document')
    for name,row in by_path.items():
        original=expected[name]
        if row.get('classification') not in {'ACTIVE','ARCHIVE','DECISION','DELETE'}:
            raise SystemExit('Invalid content classification: '+name)
        if len(row.get('reason',''))<70 or row.get('plannedPhase') not in {'P0','P1','P2','P3'}:
            raise SystemExit('Missing substantive content decision: '+name)
        if row.get('baselineSha256')!=original['sha256'] or row.get('consumers')!=original['consumers']:
            raise SystemExit('Content assessment no longer binds frozen content/consumers: '+name)
        raw=subprocess.check_output(['git','show',baseline['initialBaselineSha']+':'+name],cwd=ROOT)
        if hashlib.sha256(raw).hexdigest()!=row['baselineSha256']:
            raise SystemExit('Baseline content hash mismatch: '+name)
        quotes=row.get('contentReferences',[])
        if not quotes and row.get('binaryEvidence')!={'bytes':len(raw),'sha256':row['baselineSha256']}:
            raise SystemExit('Missing content evidence: '+name)
        lines=raw.decode('utf-8',errors='replace').splitlines()
        for quote in quotes:
            number=quote.get('line')
            if type(number)!=int or not 1<=number<=len(lines) or not quote.get('text') or not lines[number-1].startswith(quote['text']):
                raise SystemExit('Unverifiable content citation: '+name)
    return by_path


def verify_documentation_index(baseline, original, paths):
    """Immutable baseline core; explicit additive index for later documentation.

    Updating introducedDocumentation is metadata maintenance, never a new source
    baseline. Original entries cannot be removed/reclassified. Every current
    document must be covered, including new files of the review tooling itself.
    """
    core=lambda value:{k:v for k,v in value.items() if k!='introducedDocumentation'}
    if core(baseline)!=core(original):
        raise SystemExit('Frozen P0 baseline core changed')
    rows=baseline.get('introducedDocumentation',[])
    introduced={r['path']:r for r in rows}
    if len(rows)!=len(introduced):raise SystemExit('Duplicate introduced documentation')
    old={r['path']:r for r in original.get('introducedDocumentation',[])}
    if any(introduced.get(name)!=row for name,row in old.items()):
        raise SystemExit('Original introduced-documentation record changed')
    known={r['path'] for r in baseline['documents']}
    current={p for p in paths if documented(p)}
    missing=current-known-set(introduced)
    if missing:raise SystemExit('Unclassified current documentation: '+str(sorted(missing)))
    for name,row in introduced.items():
        if name in known or not documented(name) or row.get('classification') not in {'ACTIVE','ARCHIVE','DECISION','DELETE'} or row.get('plannedPhase') not in {'P0','P1','P2','P3'}:
            raise SystemExit('Invalid introduced-documentation record: '+name)
        if len(row.get('reason',''))<(20 if name in old else 70):
            raise SystemExit('Missing introduced-documentation reason: '+name)
        # Records stay historical after a later documented deletion; phantom
        # entries are not permitted. Current paths must be tracked or staged.
        if name not in current and not subprocess.check_output(
                ['git','log','--format=%H','--diff-filter=A','HEAD','--',name],cwd=ROOT,text=True).strip():
            raise SystemExit('Introduced document has no current or historical source: '+name)
    return len(current)


def generate():
    raise SystemExit('Refusing to regenerate frozen baseline; use inventory.json and content-assessment.json')


def check():
    baseline=json.loads(OUT.read_text())
    paths=tracked(ROOT)
    first=subprocess.check_output(['git','log','--diff-filter=A','--format=%H','--',str(OUT.relative_to(ROOT))],cwd=ROOT,text=True).splitlines()
    if not first:raise SystemExit('Missing original P0 inventory commit')
    original=json.loads(subprocess.check_output(['git','show',first[-1]+':'+str(OUT.relative_to(ROOT))],cwd=ROOT))
    covered=verify_documentation_index(baseline,original,paths)
    assessments=content_assessments(baseline)
    current=sorted(set(m for p in paths if '/src/main/java/' in p and p.endswith('.java')
        and p.startswith(('gretl-io-tasks/','gretl-database-tasks/','gretl-interlis-tasks/','gretl-ilivalidator/'))
        for m in re.findall(r'"(gretl\.[a-z0-9-]+)"',(ROOT/p).read_text())))
    if current!=baseline['operationIds']: raise SystemExit('Public operation IDs changed')
    for entry in baseline['productionJava']:
        path=ROOT/entry['path']
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=entry['sha256']:
            raise SystemExit('Production source changed: '+entry['path'])
    descriptor=(ROOT/baseline['descriptorContract']).read_text()
    schemas=sorted(set(int(x) for x in re.findall(r'schemaVersion != ([0-9]+)',descriptor)))
    if schemas!=baseline['descriptorSchemas']: raise SystemExit('Descriptor acceptance changed')
    print(json.dumps({'operationIdsUnchanged':len(current),'descriptorSchemas':schemas,'productionJavaUnchanged':len(baseline['productionJava']),'currentDocumentationCovered':covered,'introducedDocumentation':len(baseline['introducedDocumentation']),'contentReviewedDocuments':len(assessments),'contentClasses':dict(collections.Counter(r['classification'] for r in assessments.values()))}))


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    check() if args.check else generate()
