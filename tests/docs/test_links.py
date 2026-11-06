import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

CHECK = Path(__file__).resolve().parents[2] / 'scripts/check-docs.py'


class DocumentationLinksTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        subprocess.run(['git', 'init', '-q', self.root], check=True)

    def source(self, name, text, *, track=True):
        file = self.root / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text, encoding='utf-8')
        if track:
            subprocess.run(['git', '-C', self.root, 'add', '--', name], check=True)

    def check(self):
        output = self.root/'audit.json'
        result = subprocess.run([sys.executable, CHECK, '--root', self.root, '--output', output],
                                text=True, capture_output=True)
        return result, json.loads(output.read_text())

    def test_tracked_links_anchors_includes_and_navigation(self):
        self.source('README.md', '[guide](docs/user%20guide.md#first-steps)\n'
                    '[reference][manual]\n\n[manual]: docs/reference.adoc\n'
                    '[hosted](https://github.com/edigonzales/gretl-runtime/blob/main/docs/reference.adoc)\n'
                    '[elsewhere](https://example.test/external)\n```text\n[example](absent.md)\n```\n')
        self.source('docs/user guide.md', '# First Steps\n')
        self.source('docs/reference.adoc', '= Reference\n:parts: generated\n\ninclude::{parts}/task.adoc[]\n')
        self.source('docs/generated/task.adoc', '== Example\n')
        self.source('website/_quarto.yml', 'website:\n  navbar:\n    - href: intro.qmd\n')
        self.source('website/intro.qmd', '[next](other.html#explicit)\n')
        self.source('website/other.qmd', '# Other {#explicit}\n')
        result, audit = self.check()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertGreaterEqual(len(audit['targets']), 6)
        self.assertEqual([], audit['broken'])

    def test_broken_sources_and_generated_html_cannot_pass(self):
        self.source('docs/guide.md', '# Present\n[missing anchor](#absent)\n')
        self.source('some-module/README.md', '[missing](absent.md)\n')
        self.source('docs/manual.adoc', '= Reference\ninclude::missing.adoc[]\n')
        self.source('website/_quarto.yml', 'website:\n  navbar:\n    - href: absent.qmd\n')
        self.source('website/index.qmd', '[old output](_site/gone.html)\n')
        self.source('website/_site/gone.html', '<h1>Old output</h1>', track=False)
        self.source('docs/biblios.yml', 'content:\n  start_path: docs\n  master_file: vanished.adoc\n')
        result, audit = self.check()
        self.assertEqual(10, result.returncode, result.stdout + result.stderr)
        self.assertEqual({'#absent', 'absent.md', 'missing.adoc', 'absent.qmd', '_site/gone.html',
                          '/docs/vanished.adoc'}, {row['target'] for row in audit['broken']})


if __name__ == '__main__':
    unittest.main()
