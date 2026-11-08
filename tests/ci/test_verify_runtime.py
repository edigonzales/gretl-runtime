import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import unittest


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "verify-runtime.py"

# The subprocess imports the real coordinator and only shortens its internal deadlines.
RUN_COORDINATOR = """
import importlib.util
from pathlib import Path
import sys
sys.path.insert(0, str(Path(sys.argv[1]).parent))
spec = importlib.util.spec_from_file_location("verify_runtime", sys.argv[1])
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
module.DOCKER_TIMEOUT = 1.0
module.TERM_TIMEOUT = 0.3
module.KILL_TIMEOUT = 2.0
module.CLEANUP_TIMEOUT = 5.0
module.POLL_INTERVAL = 0.02
sys.exit(module.verify(Path(sys.argv[2])))
"""

FAKE_DOCKER = r"""
import json, os, signal, sys, time
from pathlib import Path
root = Path(os.environ["TEST_ROOT"])
args = sys.argv[1:]
with (root / "docker.jsonl").open("a") as log:
    log.write(json.dumps(args) + "\n")
mode = os.environ["TEST_MODE"]
match args:
    case ["info", *_]:
        if mode == "docker-info-error":
            sys.exit(8)
        print("Docker server fake")
    case ["network", "create", "--label", label, name]:
        (root / "network").write_text(label)
        if mode == "signal-during-network":
            (root / "ready").write_text("network")
            while not (root / "resume").exists():
                time.sleep(0.01)
        if mode == "network-create-error":
            sys.exit(9)
        print("owned-network")
    case ["container", "ls", "--all", "--quiet", "--filter", label]:
        if mode == "hang-list":
            time.sleep(300)
        if label == "label=" + (root / "network").read_text():
            print("owned-container")
    case ["container", "rm", "--force", "owned-container"]:
        if mode in {"cleanup-error", "maven-and-cleanup-error"}:
            print("permission denied", file=sys.stderr)
            sys.exit(10)
    case ["network", "ls", "--quiet", "--filter", label]:
        if label == "label=" + (root / "network").read_text():
            print("owned-network")
    case ["network", "rm", "owned-network"]:
        (root / "network").unlink()
    case _:
        print(f"Unexpected Docker call {args}", file=sys.stderr)
        sys.exit(11)
"""

FAKE_MAVEN = """
import json, os, signal, subprocess, sys, time
from pathlib import Path
root = Path(os.environ["TEST_ROOT"])
mode = os.environ["TEST_MODE"]

def wait_for(file):
    while not (root / file).exists():
        time.sleep(0.01)

def record(name):
    (root / name).write_text(str(os.getpid()))

if sys.argv[1:] == ["grandchild"]:
    signal.signal(signal.SIGTERM, signal.SIG_IGN)
    record("grandchild")
    time.sleep(300)
elif sys.argv[1:] == ["child"]:
    if mode == "term-resistant":
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        subprocess.Popen([sys.executable, __file__, "grandchild"])
        wait_for("grandchild")
    record("child")
    time.sleep(300)
else:
    info = {
        "args": sys.argv[1:],
        "run_id": os.environ["GRETL_VERIFICATION_RUN_ID"],
        "python": os.environ["GRETL_PYTHON"],
        "pid": os.getpid(), "pgid": os.getpgrp(), "sid": os.getsid(0),
    }
    (root / "maven.json").write_text(json.dumps(info))
    if mode == "term-resistant":
        workspace_arg = next(a for a in sys.argv if a.startswith("-Dgretl.test.imageWorkspace="))
        workspace = Path(workspace_arg.split("=", 1)[1])
        scenario = workspace / "fixture-00000000-0000-0000-0000-000000000001"
        scenario.mkdir()
        (scenario / "job.stdout.log").write_text("interrupted process output")
        (scenario / "job.stderr.log").write_text("password=interrupted-canary")
        (scenario / "runtime.json").write_text("forbidden image configuration")
    if mode in {"term-resistant", "orphan"}:
        signal.signal(signal.SIGTERM, lambda *_: (root / "term-seen").touch())
        subprocess.Popen([sys.executable, __file__, "child"])
        wait_for("child")
        (root / "ready").write_text("maven")
        if mode == "term-resistant":
            time.sleep(300)
    sys.exit(19 if mode in {"maven-error", "maven-and-cleanup-error"} else 0)
"""


class VerifyRuntimeTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.write_program("docker", FAKE_DOCKER)
        self.write_program("mvnw", FAKE_MAVEN)
        (self.root / "foreign-resource").write_text("untouched")

    def write_program(self, name, source):
        path = self.root / name
        path.write_text(f"#!{sys.executable}\n{source}")
        path.chmod(0o700)

    def start(self, mode):
        environment = os.environ | {
            "PATH": f"{self.root}:{os.environ['PATH']}",
            "TEST_ROOT": str(self.root),
            "TEST_MODE": mode,
            "RUNNER_TEMP": str(self.root / "runner-temp"),
        }
        process = subprocess.Popen(
            [sys.executable, "-c", RUN_COORDINATOR, str(SCRIPT), str(self.root)],
            cwd=self.root.parent, env=environment, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, text=True, start_new_session=True,
        )
        self.addCleanup(self.stop_fixture, process)
        return process

    def stop_fixture(self, process):
        # Rescue only this fixture's known group if the coordinator failed the test.
        maven = self.root / "maven.json"
        if maven.exists():
            info = json.loads(maven.read_text())
            self.assertEqual(info["pid"], info["pgid"])
            if process.poll() is None or any(
                    self.alive(int((self.root / f).read_text()))
                    for f in ("child", "grandchild") if (self.root / f).exists()):
                try:
                    os.killpg(info["pgid"], signal.SIGKILL)
                except ProcessLookupError:
                    pass
            for argument in info["args"]:
                if argument.startswith("-Dgretl.test.imageWorkspace="):
                    workspace = Path(argument.split("=", 1)[1])
                    if workspace.parent == Path("/tmp") and workspace.name.startswith("gretl-image-jobs."):
                        shutil.rmtree(workspace, ignore_errors=True)
        if process.poll() is None:
            process.kill()
        process.communicate(timeout=3)

    @staticmethod
    def alive(pid):
        result = subprocess.run(["ps", "-o", "stat=", "-p", str(pid)], capture_output=True,
                                text=True, timeout=2)
        return result.returncode == 0 and not result.stdout.strip().startswith("Z")

    def wait_ready(self, process):
        deadline = time.monotonic() + 5
        while not (self.root / "ready").exists() and time.monotonic() < deadline:
            self.assertIsNone(process.poll())
            time.sleep(0.01)
        self.assertTrue((self.root / "ready").exists())

    def finish(self, process, expected):
        out, err = process.communicate(timeout=10)
        self.assertEqual(expected, process.returncode, out + err)
        self.assertEqual("untouched", (self.root / "foreign-resource").read_text())
        self.assertFalse((self.root / "network").exists())
        return err

    def calls(self):
        return [json.loads(line) for line in (self.root / "docker.jsonl").read_text().splitlines()]

    def assert_clean_workspace(self):
        info = json.loads((self.root / "maven.json").read_text())
        workspace_argument, = [a for a in info["args"] if a.startswith("-Dgretl.test.imageWorkspace=")]
        workspace = Path(workspace_argument.split("=", 1)[1])
        self.assertEqual(Path("/tmp"), workspace.parent)
        self.assertFalse(workspace.exists())
        return info

    def test_success_keeps_the_reactor_arguments_and_cleans_only_owned_resources(self):
        self.finish(self.start("success"), 0)
        self.assertFalse((self.root / "runner-temp" / "gretl-failure-diagnostics").exists())
        info = self.assert_clean_workspace()
        self.assertEqual(info["pid"], info["pgid"])
        self.assertEqual(info["pid"], info["sid"])
        self.assertNotEqual(os.getpgrp(), info["pgid"])
        self.assertEqual(sys.executable, info["python"])
        self.assertEqual(["-B", "-Pgeotools-probe,backing-services,job-image", "clean", "verify"],
                         info["args"][:4])
        self.assertIn(f"-Dgretl.test.runId={info['run_id']}", info["args"])
        self.assertIn("-Dgretl.test.imageNetwork=owned-network", info["args"])
        self.assertIn(f"-Dgretl.test.imageUser={os.getuid()}:{os.getgid()}", info["args"])
        host, extra = ("host.docker.internal", "") if sys.platform == "darwin" else (
            "services", "services:host-gateway")
        self.assertIn(f"-Dgretl.test.httpImageHost={host}", info["args"])
        self.assertIn(f"-Dgretl.test.imageExtraHost={extra}", info["args"])
        label = f"gretl.verification.run={info['run_id']}"
        self.assertEqual([
            ["info", "--format", "Docker server {{.ServerVersion}}"],
            ["network", "create", "--label", label, f"gretl-verify-{info['run_id']}"],
            ["container", "ls", "--all", "--quiet", "--filter", f"label={label}"],
            ["container", "rm", "--force", "owned-container"],
            ["network", "ls", "--quiet", "--filter", f"label={label}"],
            ["network", "rm", "owned-network"],
        ], self.calls())

    def test_maven_failure_is_preserved(self):
        self.finish(self.start("maven-error"), 19)
        self.assert_clean_workspace()
        self.assertTrue((self.root / "runner-temp" / "gretl-failure-diagnostics" / "index.txt").exists())

    def test_diagnostics_failure_does_not_replace_maven_failure(self):
        output = self.root / "runner-temp" / "gretl-failure-diagnostics"
        output.mkdir(parents=True)
        sentinel = output / "keep.txt"
        sentinel.write_text("do not replace")
        err = self.finish(self.start("maven-error"), 19)
        self.assertIn("diagnostics could not be exported", err)
        self.assertEqual("do not replace", sentinel.read_text())
        self.assert_clean_workspace()

    def test_maven_failure_survives_a_cleanup_failure(self):
        err = self.finish(self.start("maven-and-cleanup-error"), 19)
        self.assertIn("permission denied", err)
        self.assert_clean_workspace()

    def test_cleanup_failure_makes_success_red(self):
        err = self.finish(self.start("cleanup-error"), 1)
        self.assertIn("permission denied", err)
        self.assert_clean_workspace()

    def test_docker_timeout_still_attempts_other_cleanup(self):
        err = self.finish(self.start("hang-list"), 1)
        self.assertIn("docker container ls", err)
        self.assertIn("timed out", err)
        self.assert_clean_workspace()

    def test_initial_docker_failure_creates_no_resources(self):
        self.finish(self.start("docker-info-error"), 1)
        self.assertFalse((self.root / "maven.json").exists())
        self.assertEqual([["info", "--format", "Docker server {{.ServerVersion}}"]], self.calls())

    def test_partly_created_network_is_cleaned(self):
        self.finish(self.start("network-create-error"), 1)
        self.assertFalse((self.root / "maven.json").exists())
        self.assertIn(["network", "rm", "owned-network"], self.calls())

    def test_sigint_during_network_creation(self):
        process = self.start("signal-during-network")
        self.wait_ready(process)
        process.send_signal(signal.SIGINT)
        (self.root / "resume").touch()
        self.finish(process, 130)
        self.assertFalse((self.root / "maven.json").exists())

    def test_sigterm_kills_term_resistant_process_group_including_grandchild(self):
        process = self.start("term-resistant")
        self.wait_ready(process)
        process.send_signal(signal.SIGTERM)
        self.finish(process, 143)
        self.assertTrue((self.root / "term-seen").exists())
        self.assert_clean_workspace()
        artifact = self.root / "runner-temp" / "gretl-failure-diagnostics"
        index = (artifact / "index.txt").read_text()
        self.assertIn("Interrupted IMAGE scenarios", index)
        self.assertIn("test attribution incomplete", index)
        exported = "\n".join(path.read_text() for path in artifact.iterdir())
        self.assertIn("interrupted process output", exported)
        self.assertNotIn("interrupted-canary", exported)
        self.assertNotIn("forbidden image configuration", exported)
        for name in ("child", "grandchild"):
            self.assertFalse(self.alive(int((self.root / name).read_text())), name)

    def test_cleans_live_child_after_group_leader_exits(self):
        self.finish(self.start("orphan"), 0)
        self.assert_clean_workspace()
        self.assertFalse(self.alive(int((self.root / "child").read_text())))


if __name__ == "__main__":
    unittest.main()
