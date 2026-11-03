# P3 independent review — Queries, Graphprojektion und verlustarme Editplanung

- **Candidate:** `8a38cfba4559b9704c27f45fed3b33937944de08`
- **Phase:** P3
- **Assignment base:** `185fc6700b5cb2897f724d457610a57f059b3421`
- **Phase base:** `6b1487fcc4dfef9516b0791182b920a50616337e`
- **Review ID:** `d61bdb20-9e71-48c7-af6b-dc6ab11cebec`
- **Verdict:** **FAIL**

This review is read-only. No tracked product source, test, lock, schema or specification was modified. The supplied fresh root-gate raw evidence was validated from the immutable artifacts; neither complete root gate was repeated. The affected checks and independent counterprobes were executed against the candidate worktree.

## 1. Identity, request and supplied evidence

The request schema, nine P3 criteria, requirement digest (`cb2ea98d…97d3`), repository-rules digest (`4b871383…bf2b`), artifact hashes and reviewer identity were validated programmatically (`evidence-verification.json`). The reviewer identity is OpenCode `2.2.5`, provider `deepseek`, model `deepseek-flash`, host `api.deepseek.com`, model version `DeepSeek-V4.1-Flash-1017`, checked within the required 24-hour window. All eight supplied artifacts (language-contract, tooling-core, catalog, dependency graphs, operations data/schema and catalog manifest) have the declared SHA-256 values.

The working tree is at the candidate SHA and has no tracked changes. `tools/gretl-tooling/node_modules` was created by the required Maven `test` phase (`npm ci` in the module reactor); it is git-ignored and does not change tracked state. The review harness files `.opencode/` and `opencode.json` are untracked and were not created by this review.

## 2. Supplied root gates — raw validation only

Both manifests and every captured XML/text artifact were re-hashed and re-counted with the trusted `verifyCurrentGateManifest` logic (the review copy only rewrites the module root and the `ajv` specifier to the already-installed dependency):

| Gate | SHA | Dirty | Exit | Tests | Failures | Errors | Skipped | Raw artifacts |
|---|---|---|---|---|---|---|---|---|
| `./mvnw -B clean verify` | `8a38cf…de08` | empty | 0 | 880 | 0 | 0 | 0 | 623 |
| `./mvnw -B -Pgeotools-probe,backing-services clean verify` | `8a38cf…de08` | empty | 0 | 936 | 0 | 0 | 0 | 808 |

The XML test counts sum exactly to the manifest counts; every captured file hash, timestamp and manifest timestamp window verified. Both command logs end in `BUILD SUCCESS`. The manifests record the primary checkout `/Users/stefan/sources/gretl-runtime`; its HEAD, dirty state and candidate SHA were checked by the supplied request and are the same candidate. This cwd difference is a metadata fact, not a reason to rerun the gates; the immutable raw evidence is internally consistent and tied to this SHA.

The supplied documentation evidence (`tooling-dependencies`, generated reference, documentation tests, link check, Quarto render) is exit 0 for the same SHA.

## 3. Affected checks executed by this review

| Check | Command | Result |
|---|---|---|
| `tooling-targeted` | `./mvnw -B -pl gretl-groovy -am verify` | exit 0, **475 Java tests**, 0 failures, 0 errors, 0 skips; the reactor also ran the 32 Node tooling tests |
| `tooling-docs` | `node tools/gretl-tooling/check-docs.mjs` | exit 0; 81 exact criteria, documentation links and requirement inputs valid |

Raw logs and machine-readable results are in `tooling-targeted.log`, `tooling-targeted.json`, `tooling-docs.log` and `tooling-docs.json`. No complete root gate was launched.

## 4. Criteria assessment

- **P3-01 — fulfilled.** Completion is AST/context-driven, uses whole string-literal spans and never searches comments/opaque regions. Tests cover operations, options, fields, enums, booleans, parameter/result references and captured job paths. Evidence: `LanguageFeatureQueriesTest.java`, `CompletionQueries.java`, targeted log.
- **P3-02 — fulfilled.** Hover, signatures, outline, folding, file links, definitions/references and graph projection consume the same `AnalysisSnapshot`; ambiguous targets stay multiple and incomplete coverage is reported. Evidence: `SnapshotNavigationTest.java`, `JobGraphProjectorTest.java`, `LanguageQueries.java`.
- **P3-03 — fulfilled.** Rename handles literal references, TaskRef bindings, settings/defaults, parameter namespaces and project-qualified forms; aliases, dynamic uses, readonly documents and incomplete coverage are refused rather than partially edited. The new read-only reference-document counterprobe (CP2) confirms `EDIT_READONLY` with no plan. Evidence: `ReferenceSourceEditsTest.java`, `EditabilityAnalyzerTest.java`, `counterprobe.log`.
- **P3-04 — violated.** The individual intents exist and are tested, but `SetField` / `planConfiguration` can publish a plan containing a result-field reference whose producer output is not port-compatible with the consumer input. The dedicated `BindResult` intent rejects the same binding with `EDIT_PORT_COMPATIBILITY`; the field path bypasses that check. Finding **P3-F1**.
- **P3-05 — violated (related).** `after` and `dependsOn` remain distinct and transitive data rights are respected on the tested dependency/binding paths. However, the result-binding semantic guard is bypassed when the same result field reference is supplied through `SetField`/`planConfiguration`, so the “result binding creates the correct header/field changes together with a valid port” contract is not uniformly enforced. Finding **P3-F1** relates here.
- **P3-06 — fulfilled.** Delete and disconnect are reference- and coverage-guarded, no cascade is hidden, and removing a still-needed result dependency is rejected, including downstream transitive access. Evidence: `ReferenceSourceEditsTest.java`, `DependencySourceEditsTest.java`, targeted log.
- **P3-07 — fulfilled.** Edits preserve comments, CRLF/EOL, opaque code and untouched text; string quoting is token-aware and never doubled; no whole-file regeneration path exists. Evidence: `FieldSourceEditsTest.java`, `DeclarationSourceEditsTest.java`, `HeaderSourceEditsTest.java`, CP1 log.
- **P3-08 — violated.** Plans carry source, project, configuration and catalog preconditions and are applied/reparsed in memory. But the postimage check does not include the port-compatibility guard for result references written by `SetField`; a semantically incompatible postimage is accepted as an allowed plan. Finding **P3-F1**.
- **P3-09 — fulfilled for gates and broad coverage.** The complete repository gates pass (880/936) and all required/affected checks pass; the runtime parity and no-execution tests are present. The missing negative case for this specific `SetField` port path is part of finding P3-F1’s correction, not a separate gate failure.

