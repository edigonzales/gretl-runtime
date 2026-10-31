#!/usr/bin/env python3
"""Verify P2 curation against Git source bytes and measure current documentation."""
import collections
import hashlib
import json
from pathlib import Path
import re
import subprocess
import cleanup_inventory as inventory
from verify_cleanup_docs import documented,tracked

ROOT=Path(__file__).resolve().parents[1]


def sha(data):return hashlib.sha256(data).hexdigest()


def check(root):
    actions=json.loads((root/'docs/maintenance/cleanup/cleanup-actions.json').read_text())
    baseline=json.loads((root/'docs/maintenance/cleanup/inventory.json').read_text())
    assessment=json.loads((root/'docs/maintenance/cleanup/content-assessment.json').read_text())
    rows=actions['actions'];by={r['previousPath']:r for r in rows}
    if len(by)!=len(rows) or not {r['path'] for r in baseline['documents']}<=set(by):raise RuntimeError('Incomplete or duplicate P2 decisions')
    moves={}
    for row in rows:
        source=row['previousPath'];data=subprocess.check_output(['git','show',row['sourceCommit']+':'+source],cwd=root)
        if sha(data)!=row['sourceSha256'] or len(data)!=row['bytesBefore']:raise RuntimeError('Unbound curation source: '+source)
        target=root/row['replacementPath'].split('#')[0]
        original=root/source;action=row['action']
        if not row['reason'] or not target.is_file():raise RuntimeError('Missing rationale/replacement: '+source)
        if action=='MOVE':
            if original.exists() or not target.is_file() or sha(target.read_bytes())!=sha(data):raise RuntimeError('Changed moved evidence/oracle: '+source)
            moves[str(target.relative_to(root))]=sha(data)
        elif action=='DELETE':
            if original.exists():raise RuntimeError('Historical source still active: '+source)
            if row.get('extractedContentSha256'):
                body=re.sub(r'^(#{1,5}) ',r'\1# ',data.decode().split('\n',1)[1],flags=re.M)
                if sha(body.encode())!=row['extractedContentSha256'] or body not in target.read_text():raise RuntimeError('Lost consolidated contract: '+source)
        elif action=='KEEP':
            if not original.is_file() or sha(original.read_bytes())!=sha(data):raise RuntimeError('Undocumented rewrite: '+source)
        elif action!='REWRITE':raise RuntimeError('Unknown curation action')
    if (root/'docs/spec').exists() and any((root/'docs/spec').rglob('AGENTS.md')):raise RuntimeError('Historical agent orders remain active')
    baseline_role={r['path']:r['classification'] for r in assessment['documents']}
    role=dict(baseline_role)
    introduced={r['path']:r for r in baseline['introducedDocumentation']}
    role.update({p:r['classification'] for p,r in introduced.items()})
    paths=set(tracked(root))|set(introduced)
    role.update({p:r['classification'] for p,r in inventory.verified_proof_classes(paths).items()})
    def bucket(name,kind):
        return 'maintenance' if name.startswith('docs/maintenance/cleanup/') else ('product' if kind=='ACTIVE' else 'historical_or_decision')
    before=collections.defaultdict(lambda:dict(files=0,bytes=0));after=collections.defaultdict(lambda:dict(files=0,bytes=0))
    for r in baseline['documents']:
        key=bucket(r['path'],baseline_role.get(r['path'],r['classification']));before[key]['files']+=1;before[key]['bytes']+=r['bytes']
    current=[]
    for name in sorted(p for p in paths if documented(p) and (root/p).is_file()):
        if name not in role:raise RuntimeError('Unclassified current documentation: '+name)
        key=bucket(name,role[name]);after[key]['files']+=1;after[key]['bytes']+=(root/name).stat().st_size
        current.append(dict(path=name,classification=role[name],bytes=(root/name).stat().st_size,sha256=sha((root/name).read_bytes())))
    return dict(phase='P2',sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
                actions=dict(collections.Counter(r['action'] for r in rows)),unchangedMovedFiles=moves,
                baselineSha=baseline['initialBaselineSha'],before=dict(before),after=dict(after),currentDocumentation=current,
                interpretation='Product ACTIVE files are measured separately from required new maintenance inventories/review evidence; all categories remain visible.')


if __name__=='__main__':print(json.dumps(check(ROOT),indent=2,ensure_ascii=False))
