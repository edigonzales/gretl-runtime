# GRETL Runtime for Visual Studio Code

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

Build from the repository root with JDK 25 and Node 22 or newer using `./mvnw -B
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
