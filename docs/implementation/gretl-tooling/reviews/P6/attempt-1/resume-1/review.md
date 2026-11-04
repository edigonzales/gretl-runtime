# Independent P6 Review — Interactive Static Job Graph

- Review ID: `8ecbf47e-9ca7-4604-8eaa-895643791a97`
- Phase: P6
- Candidate SHA: `ee3a2e8a40bf965a183bc688157d755e2f2320b2`
- Assignment base: `185fc6700b5cb2897f724d457610a57f059b3421`
- Phase base: `7ca1aa37d16c48cd685ac0427f5b0b1e42008da0`
- Requirements digest: `cb2ea98d41987604be60d1a7520c3e9e129d437cbb2b9a4a1d6f5a2e085997d3`
- Repository-rules digest: `4b8713835c73d4a4e9bae8e49984b1696fd54dbf7f3e1b74a0eda479cd46bf2b`
- Reviewer: OpenCode 2.2.5, provider `deepseek`, model `deepseek-flash`, API host `api.deepseek.com`, model version `DeepSeek-V4.1-Flash-1017`
- Verdict: **PASS**

## 1. Scope and method

I reviewed the complete P6 delta `7ca1aa37d16c48cd685ac0427f5b0b1e42008da0..ee3a2e8a40bf965a183bc688157d755e2f2320b2`
and the relevant mandatory specifications, architecture, test matrix, acceptance criteria,
implementation report, product sources, tests, build scripts, schemas and package manifests.

I did **not** re-execute either full root gate. I validated the supplied fresh raw
evidence for both gates independently, re-hashed the supplied artifacts, checked the
request/rules digests, verified the installed model identity from the supplied small
identity document, and executed only the affected targeted checks plus new counterprobes.
No tracked product source, test, lock, schema or specification was modified.

The worktree was clean apart from the pre-existing untracked `.opencode/` and
`opencode.json`:

```text
HEAD  ee3a2e8a40bf965a183bc688157d755e2f2320b2
git status --porcelain=v1
?? .opencode/
?? opencode.json
```

The fixture `project-main/job.groovy` and `project-other/job.groovy` were re-read after all
probes and are byte-for-byte unchanged. The probes sent no edits to disk; the LSP
stale-edit counterprobe changed only an in-memory `didChange` document.

## 2. Model identity and evidence provenance

`evidence/model-identity-check.json` validates the supplied
`/Users/stefan/.cache/gretl-tooling-review/p2-resume-readiness/identity.json`
(SHA-256 `5b5f5180de93622c14265dcd7cd0368f2aa468c48366fa842f6bec08b21f6fce`):

- OpenCode `2.2.5`
- provider `deepseek`, model `deepseek-flash`
- effective base URL `https://api.deepseek.com`, API host `api.deepseek.com`
- model version `DeepSeek-V4.1-Flash-1017`
- the official DeepSeek mapping text names both `deepseek-flash` and
  `DeepSeek-V4.1-Flash-1017`
- recorded actual provider/model and effective endpoint match the declared identity

The endpoint is explicitly pinned in the private project profile. Session metadata
verifies provider/model; it cannot cryptographically prove server-side weights.

Independent gate validation is recorded in `evidence/gate-validation.json`. It recomputed
the requirements/rules digests from the candidate files, checked the P6 registry
(`P6-01`…`P6-09`, `G11`…`G16`, focus `T25`/`T26`), validated all supplied artifact hashes,
and found zero problems.

## 3. Supplied root-gate evidence (validated, not re-executed)

Both gates ran in `/Users/stefan/sources/gretl-runtime` at the same candidate SHA with an
empty dirty field. I validated the supplied manifests, command logs, captured report
hashes/mtimes, local raw XML copies and artifact hashes.

| Gate | Tests (XML/declared) | Failures | Errors | Skips | Captured checks | Raw XML copies | Result |
|---|---:|---:|---:|---:|---:|---:|---|
| `root-standard` — `./mvnw -B clean verify` | 913 | 0 | 0 | 0 | 636 valid, 0 missing/mismatch | 174 valid, 0 missing | exit 0, BUILD SUCCESS, 385185 ms |
| `root-services` — `./mvnw -B -Pgeotools-probe,backing-services clean verify` | 969 | 0 | 0 | 0 | 821 valid, 0 missing/mismatch | 188 valid, 0 missing | exit 0, BUILD SUCCESS, 595101 ms |

