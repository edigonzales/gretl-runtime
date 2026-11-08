# GRETL Runtime for Visual Studio Code

Extension ID: `edigonzales.gretl-runtime-vscode`.

Requires Java 25 and a local filesystem workspace. Install the local VSIX using
“Extensions: Install from VSIX”. The language server and operation catalogue are
included; no GRETL checkout, server download or job execution is needed.

`job.groovy` and `settings.gretl` use the GRETL Runtime language automatically.
For another job filename, run **GRETL: Use Active File as Runtime Job**. Other
Groovy and Gradle files retain their existing language association.

Configure **GRETL Runtime: Java Path** with a Java 25 executable if `java` is not
on PATH. Paths are passed as arguments, without shell interpolation. The optional
server path must identify an exact copy of this extension's bundled JAR.

The status bar and **GRETL: Show Language Server Output** display server state.
**GRETL: Stop Language Server** and **GRETL: Restart Language Server** affect only
this window's owned server. Unsaved documents remain in the editor. Workspace
trust is required before Java starts. Virtual filesystems are unsupported.

Run **GRETL: Open Modeler** to open the current source in the optional graph
editor. The text editor remains the default. Each modeler stays pinned to its
source, including when another workspace root or a same-named job has focus.
Tasks, actions, projects and explanatory inner invocations come from the language
server's static snapshot. Parameters and cleanup appear separately in Details.
Definitions and static target previews are separate views; a preview describes
the statically known dependency closure and does not execute or validate a job
against live services.

Drag nodes, pan the canvas, zoom, fit or filter declarations to explore the graph.
View positions are stored separately from the Groovy source. Select a node or
edge and choose **Reveal selection**, or double-click it, to navigate to its
current source. From a text selection use **GRETL: Show Selection in Modeler**.
Dirty text changes update the graph; partial analysis and stale server state are
shown explicitly. Local React Flow assets and a bundled ELK worker render the
view without a second server or remote frontend resources.

Use **Add declaration** to choose a catalogue operation or declare a parameter.
Forms keep a local draft until **Confirm source change**. Scalars, nested lists
and maps, parameter references, result references, job files, work paths and
secret aliases are explicit choices; aliases never resolve secret contents in
the editor. **Cancel draft** leaves the source unchanged. Unsupported dynamic
values remain in source with an explanation.

Select a task and open **Details** to configure, rename or delete it. Drag a
node's dependency handle to another node, or use **Add dependency**, to choose
`dependsOn`, ordering-only `after`, or explicit `finalizedBy` (owner → finalizer). Connect an output port to an input port, or
use **Bind result**, to add a result reference and its required data dependency
in one source change. **Defaults** edits the Core's complete target list.
Delete and disconnect refuse changes that would leave unsafe references; they
never cascade silently. If another source must be open for a safe edit, open the
requested sources and confirm a newly planned change.

