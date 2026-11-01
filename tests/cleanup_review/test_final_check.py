"""Negative tests for scope-complete rest classification, never production changes."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'scripts'))
import cleanup_final_check as final


class FinalCheckTest(unittest.TestCase):
    def test_numbered_phase_phrases_are_not_execution_phase_exceptions(self):
        path='gretl-interlis-tasks/src/main/java/ch/so/agi/gretl/internal/shapefile/core/ShapefileWriteOptions.java'
        for phrase in ('Phase2','Phase 7','phase-7','Phase_7','Phase2Later'):
            line=' * adapter level) and is not part of the '+phrase+' core.'
            with self.subTest(phrase=phrase):
                tokens=[m.group(0) for m in final.TOKENS.finditer(line)]
                self.assertIn(phrase,tokens)
                self.assertEqual('historical-source-comment',final.reason(path,line,phrase)[0])
                with self.assertRaises(RuntimeError):final.reason('website/features.qmd',line,phrase)
        # Also prevent the original bug for a caller that supplies a split token.
        self.assertEqual('historical-source-comment',final.reason(path,' * Phase 7 core.','Phase')[0])
        with self.assertRaises(RuntimeError):final.reason('website/features.qmd','Phase 7 coming later','Phase')

    def test_scan_rejects_unknown_spaced_and_hyphenated_historical_phases(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);subprocess.run(['git','init','-q',directory],check=True)
            page=root/'website/features.qmd';page.parent.mkdir()
            for text in ('Phase 7 is pending','phase-2 is pending','Phase_3 is pending'):
                page.write_text(text+'\n')
                with self.subTest(text=text),self.assertRaisesRegex(RuntimeError,'Unclassified historical marker'):final.scan(root)

    def test_unknown_historical_names_fail(self):
        for path,line,token in [('website/features.qmd','Phase2 coming later','Phase2'),
                                ('gretl-api/src/main/java/NewM0.java','NewM0.java','M0'),
                                ('examples/job.groovy','Hardening current job','Hardening')]:
            with self.subTest(path=path),self.assertRaises(RuntimeError):final.reason(path,line,token)

    def test_domain_names_and_physical_units_are_explicitly_preserved(self):
        self.assertEqual('physical-unit',final.reason('models/Units.ili','Area = [m2]','m2')[0])
        self.assertEqual('maven-cache',final.reason('pom.xml','.m2/repository','m2')[0])
        self.assertEqual('execution-or-error-phase',final.reason('Run.java','failurePhase','failurePhase')[0])
        self.assertEqual('csv-library-compatibility',final.reason('CsvReaderProbeTest.java','CsvReaderProbeTest','CsvReaderProbeTest')[0])
        self.assertEqual('validator-function-probe',final.reason('FunctionProbe.ili','FunctionProbe','FunctionProbe')[0])
        self.assertEqual('compatibility-mode',final.reason('PackageDescriptor.java','legacySchema','legacySchema')[0])

    def test_scan_covers_names_and_rejects_unclassified_new_markers(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);subprocess.run(['git','init','-q',directory],check=True)
            p=root/'website/features.qmd';p.parent.mkdir();p.write_text('Current runtime\n')
            self.assertEqual(['website/features.qmd'],[r['path'] for r in final.scan(root)['files']])
            p.write_text('Phase2 is pending\n')
            with self.assertRaises(RuntimeError):final.scan(root)
            p.write_text('Current runtime\n')
            java=root/'gretl-api/src/main/java/NewM0.java';java.parent.mkdir(parents=True);java.write_text('class NewM0 {}\n')
            with self.assertRaisesRegex(RuntimeError,'Historical filename'):final.scan(root)

    def test_binary_assets_are_hashed_without_false_text_markers(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);subprocess.run(['git','init','-q',directory],check=True)
            p=root/'website/font.woff2';p.parent.mkdir();p.write_bytes(b'\xffM0\x00')
            row=final.scan(root)['files'][0]
            self.assertEqual([],row['matches']);self.assertEqual(final.sha(p.read_bytes()),row['sha256'])

    def test_stale_inventory_and_unclassified_additions_are_blocked(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);subprocess.run(['git','init','-q',directory],check=True)
            p=root/'README.md';p.write_text('Current\n')
            manifest=root/final.MANIFEST;manifest.parent.mkdir(parents=True)
            manifest.write_text(json.dumps(final.scan(root)))
            p.write_text('Unreviewed edit\n')
            with self.assertRaisesRegex(RuntimeError,'stale'):final.check(root)