Evidence: `evidence/root-standard/manifest.json`, `evidence/root-standard/command.log`,
`evidence/root-services/manifest.json`, `evidence/root-services/command.log`,
`evidence/gate-validation.json`.

Additional checks:

- The build logs and Surefire report properties identify Temurin **JDK 25.0.2**
  (`java.version 25.0.2`, `java.runtime.version 25.0.2+10-LTS`).
- Both gates include the real extension package build:
  `gretl-runtime.vsix` (20 files, 12.19 MB), `1..41` Node extension tests,
  `# tests 41`, `# pass 41`, `# fail 0`.
- The services gate used Docker/Testcontainers and executed the GeoTools probe module.
- The manifests' Maven log totals are exactly twice the XML totals because Maven prints
  per-class and per-module summary lines. Only zero failure/error/skip counts appear in
  every summary line; the XML totals are the authoritative test counts.
- Supplied artifacts match the request hashes, including the LSP server
  `5189670a5c28dbf86b37c9115d5514b4e2fdffb7f4e1c977e19b8512db8da80c`, the final VSIX
  `151e875bc863398d952ed1f2946290ccd6c2f46d3766d64d3037cb20068a5e7b`, the protocol schema
  and the catalog/distribution inventories.

The root gates were executed outside the review worktree in a separate repository with the
same SHA and an empty dirty field. This is the supplied candidate evidence; I did not
duplicate the full gates in the review worktree.

## 4. Targeted checks executed in this review

`evidence/required-checks-evidence.json` records the required targeted run:

```text
./mvnw -B -pl gretl-language-contract,gretl-tooling-core,gretl-lsp verify
```

- exit code 0, `BUILD SUCCESS`
- 38 Maven summary lines, every one with `Failures: 0, Errors: 0, Skipped: 0`
- 188 unique Java tests (the raw aggregate parser's 376 is the per-class plus per-module
  double count): language-contract 7, tooling-core 154, LSP 12 surefire + 15 failsafe
- Node contract/schema cases in the same build: 32 tooling-contract cases and 2 protocol
  schema cases, all passing
- final LSP failsafe summary `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`

Documentation check:

```text
node tools/gretl-tooling/check-docs.mjs
Validated 81 exact criteria
Tooling documentation links and requirement inputs valid
exit 0
```

## 5. Supplied GUI evidence and personal inspection

`evidence/gui/files.json` contains 230 entries; `evidence/gui-evidence-validation.json`
confirms all 230 sizes/hashes, all 18 current case documents and all 15 current
screenshots (valid dimensions/hashes), with zero problems. I personally opened the
returned images and AX transcripts for the current G11–G16 cases, the editor smoke, the
final-service-VSIX startup, and the reused development G14/G16 originals.

