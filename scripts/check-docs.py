#!/usr/bin/env python3
"""Check tracked Markdown, AsciiDoc and website links without network access."""
import argparse
import json
import re
import subprocess
from collections import Counter
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
TEXT = {'.md', '.qmd', '.adoc'}
REPO_URL = '/edigonzales/gretl-runtime/'
PATH_VALUE = r"""(?:"([^"]+)"|'([^']+)'|([^\s]+))"""


def tracked(root):
    return set(subprocess.check_output(['git', 'ls-files', '-z'], cwd=root)
               .decode().rstrip('\0').split('\0')) - {''}


def documented(name):
    path = Path(name)
    return (path.suffix in TEXT and (name.startswith(('docs/', 'website/', 'examples/'))
            or path.name.lower().startswith('readme') or name == 'AGENTS.md')
            or (name.startswith('website/') and path.suffix in {'.yml', '.yaml'})
            or path.name in {'biblios.yml', 'biblios.local.yml.example'})


def prose(text, suffix):
    fence = ''
    for number, line in enumerate(text.splitlines(), 1):
        marker = re.match(r'^\s*(`{3,}|~{3,}' + (r'|-{4,}' if suffix == '.adoc' else '') + r')', line)
        if fence:
            if re.fullmatch(r'\s*' + re.escape(fence[0]) + '{' + str(len(fence)) + r',}\s*', line):
                fence = ''
            continue
        if marker and (marker[1][0] != '-' or line.strip() == marker[1]):
            fence = marker[1]
            continue
        yield number, line


def targets(path, text):
    """Scan inline/reference Markdown, AsciiDoc directives and local site configuration."""
    lines = list(prose(text, path.suffix))
    definitions = {}
    for number, line in lines:
        if match := re.match(r'^\s*\[([^\]]+)\]:\s*(?:<([^>]+)>|([^\s]+))', line):
            definitions[match[1].casefold()] = (number, match[2] or match[3])
    attributes = dict(re.findall(r'^:([\w-]+):\s*(\S[^\n]*)', text, re.M)) if path.suffix == '.adoc' else {}
    start_path = None
    for number, line in lines:
        links = []
        if path.suffix in TEXT:
            markup = re.sub(r'`[^`]*`', '', line)
            links.extend((a or b, 'link') for a, b in re.findall(
                r'!?\[[^\]]*\]\(\s*(?:<([^>]+)>|([^\s)>]+))[^)]*\)', markup))
            for title, label in re.findall(r'\[([^\]]+)\]\[([^\]]*)\]', markup):
                if definition := definitions.get((label or title).casefold()):
                    links.append((definition[1], 'link'))
            links.extend((value, 'include' if kind == 'include::' else 'link')
                         for kind, value in re.findall(r'(include::|image::?|link:|xref:)([^\s\[]+)\[', markup))
            links.extend((value, 'source path') for value in re.findall(
                r'`((?:docs|scripts|examples|website|gretl-[^/` ]+)/[^` *<>]+\.(?:java|groovy|ili|xtf|qmd|md|py|adoc))`', line))
            links.extend((value, 'link') for value in re.findall(r'''\b(?:href|src)=["']([^"']+)["']''', markup))
        if path.name in {'biblios.yml', 'biblios.local.yml.example'}:
            if match := re.search(r'^\s*(start_path|start_page|master_file):\s*' + PATH_VALUE, line):
                key, *values = match.groups()
                value = next(v for v in values if v)
                if key == 'start_path':
                    start_path = value
                    links.append(('/' + value, 'config'))
                else:
                    links.append(('/' + str(Path(start_path or '') / value), 'config'))
        elif path.suffix in {'.yaml', '.yml'}:
            if match := re.search(r'^\s*(?:-\s*)?(?:href|file|source|favicon|logo):\s*' + PATH_VALUE, line):
                links.append((next(v for v in match.groups() if v), 'config'))
            if match := re.match(r'^\s*-\s+([\w./-]+\.(?:qmd|scss))\s*$', line):
                links.append((match[1], 'config'))
        for value, kind in dict.fromkeys(links):
            if attributes:
                value = re.sub(r'\{([\w-]+)\}', lambda m: attributes.get(m[1], m[0]), value)
            yield number, value, kind
    for number, value in definitions.values():
        yield number, value, 'link'