Owners also offer **Details → Edit owner finalizers**. Finalizer edges have their
own label/style and no data port. Disconnect edits the owner header; rename
updates bound finalizer links and delete refuses referenced targets. Static target
previews show a nominal success plan with conditional candidates: selection alone
does not activate a finalizer or grant result access. The matching server advertises
`graphSchemaVersion=2` and `taskFinalizersVersion=1`; base protocol and catalogue
remain version 1. See the [finalizer guide](https://github.com/edigonzales/gretl-runtime/blob/main/docs/guides/task-finalizers.md).

**Undo text** and **Redo text** use native VS Code history in an actual target of
the last modeler edit.
**Undo view** and **Redo view** affect only node positions and viewport. Use
**Save changed sources** to persist that edit's target documents; reopening the
graph reads the same original files. Modelling never executes a job. Run saved jobs separately with the
Runtime's normal execution workflow.

## Static includes and shared contexts

A completely captured static `apply from:` job has a full graph. Fragment tasks
keep their original files and owner scope; includes add no execution edges.
Definition, references, hover and completion cross source files. Relative `file`
and SQL resources use the owning job directory. Include literals use the containing
source directory. Safe include-path suggestions also work in an unfinished quote;
unsupported or limited listings remain incomplete.

Server-confirmed fragments synchronize dirty buffers before offering fresh edits.
They acquire GRETL mode only while members; explicit language choices are respected.
There is no global `*.groovy` association. A source without an owner stays unassigned;
**Use Active File as Runtime Job** explicitly chooses its standalone entry role.

For a shared fragment use **GRETL: Select Source Context**. Each modeler panel pins
its own entry/scope, so another editor or panel cannot replace it. Shared sources
are readable in every known context. Automatically planned writes require an
exclusive known physical footprint, including all uses changed by rename. Independent
edits in unique sources and references to shared producers remain possible.
Opening additional buffers, closing/reopening a source, new unopened owners and
changed context selections invalidate old plans. No files are saved automatically.
Opaque control flow, recovery and missing sources keep analysis partial or invalid.
Unknown consumers outside the captured workspace domain are not claimed as covered.

## Development and qualification

For local development, open the repository root in VS Code. Make JDK 25,
Node 22 or newer, `zip` and `unzip` available to VS Code. Select **GRETL
Extension** in **Run and Debug** and press **F5**. The launch task runs
`./mvnw -B -pl vscode/gretl-vscode -am package` to build the language server,
extension and modeler. The Extension Development Host loads the extension from
the checkout and opens the editor fixture and its `job.groovy`. If prompted,
trust the repository to run the build task and the fixture workspace to start
the language server. To rebuild and reload after a change, stop the debug
session and press **F5** again.

Build from the repository root with JDK 25, Node 22 or newer, and the local `zip`/`unzip` tools using `./mvnw -B
clean verify`. This includes the server build, locked npm installation, host
tests, type checking, local VSIX packaging and package tests. The VSIX is
written to `vscode/gretl-vscode/dist/gretl-runtime.vsix`. During extension
development, run `npm test` for host tests and `npm run package` to build the
VSIX and inventory after building the matching server. Then run
`npm run test:package` to test those files. The package build checks types and
refuses mismatching protocol resources.

To prepare an isolated installed workbench, run `node scripts/workbench.mjs
/absolute/fresh/evidence-directory` in this directory. It installs the actual
VSIX and a separate read-only evidence extension, and creates a dedicated profile
and fixture workspace. It does not change the normal VS Code profile. Use actual
keyboard/mouse interactions and screenshots; the evidence driver's provider
captures are supplementary assertions. A prepared environment is not a passed
test. Workspace trust must be granted explicitly to this disposable fixture.
Add `--modeler` to prepare the two-root modeler fixture instead of the text-editor
fixture. Use `--qualification` for the cumulative three-root fixture, including
SQL, CRLF, read-only and 500-node inputs. That private Electron process uses a
closed loopback HTTP proxy; this does not disable networking for the operating
system or prove offline support of other hosts. **GRETL Evidence: Capture Buffer State Only** also collects bounded,
read-only geometry diagnostics from visible modeler panels; it never drives
selection, layout, navigation or source changes.

Native Rename and Quick Fix validate source preconditions before returning edits
to VS Code. An external filesystem writer can still race after that validation;
VS Code does not expose an atomic filesystem compare-and-swap transaction. Keep
multi-file changes in open buffers for the strongest native version protection.

The primary installed-workbench qualification runs on macOS arm64. The same
bundled server is also exercised as a real Linux arm64 Java 25 process in a
container with networking disabled. Windows, other CPU architectures and actual
VS Code Remote hosts have not been qualified. Java 25 is required on the host
that runs the extension; a cross-platform JAR alone does not establish GUI support
on every platform.

If the modeler shows STALE after a server failure, keep the source open and use
**GRETL: Show Language Server Output**, then **GRETL: Restart Language Server**.
Reopen a draft after the graph becomes fresh. A changed source, missing source
buffer or read-only file can reject an edit; the Core's reason remains visible.
An invalid Java path or a mismatching bundle is reported without downloading a
replacement. Complex Groovy stays available through **Show source**.

Groovy syntax colouring is adapted from VS Code's Groovy TextMate grammar, under
its retained TextMate Bundle licence; VS Code conversion code is MIT licensed.
Provenance and original hashes are included in `syntaxes/PROVENANCE.json`.
All npm dependencies are locked. `minimatch` is pinned through an override to
11.0.1 to include the glob amplification fix; client and packaging behaviour are
checked with this version. Bundled licence texts are included in the VSIX.

The bundle manifest records the commit (and dirty state), Maven/artifact/Java/
Groovy/extension versions, catalogue and protocol identity, and hashes for every
shipped payload. The manifest itself and generated VSIX container metadata are
covered by the external `dist/distribution-inventory.json`. The package
tests verify that inventory and reject a deliberately tampered copy of the
real VSIX.