| Case | Representative evidence | Observation |
|---|---|---|
| G11 | `current/cases/graph-first-open.json` (`b2c058…`), `two-project-panels-final.json` (`2020a5…`), `pinned-source-other-editor-focused.json` (`9ecb48…`) | First open renders the real static graph; Main and Other roots remain independent; pinning a source does not replace the graph. |
| G12 | `inner-invocations-details.json` (`6feaec…`), `default-target-preview.json` (`115429…`), `single-target-preview.json` (`d30a63…`) | Project/Task/Action containers and inner invocations are explained; Parameters and Cleanup are shown separately from the task DAG; default and explicit target previews remain distinguishable from definitions. |
| G13 | `task-filter.json` (`5530ca…`), `native-drag-source-invariance.json`, `panel-zoom-restart-source-invariance.json` | Native drag moved the card (24,232 → 65.21,256.73) while paired buffer captures stayed identical; filter/zoom/fit are operable; zoom changed only one panel viewport and source stayed invariant across restart. |
| G14 | `diagram-to-source.json` (`b1dbe3…`), `source-to-diagram.json` (`a72fad…`), development `result-binding-navigation.json` (`e5b178…`) | Node/edge reveal opens the exact source range; source selection selects the corresponding graph node; result-binding navigation is covered. |
| G15 | `dirty-partial-diagnostics.json` (`19f58c…`), `server-stopped-stale-panels.json` (`ea2282…`), `restart-fresh-panels.json` (`e61998…`), `panel-close-reopen-persistence.json` | Dirty/partial diagnostics are shown honestly; stopped server yields stale panels; restart yields fresh panels; close/reopen preserved viewport and node positions with unchanged source. |
| G16 | current `two-project-panels-final.json`; development `light-long-title-fixed.json` (`d264e1…`), `high-contrast-controls-fixed.json` (`261de8…`), `empty-source-panel.json` (`8e3f9d…`), `dark-empty-partial.json` (`cb26f1…`) | Light, high-contrast, long-title, empty and partial states are readable; see the transparent-reuse note below. |
| Editor smoke | `current/cases/editor-native-hover.json` (`27297e…`), `current/captures/capture-001.json` | Native hover, completion and outline are real editor behavior from the actual extension/server path. |

My own current-candidate probes are documented in section 6. In addition, the supplied
`current/cases/final-service-vsix-startup.json` (`96bdac…`) proves that the exact final
service VSIX `151e875b…` starts with the final server `5189670a…` and the same candidate.

## 6. New counterprobes

### 6.1 Real LSP wire identity, target preview and stale rejection

Source: `evidence/probe-lsp.mjs`; result: `evidence/counterprobe-lsp.json`.

The probe starts the actual supplied LSP jar
(`5189670a5c28dbf86b37c9115d5514b4e2fdffb7f4e1c977e19b8512db8da80c`) over stdio against
the private two-project fixture, then asserts on the wire:

- Main definitions graph is `COMPLETE`: 7 nodes, 5 edges; `:download` is a `TASK`.
- Main target preview for `:done` is available and ordered
  `[:download, :extract, :sub:child, :done]`, a subset of the definition nodes.
- Unqualified target `done` is rejected honestly with `available:false`,
  `reason:UNKNOWN_TARGET`, empty `orderedSymbols` (no fabricated target).
- Other graph is `COMPLETE` with one `ACTION` named `download`; it has a different node
  identity from Main's `TASK :download`.
- Offset navigation selects the Main task or the Other action respectively and never
  returns the other project's node.
- After an in-memory source edit, navigation with the old snapshot fails with
  `ContentModified` / `SNAPSHOT_MISMATCH`; it does not return a stale location.

All 21 recorded assertions pass. This covers P6-01, P6-03, P6-06 and P6-07 identity
boundaries on the real protocol path, not a mock.

### 6.2 Native same-name cross-panel selection probe

The supplied G14 navigation cases cover one panel at a time. This new native probe adds
the cross-project, same-name, two-visible-panel case that a single-panel test cannot
observe.

Actions (GUI bridge `004`, `008`, `009`, `010`):

1. Pin the Main modeler and the Other modeler side by side.
2. Activate the exact background Main text tab in the left group while the Other modeler
   remains visible in the right group.
3. `find task('download'`, `Escape`, then invoke `GRETL: Show Selection in Modeler`.
4. Two Main panels existed from the repeated setup; the command honestly presented the
   duplicate-panel QuickPick. I selected the Main panel in **editor group 1** (`Down`,
   `Return`) and observed the final state.

Expected: the chosen exact-document Main panel selects its `TASK :download`; the Other
project's modeler and its same-named `ACTION download` remain unselected, with Reveal
selection disabled; no cross-project node is selected.

Observed (`responses/010.png`, hash `bddd88b6e22c9e702b3bd4fcadbf26d853b934acebed79b2a4a159b7240a6ca2`):

- left (Main) panel: `TASK :download` selected with the blue selection outline;
  `Reveal selection` enabled
- right (Other) panel: `ACTION download` unchanged; `Reveal selection` disabled
- both panels remain visible side by side; the Other graph viewport is unchanged from the
  pre-command capture
- the Main text buffer was not edited; only find/selection and panel reveal occurred