## 5. Open mandatory finding

### P3-F1 — `SetField`/`planConfiguration` bypass the port-compatibility guard

**Severity:** major  
**Criteria:** P3-04, P3-05, P3-08  
**Source:** `gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/edit/FieldSourceEdits.java`, `SemanticEditPlanner.java`; contrast `DependencySourceEdits.bind` and `PortCompatibility.java`.

**Reproduction (independent probe `P3PortProbe.java`):** build a catalog with a producer `Source` whose output `file` has shape `path` and a consumer `Sink` whose required input `input` has shape `text`; use the job

```groovy
task('p', Source) {}; task('c', Sink, dependsOn:'p') { input='old' }
```

On the same snapshot:

- `SemanticEditIntent.BindResult(p, "file", c, "input")` → `REJECTED`, reason `EDIT_PORT_COMPATIBILITY`.
- `SemanticEditIntent.SetField(c, "input", new EditableValue.ResultFieldRef(p, "file"))` → `ALLOWED`, postimage `input=result('p').file`.
- `SemanticEditPlanner.planConfiguration(c, Map.of("input", ResultFieldRef(p,"file")), …, Map.of("dependsOn", Literal("p")), …)` → `ALLOWED`, same postimage.

The raw observation is in `port-probe.log`:

```text
BindResult state=REJECTED code=EDIT_PORT_COMPATIBILITY
SetField state=ALLOWED code=<none>
SetField postimage=... input=result('p').file ...
planConfiguration state=ALLOWED code=<none>
planConfiguration postimage=... input=result('p').file ...
```

**Expected:** every intent that writes a result-field reference must apply the same data-schema/port check as `BindResult` (and reject nullable/unknown/Opaque outputs), or `SetField` must reject `ResultFieldRef` and require `BindResult`. A form confirmation must not publish an allowed plan for a binding that the dedicated binding intent rejects.

**Observed:** `FieldSourceEdits.set` writes `ResultFieldRef` values and only checks structural equality in the postcondition; `SemanticEditPlanner.planConfiguration` composes those field intents. `PortCompatibility.require` is called by `BindResult` and by `AddTask` initial-field dependency collection, but not here. The postimage analyzer does not diagnose the path/text mismatch, so the plan is accepted.

**Required correction:** invoke `PortCompatibility.require` (and the dependency collection where needed) for every `ResultFieldRef` in `SetField`/`planConfiguration`, or disallow that value in `SetField` and route it through `BindResult`. Add positive and negative tests for compatible and incompatible result-field values through both the direct SetField and the form-configuration path.

## 6. Independent counterprobes

- **P3-CP1 — duplicate/opaque isolation plus safe header edit (passed).** A task with duplicate `input` assignments and an opaque `mode=loadDynamicValue()` rejects edits to those two targets (`EDIT_MULTIPLE_ASSIGNMENTS`, `EDIT_UNSAFE_REGION`) while an unrelated `description` option is allowed; the duplicate assignments, opaque call and CRLF bytes remain byte-identical, and the plan is current only for its original snapshot. Evidence: `P3CounterProbe.java`, `counterprobe.log`.
- **P3-CP2 — read-only reference-document rename boundary (passed).** Renaming a project task referenced from a read-only second job is rejected with `EDIT_READONLY` and no plan; making that job editable produces one plan touching settings and both jobs with exact `:a:renamed` references and no doubled quotes. Evidence: `P3CounterProbe.java`, `counterprobe.log`.
- **P3-CP3 — result port-bypass counterprobe (failed, exposes P3-F1).** The incompatible `SetField`/`planConfiguration` route is accepted while `BindResult` is rejected; see section 5. Evidence: `P3PortProbe.java`, `port-probe.log`, `run-port-probe.mjs`.

## 7. Residual notes and limits

- The host-transaction limitation documented in the phase report remains: the pure core returns a verified in-memory plan and does not perform an atomic filesystem transaction with external writers. This is an honest boundary, not a hidden claim.
- `PlannedEdit.isCurrent` is snapshot-bound (`snapshotId` included). A new capture with identical text is treated as stale; this is conservative and consistent with the tests, though hosts must recapture and replan after any workspace event.
- No GUI or P4 work is in scope. The phase report/status documentation remains in an in-progress state until an independent PASS; that is expected.
- The supplied root-gate manifests were generated in the primary checkout rather than the review worktree. Their SHA, clean state, raw XML counts, timestamps and hashes are consistent; I did not treat this metadata difference as evidence of a different candidate.

## Verdict

**FAIL.** The implementation is broadly complete and the supplied gates are valid, but P3-F1 is a concrete semantic-safety hole: the modeler/field path can publish an allowed plan for a result binding that the dedicated binding path correctly rejects as port-incompatible. This must be corrected and independently re-reviewed before P3 can pass.
