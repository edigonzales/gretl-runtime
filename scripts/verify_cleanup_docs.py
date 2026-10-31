#!/usr/bin/env python3
"""Audit local documentation targets; no network requests, Python 3.9+."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
TEXT_SUFFIXES = {'.md', '.adoc', '.qmd', '.yml', '.yaml', '.json', '.jsonc', '.xml', '.txt', '.scss', '.ftl', '.example'}


def tracked(root):
    return subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')[:-1]


def documented(path):
    return (path.startswith(('docs/', 'website/')) or path in {'README.md', 'AGENTS.md'}
            or (path.startswith(('examples/', 'gretl-dist/src/main/dist/'))
                and Path(path).name.lower().startswith(('readme', 'license', 'notice'))))


def targets(path, text):
    """Return links with line provenance, including reference style and Quarto nav."""
    definitions = dict(re.findall(r'^\s*\[([^\]]+)\]:\s*<?([^\s>]+)>?', text, re.M))
    fence = False
    for line_no, line in enumerate(text.splitlines(), 1):
        if re.match(r'^\s*(```|~~~)', line):
            fence = not fence
            continue
        if fence:
            continue
        links = re.findall(r'!?\[[^\]]*\]\(<?([^\s)>]+)>?(?:\s+[^)]*)?\)', line)
        links += [definitions.get(label or title, '') for title, label in re.findall(r'\[([^\]]+)\]\[([^\]]*)\]', line)]
        links += re.findall(r'(?:include::|image::?)([^\s\[]+)\[', line)
        links += re.findall(r'link:([^\s\[]+)\[', line)
        if path.suffix in {'.yml', '.yaml'}:
            links += re.findall(r'(?:href|file|source):\s*[\'\"]?([^\s\'\"#]+)', line)
        references=[]
        if path.suffix in {'.md','.qmd','.adoc'}:
            references = re.findall(r'`((?:docs|scripts|examples|website|gretl-[^/` ]+)/[^` *<>]+\.(?:java|groovy|ili|xtf|qmd|md|py|adoc))`',line)
        if path.suffix in {'.yml','.yaml'}:
            links += re.findall(r'^\s*-\s+([A-Za-z0-9_./-]+\.qmd)\s*$',line)
        for link in dict.fromkeys(links+references):
            if link:
                yield line_no, link, ('code-reference' if link in references else 'link')


def audit(root, paths=None):
    root = Path(root).resolve()
    rows = []
    for name in sorted(paths if paths is not None else [p for p in tracked(root) if documented(p)]):
        path = root / name
        if path.suffix not in {'.md','.qmd','.adoc','.yml','.yaml'} or not path.is_file():
            continue
        for line_no, link, kind in targets(path, path.read_text(encoding='utf-8', errors='replace')):
            url = urlsplit(link)
            if url.scheme or url.netloc or (not url.path and not url.fragment) or '{' in link:
                continue
            local = unquote(url.path)
            target = (path if not local else ((root / local.lstrip('/')) if local.startswith('/') else path.parent / local)).resolve()
            candidates = [target]
            if target.suffix == '.html' and name.startswith('website/'):
                candidates.append(target.with_suffix('.qmd'))
            if kind=='code-reference' and not any(p.exists() for p in candidates) and local.startswith(('docs/','scripts/','examples/','website/','gretl-')):
                candidates.append(root/local)
            exists = any(p.exists() for p in candidates)
            if exists and url.fragment:
                source=next(p for p in candidates if p.is_file()) if any(p.is_file() for p in candidates) else None
                if source and source.suffix in {'.md','.qmd','.adoc'}:
                    contents=source.read_text(encoding='utf-8',errors='replace')
                    anchors=set(re.findall(r'\[\[([\w.-]+)\]\]|\[#([\w.-]+)\]|\{#([\w.-]+)\}',contents))
                    anchors={value for group in anchors for value in group if value}
                    duplicates={}
                    for heading in re.findall(r'^(?:#{1,6}|={1,5})\s+(.+)',contents,re.M):
                        slug=re.sub(r'[^\w\s-]','',heading.lower()).strip().replace(' ','-')
                        number=duplicates.get(slug,0);duplicates[slug]=number+1
                        anchors.add(slug+('-'+str(number) if number else ''))
                    exists=unquote(url.fragment) in anchors
            rows.append({'source': name, 'line': line_no, 'target': link, 'kind':kind, 'exists': exists})
    return rows


SPEC_HASH='c0bb1b7488853de397d540c9d0aadf2cb62744a2a18808bd043f03cf0a9c0946'
SPEC_QUOTES={(67,'gretl-integration-tests/.../HardeningLocalJobIT.java'):'Abbreviated baseline finding; actual test is under src/test/java/ch/so/agi/gretl/it.',
             (420,'docs/maintenance/cleanup/spec-v1.md'):'Immutable older filename in requirement; canonical authorized input is spec-v2.md.',
             (569,'docs/maintenance/cleanup/spec-v1.md'):'Quoted historical prompt template; actual invocation uses spec-v2.md.',
             (591,'docs/maintenance/cleanup/spec-v1.md'):'Quoted reviewer template; actual invocation uses spec-v2.md.'}

# Exact immutable references to deliberately removed historical inputs. New
# targets/lines or any changed specification bytes remain hard failures.
SPEC_QUOTES.update({
 (58,'examples/project-subprojects/README.md'):'DOC-05 example README was consolidated into the current project guide; fixture source remains.',
 (9,'docs/spec/AGENTS.md'):'Scope statement quotes the historical instruction removed by P2; current rules are AGENTS.md.',
 (54,'docs/spec/AGENTS.md'):'DOC-01 baseline finding; original source preserved by cleanup-actions.json.',
 (55,'docs/spec/README.md'):'DOC-02 baseline finding; current navigation is docs/README.md.',
 (59,'docs/implementation/improvement-plan/requirement-tests.md'):'DOC-06 completed matrix preserved in Git, not a current target.',
 (60,'docs/implementation/hardening-f1-f4/gretl-runtime-startprompt-f1-f4-neu.md'):'DOC-07 completed prompt intentionally removed; Git preservation is indexed.',
 (73,'docs/implementation/m7-report.md'):'DOC-11 historical report; summary and verified Git permalink are in docs/history/README.md.',
 (117,'docs/spec/AGENTS.md'):'Requirement to remove the historical active filename, fulfilled by P2.',
 (440,'docs/spec/AGENTS.md'):'P1 temporary warning requirement; P2 subsequently removes the old instructions.',
 (466,'docs/implementation/m0-report.md'):'P2 curation requirement quotes its historical input; source retained in Git.',
 (569,'docs/spec/AGENTS.md'):'Quoted old prompt explicitly rejects those obsolete instructions.'})


def active_sources(root,paths):
    root=Path(root)
    assessment=root/'docs/maintenance/cleanup/content-assessment.json'
    if not assessment.exists():return list(paths),[]
    decisions={r['path']:r['classification'] for r in json.loads(assessment.read_text())['documents']}
    # These are specifically inventoried legacy sources, pending P2 curation.
    # Unknown/new pages always receive the hard gate.
    historical=[p for p in paths if decisions.get(p) in {'DELETE','ARCHIVE','DECISION'}]
    return [p for p in paths if p not in historical],historical


def classify_exceptions(root,rows):
    spec=Path(root)/'docs/maintenance/cleanup/spec-v2.md'
    qualified=spec.is_file() and hashlib.sha256(spec.read_bytes()).hexdigest()==SPEC_HASH
    exceptions=[]
    for row in rows:
        key=(row['line'],row['target'])
        if not row['exists'] and qualified and row['source']=='docs/maintenance/cleanup/spec-v2.md' and key in SPEC_QUOTES:
            row['historicalException']=SPEC_QUOTES[key];exceptions.append(row)
    return exceptions


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--audit', action='store_true', help='Report baseline defects without granting a docs PASS')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args(argv)
    paths=[p for p in tracked(args.root) if documented(p)]
    selected,historical=active_sources(args.root,paths) if not args.audit else (paths,[])
    rows = audit(args.root,selected)
    exceptions=classify_exceptions(args.root,rows) if not args.audit else []
    result = {'mode': 'AUDIT' if args.audit else 'STRICT', 'targets': rows,
              'historicalSources':historical,'knownHistoricalExceptions':exceptions,
              'broken': [r for r in rows if not r['exists'] and 'historicalException' not in r]}
    output = json.dumps(result, indent=2, ensure_ascii=False) + '\n'
    if args.output:
        args.output.write_text(output)
    print(json.dumps({'mode': result['mode'], 'checked': len(rows), 'broken': len(result['broken'])}))
    return 0 if args.audit or not result['broken'] else 10


if __name__ == '__main__':
    raise SystemExit(main())