This is a genuine native GUI counterprobe, not a transport-only assertion. I opened the
returned PNG and read the AX tree myself.

### 6.3 Native current-candidate empty view

The supplied G16 empty-source image is a development original (`sourceDirty=true`), so I
added a current-candidate probe (`request 011`, `response 011`):

1. Open the exact `project-main/empty.groovy` text.
2. Run `GRETL: Open Modeler`.
3. Observe the fresh panel.

Observed (`responses/011.png`, hash
`393dd61dfdb45227cb6127536503cb69ff09c869d0889d0710eb8a4165a979fd`):

- status `EMPTY`
- message `No statically known task declarations. Parameters, cleanup and diagnostics remain available.`
- no fabricated Task/Action node
- the Other modeler beside it remains `FRESH` and unchanged

This closes the only residual current-image gap in G16 for the empty state.

## 7. Mandatory P6 criteria

### P6-01 — Graph only from the current language-core snapshot; all static declarations; definitions vs target preview

**Fulfilled.**

- `TargetPreviewProjector` derives the target closure from the shared core semantics and
  keeps the definition graph complete.
- `TargetPreviewProjectorTest` covers the data closure, After-only nodes, defaults,
  explicit/unknown targets, unchanged definitions, dynamic ignorance and cancellation.
- `ServerProcessIT#targetPreviewIsOptionalAndUsesTheSharedDataClosureOnTheActualWire`
  verifies optional preview, complete definitions and honest `UNKNOWN_TARGET` on the real
  process boundary.
- Counterprobe 6.1 confirms 7 definition nodes, an ordered target closure and an empty
  ordered list for an unknown target.
- GUI `graph-first-open`, `default-target-preview` and `single-target-preview` show the
  definitions view and target preview as separate modes.

Evidence: `evidence/counterprobe-lsp.json`, `evidence/gui/current/cases/graph-first-open.json`,
`evidence/gui/current/cases/default-target-preview.json`,
`evidence/gui/current/cases/single-target-preview.json`,
`gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/graph/TargetPreviewProjector.java`,
`gretl-tooling-core/src/test/java/ch/so/agi/gretl/tooling/edit/TargetPreviewProjectorTest.java`,
`gretl-lsp/src/test/java/ch/so/agi/gretl/lsp/ServerProcessIT.java`.

### P6-02 — Correct representation of Tasks/Actions/project containers and parameter/cleanup/dynamic areas; no invented parallel tasks

**Fulfilled.**

- `view.tsx` maps `TASK`, `ACTION`, `PROJECT`, `DYNAMIC_CODE`, `INNER_INVOCATION`,
  `PARAMETER` and `CLEANUP` distinctly; Parameters and Cleanup are rendered in separate
  sections labelled "Separate from the task DAG".
- Inner invocations are explanatory children, not independently scheduled nodes.
- `inner-invocations-details` shows the `Inner invocation HttpDownload` container plus
  separate Parameters/Cleanup sections.
- `dirty-partial-diagnostics` shows dynamic-code regions and partial diagnostics without
  promoting them to tasks.
- `two-project-panels-final` shows the two roots with their own correct task/action sets.
- The compound-card geometry regression log records the fix for overlapping compound
  children.

Evidence: `evidence/gui/current/cases/inner-invocations-details.json`,
`evidence/gui/current/cases/dirty-partial-diagnostics.json`,
`evidence/gui/current/cases/two-project-panels-final.json`,
`evidence/p6-compound-card-geometry-fixed.log`,
`vscode/gretl-vscode/webview/view.tsx`,
`vscode/gretl-vscode/src/modeler/graph-model.ts`.

### P6-03 — dependsOn/after/result bindings separated, producer→consumer correct; partial/unresolved/diagnostic honesty

**Fulfilled.**

- The webview renders three distinct edge classes and a matching legend; the graph model
  preserves the edge kind and producer/consumer endpoints.
- `graph-first-open` shows `dependsOn`, `after · order only` and `file → archive`
  result-binding edges in one graph.
- The development result-binding navigation case remains an original, labelled
  development record; the final client payload is separately compared.
