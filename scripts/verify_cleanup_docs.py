#!/usr/bin/env python3
"""Audit local documentation targets; no network requests, Python 3.9+."""
import argparse
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
        if path.suffix not in TEXT_SUFFIXES or not path.is_file():
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


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--audit', action='store_true', help='Report baseline defects without granting a docs PASS')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args(argv)
    rows = audit(args.root)
    result = {'mode': 'AUDIT' if args.audit else 'STRICT', 'targets': rows,
              'broken': [r for r in rows if not r['exists']]}
    output = json.dumps(result, indent=2, ensure_ascii=False) + '\n'
    if args.output:
        args.output.write_text(output)
    print(json.dumps({'mode': result['mode'], 'checked': len(rows), 'broken': len(result['broken'])}))
    return 0 if args.audit or not result['broken'] else 10


if __name__ == '__main__':
    raise SystemExit(main())