def anchors(text, suffix):
    result, duplicates = set(), Counter()
    for _, line in prose(text, suffix):
        for groups in re.findall(r'\[\[([\w.-]+)\]\]|\[#([\w.-]+)\]|\{#([\w.-]+)\}|\bid=["\']([\w.-]+)', line):
            result.update(v for v in groups if v)
        if heading := re.match(r'^\s*(?:#{1,6}|={1,6})\s+(.+?)\s*#*$', line):
            title = re.sub(r'\{#[^}]+\}\s*$', '', heading[1])
            slug = re.sub(r'[^\w\s-]', '', title.lower()).strip()
            slug = re.sub(r'\s+', '-', slug)
            n = duplicates[slug]
            duplicates[slug] += 1
            result.add(slug + ('-' + str(n) if n else ''))
            if suffix == '.adoc':
                result.add('_' + slug.replace('-', '_') + ('_' + str(n + 1) if n else ''))
    return result


def local_target(value):
    url = urlsplit(value)
    if url.hostname in {'github.com', 'www.github.com'} and url.path.startswith(REPO_URL):
        match = re.fullmatch(re.escape(REPO_URL) + r'(?:blob|tree)/main(/.+)', url.path)
        if match:
            return unquote(match[1]), unquote(url.fragment)
    if url.scheme or url.netloc:
        return None
    return unquote(url.path), unquote(url.fragment)


def audit(root, files, sources):
    root = root.resolve()
    files = {root / name for name in files}
    directories = {parent for file in files for parent in file.parents if parent.is_relative_to(root)}
    anchor_cache = {}
    rows = []
    for name in sorted(sources):
        path = root / name
        for number, value, kind in targets(path, path.read_text(encoding='utf-8')):
            local = local_target(value)
            if local is None:
                continue
            address, fragment = local
            if not address and not fragment:
                continue
            target = path if not address else ((root / address.lstrip('/')) if address.startswith('/')
                                                else path.parent / address).resolve()
            if kind == 'source path' and target not in files:
                target = (root / address).resolve()
            if name.startswith('website/') and target.suffix == '.html':
                target = target.with_suffix('.qmd')
            exists = ((target in files and target.is_file())
                      or (kind != 'include' and target in directories and target.is_dir()))
            if exists and fragment and target.suffix in TEXT:
                if target not in anchor_cache:
                    anchor_cache[target] = anchors(target.read_text(encoding='utf-8'), target.suffix)
                exists = fragment in anchor_cache[target]
            rows.append(dict(source=name, line=number, target=value, kind=kind, exists=exists))
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--source', action='append', help='Check a specific tracked source; default: all documentation')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    files = tracked(args.root)
    eligible = {name for name in files if documented(name)}
    sources = set(args.source or eligible)
    if not sources or not sources <= eligible or any(not (args.root/name).is_file() for name in sources):
        parser.error('Expected existing tracked documentation sources')
    rows = audit(args.root, files, sources)
    broken = [row for row in rows if not row['exists']]
    if args.output:
        args.output.write_text(json.dumps(dict(sources=sorted(sources), targets=rows, broken=broken),
                                          indent=2, ensure_ascii=False) + '\n')
    for row in broken:
        print('{source}:{line}: unresolved {kind}: {target}'.format(**row))
    print(f'{len(sources)} sources, {len(rows)} local targets, {len(broken)} unresolved')
    return 10 if broken else 0


if __name__ == '__main__':
    raise SystemExit(main())