- `dirty-partial-diagnostics` shows the partial/invalid state with diagnostics and
  dynamic code, without pretending the graph is complete.
- The default-target preview and unknown-target behavior are honest (no fabricated
  closure).

Evidence: `evidence/gui/current/cases/graph-first-open.json`,
`evidence/gui/current/cases/dirty-partial-diagnostics.json`,
`evidence/gui/current/cases/default-target-preview.json`,
`evidence/gui/development-originals/cases/result-binding-navigation.json`,
`vscode/gretl-vscode/webview/view.tsx`,
`vscode/gretl-vscode/src/modeler/graph-model.ts`.

### P6-04 — Real CustomTextEditor with option priority, local React Flow nodes and ELK worker; no static SVG or second server

**Fulfilled.**

- `package.json` registers `gretlRuntime.modeler` for `*.groovy` and `settings.gretl`
  with `"priority": "option"`.
- `provider.ts` registers a real `CustomTextEditorProvider`, uses a webview panel,
  local `dist/modeler` resources and the extension's own backend.
- `view.tsx` renders React Flow 12 / React 19 nodes, with a bundled local
  `elk-worker.min.js`; `build-webview.mjs` packages those assets.
- The root gates build and package the VSIX (20 files, 12.19 MB) and run 41 extension
  tests; the distribution test checks the packaged payload.
- `graph-first-open` is the actual webview, not a static image; the native drag probe
  moves a real React Flow node.

Evidence: `vscode/gretl-vscode/package.json`,
`vscode/gretl-vscode/src/modeler/provider.ts`,
`vscode/gretl-vscode/webview/view.tsx`,
`vscode/gretl-vscode/scripts/build-webview.mjs`,
`vscode/gretl-vscode/test/package/distribution.test.mjs`,
`evidence/gui/current/cases/graph-first-open.json`,
`evidence/root-standard/command.log`.

### P6-05 — Drag, pan/zoom/fit, selection and simple filters are operable and never change source/order/dirty state

**Fulfilled.**

- Native drag moved the card from `(24,232)` to `(65.21,256.73)`; paired buffer captures
  show source invariant.
- Zoom changed one panel viewport; the other stayed unchanged; source invariant across a
  real server restart.
- Task filter is a real checkbox interaction; `Reset layout` and fit are wired.
- The webview persists only view positions/viewports; source edits are not part of any
  drag/filter/layout path. The footer states this, and the view code only saves view
  state.
- My probe selected a node and invoked reveal; the buffer remained unedited.

Evidence: `evidence/gui/current/cases/native-drag-source-invariance.json`,
`evidence/gui/current/cases/task-filter.json`,
`evidence/gui/current/cases/panel-zoom-restart-source-invariance.json`,
`evidence/gui/current/captures/buffers-1793780004516.json`,
`evidence/gui/current/captures/buffers-1793780012982.json`,
`evidence/gui-bridge/responses/010.json`.

### P6-06 — Source↔diagram navigation and live updates use exact current identities; rapid edits/restart/panel changes discard old answers

**Fulfilled.**

- `provider.ts` checks the graph snapshot, source SHA-256 digest, document identity and
  cancellation token before opening source; stale navigation is invalidated.
- `ServerProcessIT` and `probe-lsp.mjs` verify offset navigation and stale rejection on
  the real wire.
- `diagram-to-source` and `source-to-diagram` show both navigation directions.
- My native probe selected the Main `TASK :download` while the Other `ACTION download`
  stayed unselected, proving exact-document/source identity across two visible panels.
- Counterprobe 6.1 rejects a stale snapshot with `SNAPSHOT_MISMATCH`.
- Restart/panel cases show fresh panels and discarded old state.

Evidence: `evidence/counterprobe-lsp.json`, `evidence/probe-lsp.mjs`,
`evidence/gui/current/cases/diagram-to-source.json`,
`evidence/gui/current/cases/source-to-diagram.json`,
`evidence/gui-bridge/responses/010.json`,
`evidence/gui/current/cases/restart-fresh-panels.json`,
`vscode/gretl-vscode/src/modeler/provider.ts`,
`gretl-lsp/src/test/java/ch/so/agi/gretl/lsp/ServerProcessIT.java`.

