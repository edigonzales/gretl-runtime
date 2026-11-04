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
`dependsOn` or ordering-only `after`. Connect an output port to an input port, or
use **Bind result**, to add a result reference and its required data dependency
in one source change. **Defaults** edits the Core's complete target list.
Delete and disconnect refuse changes that would leave unsafe references; they
never cascade silently. If another source must be open for a safe edit, open the
requested sources and confirm a newly planned change.

**Undo text** and **Redo text** use the pinned source's native VS Code history.
**Undo view** and **Redo view** affect only node positions and viewport. Use
**Save source** to persist the Groovy job; reopening the graph reads that same
source. Modelling never executes a job. Run saved jobs separately with the
Runtime's normal execution workflow.

Build from the repository root with JDK 25, Node 22 or newer, and the local `zip`/`unzip` tools using `./mvnw -B
clean verify`. This includes the server build, locked npm installation, host
tests, type checking and local VSIX packaging. The VSIX is written to
`vscode/gretl-vscode/dist/gretl-runtime.vsix`. During extension development, run
`npm run check`, `npm test`, and `npm run package` in this directory after building
the matching server. `npm run package` refuses mismatching protocol resources.

To prepare an isolated installed workbench, run `node scripts/workbench.mjs
/absolute/fresh/evidence-directory` in this directory. It installs the actual
VSIX and a separate read-only evidence extension, and creates a dedicated profile
and fixture workspace. It does not change the normal VS Code profile. Use actual
keyboard/mouse interactions and screenshots; the evidence driver's provider
captures are supplementary assertions. A prepared environment is not a passed
test. Workspace trust must be granted explicitly to this disposable fixture.
Add `--modeler` to prepare the two-root modeler fixture instead of the text-editor
fixture. **GRETL Evidence: Capture Buffer State Only** also collects bounded,
read-only geometry diagnostics from visible modeler panels; it never drives
selection, layout, navigation or source changes.

Native Rename and Quick Fix validate source preconditions before returning edits
to VS Code. An external filesystem writer can still race after that validation;
VS Code does not expose an atomic filesystem compare-and-swap transaction. Keep
multi-file changes in open buffers for the strongest native version protection.

Groovy syntax colouring is adapted from VS Code's Groovy TextMate grammar, under
its retained TextMate Bundle licence; VS Code conversion code is MIT licensed.
Provenance and original hashes are included in `syntaxes/PROVENANCE.json`.
All npm dependencies are locked. `minimatch` is pinned through an override to
11.0.1 to include the glob amplification fix; client and packaging behaviour are
checked with this version. Bundled licence texts are included in the VSIX.

The bundle manifest records the commit (and dirty state), Maven/artifact/Java/
Groovy/extension versions, catalogue and protocol identity, and hashes for every
shipped payload. The manifest itself and generated VSIX container metadata are
covered by the external `dist/distribution-inventory.json`. Packaging verifies
that inventory and tests a deliberately tampered copy of the real VSIX.
