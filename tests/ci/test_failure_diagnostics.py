import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "failure_diagnostics.py"
SPEC = importlib.util.spec_from_file_location("failure_diagnostics", SCRIPT)
diagnostics = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(diagnostics)


class FailureDiagnosticsTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.repo = self.root / "repo"
        self.output = self.root / "export"
        self.reports = self.repo / "nested" / "module" / "target" / "surefire-reports"
        self.reports.mkdir(parents=True)
        (self.repo / "pom.xml").write_text(
            '<project xmlns="http://maven.apache.org/POM/4.0.0">'
            '<modules><module>nested/module</module><module>fake-module</module></modules></project>'
        )
        self.staging = (self.repo / "gretl-integration-tests" / "target"
                        / "failed-process-diagnostics" / "fixture")
        self.staging.mkdir(parents=True)

    def collect(self):
        diagnostics.collect(self.repo, self.output, run_id="fixture")
        return (self.output / "index.txt").read_text()

    def case(self, name, logs):
        directory = self.staging / name
        directory.mkdir()
        (directory / "case.json").write_text(json.dumps({
            "test": "fixture.DemoTest#assertion",
            "logs": [{"name": name} for name in logs],
            "omitted": 1,
        }))
        for filename, text in logs.items():
            if text is not None:
                (directory / filename).write_text(text)
        return directory

    def test_exports_only_failure_details_and_allowlisted_redacted_logs(self):
        (self.reports / "TEST-demo.xml").write_text("""
            <testsuite>
              <properties><property name="private" value="forbidden-properties"/></properties>
              <testcase classname="fixture.DemoTest" name="assertion">
                <failure type="java.lang.AssertionError" message="wrong fixture output">fixture location
                    password=report-canary</failure>
                <system-out>forbidden-system-out</system-out>
              </testcase>
              <testcase classname="fixture.DemoTest" name="negativePassed">
                <system-err>forbidden-success-logs</system-err>
              </testcase>
            </testsuite>
        """)
        (self.reports / "TEST-invalid.xml").write_text("<invalid")
        outside = self.root / "outside"
        outside.mkdir()
        (outside / "outside.log").write_text("forbidden-outside")
        directory = self.case("case-1", {
            "1-IMAGE-stdout.log": "useful output\nAuthorization: Basic staged-canary\n",
            "1-IMAGE-stderr.log": None,
            "runtime.json": "forbidden-runtime",
            "command.json": "forbidden-command",
            "artifact.jar": "forbidden-artifact",
            "../outside.log": None,
        })
        (directory / "1-IMAGE-stderr.log").symlink_to(outside / "outside.log")
        (self.staging / "case-link").symlink_to(outside, target_is_directory=True)
        (self.repo / "fake-module").symlink_to(outside, target_is_directory=True)

        index = self.collect()
        self.assertIn("failure: fixture.DemoTest#assertion (java.lang.AssertionError)", index)
        self.assertIn("wrong fixture output", index)
        self.assertIn("fixture location", index)
        self.assertIn("malformed report TEST-invalid.xml", index)
        self.assertIn("outside the log allowlist", index)
        self.assertIn("symbolic-link entry", index)
        self.assertIn("unsafe or unreadable log omitted", index)
        self.assertIn("logs were unavailable", index)
        self.assertNotIn("negativePassed", index)
        self.assertEqual({"index.txt", "process-001.log"}, {p.name for p in self.output.iterdir()})
        text = "\n".join(p.read_text() for p in self.output.iterdir())
        self.assertIn("useful output", text)
        self.assertIn("[Sensitive line omitted]", text)
        self.assertNotIn("canary", text)
        self.assertNotIn("forbidden-", text)

    def test_bounds_tail_file_count_total_bytes_and_explains_omissions(self):
        for number in range(8):
            self.case(f"case-{number}", {
                "1-IMAGE-stdout.log": "beginning is discarded\n" + "x" * (2 * diagnostics.MAX_LOG)
                + "\nkept tail\n" + ("short lines\n" * 60),
            })
        with patch.multiple(diagnostics, MAX_FILES=3, MAX_BYTES=4096, MAX_INDEX=2048, MAX_LOG=1024):
            index = self.collect()
        files = list(self.output.iterdir())
        self.assertEqual(3, len(files))
        self.assertLessEqual(sum(p.stat().st_size for p in files), 4096)
        self.assertIn("truncated", index)
        self.assertIn("remaining logs omitted", index)
        for path in self.output.glob("process-*.log"):
            self.assertLessEqual(path.stat().st_size, 1024)
            self.assertIn("kept tail", path.read_text())
            self.assertNotIn("beginning", path.read_text())

    def test_missing_reports_get_explained_and_stale_reports_are_excluded(self):
        first = self.collect()
        self.assertIn("No Maven XML test reports were available", first)
        (self.reports / "TEST-old.xml").write_text("""
            <testsuite><testcase classname="old" name="old">
                <error type="Old">stale details</error></testcase></testsuite>
        """)
        output = self.root / "new-export"
        diagnostics.collect(self.repo, output, since=10**12)
        index = (output / "index.txt").read_text()
        self.assertIn("stale", index)
        self.assertNotIn("stale details", index)

    def test_workflow_uploads_only_final_diagnostics_directory(self):
        workflow = (SCRIPT.parents[1] / ".github" / "workflows" / "publish-snapshots.yml").read_text()
        diagnostics_step = workflow.split("name: Preserve failure diagnostics", 1)[1].split("  publish:", 1)[0]
        self.assertIn("if: failure()", diagnostics_step)
        self.assertIn("retention-days: 7", diagnostics_step)
        self.assertIn("path: ${{ runner.temp }}/gretl-failure-diagnostics/", diagnostics_step)
        self.assertNotIn("job-evidence", diagnostics_step)
        self.assertNotIn("failed-process-diagnostics", diagnostics_step)


if __name__ == "__main__":
    unittest.main()