### P6-07 — View state separate from graph/source; unique identity only; multiple panels and cleanup

**Fulfilled.**

- `ViewStateStore` persists bounded positions/viewports under a key derived from the
  document, pinned source and view slot, not the semantic graph.
- Tests cover bounded storage, identity, stale/foreign keys and multiple slots.
- `panel-close-reopen-persistence` preserved positions/viewport with unchanged source.
- `same-source-panel-independence` shows two panels on the same document with independent
  viewports.
- `two-project-panels-final` and my probe show independent panels for different roots.
- Disposal removes the panel from the set and cancels pending work; the controller test
  covers late navigation after disposal.

Evidence: `vscode/gretl-vscode/src/modeler/view-state-store.ts`,
`vscode/gretl-vscode/test/modeler-storage.test.mjs`,
`vscode/gretl-vscode/test/modeler-controller.test.mjs`,
`evidence/gui/current/cases/panel-close-reopen-persistence.json`,
`evidence/gui/development-originals/cases/same-source-panel-independence.json`,
`evidence/gui/current/cases/two-project-panels-final.json`,
`evidence/gui-bridge/responses/010.json`.

### P6-08 — CSP, local assets, validated messages, safe labels, themes, empty/partial/stale views

**Fulfilled.**

- The custom editor sets a restrictive CSP: no remote script/style/connect origin, nonce
  for the local script, `worker-src blob:` only, local resources only.
- Webview messages are validated for panel ID, protocol version, kind, snapshot and
  bounded view state; unknown messages are ignored.
- Source-derived labels are rendered as React text and asset URLs are escaped; no HTML
  injection path for source titles.
- Light, high-contrast and dark long-title/empty/partial states are readable in the
  supplied captures; the reused development originals are limited to byte-identical
  client assets (see below).
- `server-stopped-stale-panels` shows the stale state; `dirty-partial-diagnostics` shows
  partial/invalid; my current `011` probe shows `EMPTY` with the honest message.
- The distribution test and root packaging prove local bundled assets.

Evidence: `vscode/gretl-vscode/src/modeler/provider.ts`,
`vscode/gretl-vscode/src/modeler/graph-model.ts`,
`vscode/gretl-vscode/webview/view.tsx`,
`vscode/gretl-vscode/test/modeler-resource.test.mjs`,
`vscode/gretl-vscode/test/package/distribution.test.mjs`,
`evidence/gui/current/cases/dirty-partial-diagnostics.json`,
`evidence/gui/current/cases/server-stopped-stale-panels.json`,
`evidence/gui/development-originals/cases/light-long-title-fixed.json`,
`evidence/gui/development-originals/cases/high-contrast-controls-fixed.json`,
`evidence/gui/development-originals/cases/empty-source-panel.json`,
`evidence/gui/development-originals/cases/dark-empty-partial.json`,
`evidence/gui-bridge/responses/011.json`.

### P6-09 — G11–G16 and editor smoke with current images/geometry/source evidence; affected core/LSP/UI tests and required root gates green

**Fulfilled.**

- `evidence/gui/index.json` and `gui-evidence-validation.json` cover G11–G16 and the editor
  smoke; all 230 manifest entries, 18 current cases and 15 current screenshots validate.
- I personally inspected the current images/AX and added two new current native probes
  (cross-panel selection, empty view).
- The targeted Java/Node checks and documentation check pass with zero failures/skips.
- Both supplied full root gates are exit 0 with 913/969 XML tests, zero
  failures/errors/skips, BUILD SUCCESS and JDK 25.0.2.
- The final service VSIX `151e875b…` cold-starts with the final server `5189670a…` and
  candidate SHA `ee3a2e8a…`.

Evidence: `evidence/gui/index.json`, `evidence/gui-evidence-validation.json`,
`evidence/gui/current/cases/editor-native-hover.json`,
`evidence/gui/current/captures/capture-001.json`,
`evidence/required-checks-evidence.json`, `evidence/gate-validation.json`,
`evidence/root-standard/manifest.json`, `evidence/root-services/manifest.json`,
`evidence/gui/current/qualification.json`.

