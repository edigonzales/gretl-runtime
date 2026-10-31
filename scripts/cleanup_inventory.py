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


def classification(name, text, consumers):
    if '/licenses/' in name or 'provenance' in name or 'fixture-origins' in name or 'original-scenarios' in name:
        return 'ARCHIVE', 'P2', 'Provenienz bzw. Lizenz-/Originaltestnachweis; Herkunft und Nutzer vor gezielter Archivierung erhalten.'
    if name.endswith(('operation-inventory.json', 'local-models.json')):
        return 'ACTIVE', 'P2', 'Maschinenlesbarer Katalog mit Code-/Auditnutzern; vor Verschiebung alle Nutzer anpassen.'
    if name.startswith('docs/spec/'):
        if any(x in name for x in ['02-publisher', '05-publisher', '08-native', '11-quellen']):
            return 'DECISION', 'P2', 'Offene Zukunftsentscheidungen extrahieren und gegen tatsächlichen Code abgrenzen; keine Implementierung beauftragt.'
        return 'DELETE', 'P2', 'Historische Soll-Spezifikation/Agentenauftrag; gültige Verträge vor Entfernung gegen Source prüfen und aktuell dokumentieren.'
    if name.startswith(('docs/implementation/improvement-plan/', 'docs/implementation/hardening-f1-f4/', 'docs/implementation/project-subprojects/')) and not name.endswith('/README.md'):
        final = name.endswith(('phase-3-review.md', 'phase-b-review-revision-1.md', 'qualification.md'))
        return ('ARCHIVE' if final else 'DELETE'), 'P2', ('Finaler SHA-gebundener Abschlussnachweis; auf kurzen historischen Entscheidungsnachweis reduzieren.' if final else 'Abgeschlossener Plan bzw. Iterationsevidenz; Nutzerliste und Inhalt vor Entfernung prüfen, Git erhält Original.')
    if re.search(r'/m\d(?:-report|-user-acceptance)\.md$', name) or name.startswith('docs/migration/'):
        return 'ARCHIVE', 'P2', 'Historischer Meilenstein-/Migrationsnachweis, keine aktuelle Produktstatusquelle; SHA-Bezug bewahren.'
    return 'ACTIVE', ('P0' if '/maintenance/cleanup/' in name else 'P1'), 'Aktuelle Anleitung, Referenz, Build-/Website-Ressource oder publiziertes Beispiel; anhand implementierter Ausführung aktualisieren.'


def generate():
    if OUT.exists():
        raise SystemExit('Refusing to overwrite frozen baseline')
    paths = tracked(ROOT)
    texts = {}
    for name in paths:
        try:
            value = (ROOT / name).read_text(encoding='utf-8')
        except (UnicodeError, OSError):
            continue
        texts[name] = value
    link_rows=audit(ROOT)
    basenames=collections.Counter(Path(p).name for p in paths)
    link_consumers={}
    for row in link_rows:
        target=(ROOT/row['source']).parent/unquote(urlsplit(row['target']).path)
        try: resolved=str(target.resolve().relative_to(ROOT))
        except ValueError: continue
        link_consumers.setdefault(resolved,set()).add(row['source'])
    documents = []
    for name in sorted(p for p in paths if documented(p)):
        text = texts.get(name, '')
        consumers = sorted(link_consumers.get(name,set()) | {p for p, value in texts.items() if p != name and (name in value or (basenames[Path(name).name]==1 and Path(name).name in value))})
        kind, phase, reason = classification(name, text, consumers)
        data = (ROOT / name).read_bytes()
        documents.append(dict(path=name, classification=kind, reason=reason, plannedPhase=phase,
                              contentEvidence=[line[:300] for line in text.splitlines() if re.search(r'Status|Stand:|Phase|Spezifikation|[Ll]icen[sz]|[Hh]erkunft',line)][:3],
                              bytes=len(data), sha256=hashlib.sha256(data).hexdigest(), consumers=consumers,
                              contentHeadings=re.findall(r'^(?:#{1,6}|={1,5})\s+(.+)', text, re.M)[:25]))
    production = [p for p in paths if '/src/main/java/' in p and p.endswith('.java')]
    # Extract IDs from executable registration sources, independently of the old JSON inventory.
    operation_ids = sorted(set(m for p in production if any(p.startswith(x) for x in ['gretl-io-tasks/', 'gretl-database-tasks/', 'gretl-interlis-tasks/', 'gretl-ilivalidator/'])
                               for m in re.findall(r'"(gretl\.[a-z0-9-]+)"', texts.get(p, ''))))
    result = dict(schemaVersion=1, initialBaselineSha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
                  specification='docs/maintenance/cleanup/spec-v2.md',
                  baselineDeviation='Actual clean HEAD includes two specification commits after the analysed 37c6f115980ecabc9a5931afa4bffc9b353f51f2; product baseline unchanged.',
                  documents=documents, introducedDocumentation=[dict(path=p,classification='ACTIVE',plannedPhase='P0',reason=reason) for p,reason in [('docs/maintenance/cleanup/README.md','Aktuelle Anleitung des neuen Reviewverfahrens.'),('docs/maintenance/cleanup/inventory.json','Erzeugtes eingefrorenes Baselineinventar; kein rekursiver Eigenhash.')]], modules=re.findall(r'<module>(.*?)</module>',texts['pom.xml']),
                  productionJava=[dict(path=p,sha256=hashlib.sha256((ROOT/p).read_bytes()).hexdigest()) for p in sorted(production)],
                  operationIds=operation_ids,
                  descriptorSchemas=[1,2,3], descriptorContract='gretl-packager/src/main/java/ch/so/agi/gretl/packager/PackageDescriptor.java',
                  schemaTests=[p for p in paths if p.endswith('.java') and '/src/test/' in p and 'schemaVersion' in texts.get(p,'')],
                  testClasses=sorted(p for p in paths if '/src/test/' in p and p.endswith('.java')),
                  docMetrics=dict(files=len(documents),bytes=sum(r['bytes'] for r in documents),classes=dict(collections.Counter(r['classification'] for r in documents))),
                  linkBaseline=link_rows)
    if len(operation_ids)!=44:
        raise SystemExit('Operation extraction must yield 44, got %d: %s' % (len(operation_ids),operation_ids))
    OUT.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n')
    print(json.dumps(result['docMetrics']))


def check():
    baseline=json.loads(OUT.read_text())
    paths=tracked(ROOT)
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
    first=subprocess.check_output(['git','log','--diff-filter=A','--format=%H','--',str(OUT.relative_to(ROOT))],cwd=ROOT,text=True).splitlines()
    if first:
        frozen=subprocess.check_output(['git','show',first[-1]+':'+str(OUT.relative_to(ROOT))],cwd=ROOT)
        if frozen!=OUT.read_bytes(): raise SystemExit('Frozen P0 inventory changed')
    print(json.dumps({'operationIdsUnchanged':len(current),'descriptorSchemas':schemas,'productionJavaUnchanged':len(baseline['productionJava'])}))


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    check() if args.check else generate()