## 8. Non-blocking observations (no mandatory finding)

1. **Screenshot container metadata.** All 15 current GUI screenshots are JPEG byte streams
   stored with `.png` extensions. They decode to valid images, dimensions and hashes match
   `files.json`/the case JSONs, and I inspected the rendered content. This is an
   evidence-metadata quirk only, not a product-payload or rendering failure.
2. **G16 transparent reuse.** The G16 light/high-contrast/empty/dark-partial/cold-start
   originals are from development (`sourceDirty=true`, commit `7ca1aa…`). The reuse
   comparison proves `extension.cjs`, `view.js`, `view.css` and `elk-worker.min.js` are
   byte-identical to the current client payload. Current native G11–G15, two-panel G16
   smoke, restart/close-reopen and exact final-service-VSIX startup are separately current.
   My own current `011` probe additionally covers the empty state. The provenance is
   explicit and was not relabelled.
3. **Standard vs service VSIX container.** The current G11–G15 case JSONs were captured
   with the standard gate VSIX `283fd089…`; the final service VSIX `151e875b…` is
   cold-started separately. The ZIP payload entries are byte-identical; only container
   metadata differs, and both use the same final server `5189670a…` and candidate SHA.
   This is documented artifact packaging, not a functional divergence.
4. **Duplicate-panel QuickPick in the probe.** Repeated setup produced two Main modeler
   panels, so `GRETL: Show Selection in Modeler` correctly offered a choice. Selecting
   editor group 1 revealed the intended panel; the other same-document panel stayed
   backgrounded and the other project's panel stayed unselected. This exercises supported
   `supportsMultipleEditorsPerDocument` behavior, not a defect.
5. **Target-preview closure filtering.** The target-preview UI lists TASK-namespace
   symbols in the closure; symbols outside the closure are intentionally hidden. The
   unqualified `done` request is rejected as `UNKNOWN_TARGET` rather than silently mapped.
   This is the specified honest behavior, not a missing declaration in the definitions
   view.

## 9. Limitations

- The full root gates are supplied evidence from `/Users/stefan/sources/gretl-runtime`
  (same candidate SHA, empty dirty field), not runs repeated in this review worktree.
  I validated their manifests, logs, captured hashes/mtimes, raw XML copies and artifacts.
- The model identity proves the configured provider/model and endpoint; it cannot
  cryptographically prove server-side model weights.
- The reused G16 development originals are accepted only for byte-identical client
  behavior; current candidate empty and navigation behavior was probed natively.
- No Docker service execution was repeated in this review; the supplied services gate log
  proves Docker/Testcontainers execution and the GeoTools probe.

## 10. Final state

- HEAD remains `ee3a2e8a40bf965a183bc688157d755e2f2320b2`.
- `git status --porcelain=v1` contains only the pre-existing untracked `.opencode/` and
  `opencode.json`.
- No tracked product source, test, lock, schema or specification was modified.
- The private fixture sources are unchanged.
- Reviewer-spawned Maven/Node processes finished before this report; no reviewer child
  process remains.
- All reviewer evidence is under
  `/Users/stefan/.cache/gretl-tooling-review/P6-review-1/evidence/`; the formal verdict is
  `verdict.json`.

## Metadata correction after outer validation

The first submission of this complete review was rejected by the outer validator with
`Evidence must be an absolute local path: docs/implementation/gretl-tooling/implementation/P6.md`.
The review's technical work, evidence, PASS judgment and all identities remain unchanged.
Only the machine-readable `evidence` arrays in `verdict.json` were corrected: every entry
is now an absolute path to an existing regular local file. Repository-relative references
and `#anchor` qualifiers that remain in this report are explanatory prose; anchors were
removed from the machine-readable evidence entries. No test, root gate, GUI case or npm
task was re-executed for this correction, and no supplied run was relabelled as new.

This file is the complete original `review.md` plus this metadata note. The formal revised
pair is `resume-1/review.md` and `resume-1/verdict.json`; the original `review.md`,
`verdict.json`, `process.json` and `validation.json` remain untouched in the parent
evidence directory.
