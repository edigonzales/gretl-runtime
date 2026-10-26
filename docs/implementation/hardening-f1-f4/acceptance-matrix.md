# Abnahmematrix – Spezifikation 1.1

Ausgangspunkt: `bc044302829ede67ead27038bfdf2be90d0b0063`. Spezifikationshash: `2b5df1aaaaf614184f26b657be1e4b3c567f9ee5c393df09c1faf9d169febeab`.

F1-Produktkorrektur ist ALREADY_FIXED; Plattformbelege sind ergänzt. Gate A für `b7ada7598e2750112836162827c36d15586aacee` ist unabhängig PASS. Phase B benötigt noch Gesamtqualifikation und Gate B. Testfallkennungen sind Orakel und dürfen mehrere bestehende Tests verwenden. Historische Berichte bleiben unverändert.

| Prüffall | Konkreter Nachweis | Gate/Status |
| --- | --- | --- |
| TC-F1-01 | HardeningLocalJobIT.preservesExistingRootsAndProtectsOwnedRunAndDiagnostics | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F1-02 | HardeningLocalJobIT; RunReportWriterTest | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F1-03 | WorkspacePlatformTest.deniedPosixRootIsNotRepaired; Linux PosixQualification | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F1-04 | WorkspaceOwnershipTest.createsPrivateOwnedDirectoriesAndLeavesExistingWorkRootAlone | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F1-05 | StartBoundaryTest; WorkspaceOwnershipTest | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F1-06 | WorkspacePlatformTest; WorkspaceManager atomic attributes | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F1-07 | WorkspacePlatformTest.configuredSymlinkRootPreservesTargetMetadataAndForeignSibling | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F1-08 | DiagnosticFilePermissionsTest; DiagnosticPosixQualification | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F1-09 | WorkspacePlatformTest.nonPosixZipFileSystemUsesPortableCreationAndStillRejectsCollisions | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F1-10 | WorkspaceRetentionTest; WorkPathOwnershipTest | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-01 | LocalBudgetRegressionTest.actionReturnAfterDeadlineCannotPublishAValue; ScopedControlTest.taskBudgetIncludesSynchronousStartObservation | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-02 | LocalBudgetRegressionTest.caughtLocalCancellationStillBlocksNewCallsAndReturn | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-03 | LocalBudgetRegressionTest.expiredPredicateCancelsBeforeBodyEvenWhenItReturnsFalse | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-04 | LocalBudgetRegressionTest.expiredConfigurationDoesNotConstructAHandler | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-05 | ScopedControlTest.successiveInvocationsShareTheOriginalDeadlineAndFreezeEarlierSuccess | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-06 | RunControlTest.childBudgetIsBoundedByParentAndLocalLimit; ScopedControlTest.purePollingDoesNotChangePrecedenceOrInterrupt | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-07 | LocalBudgetRegressionTest; HardeningLocalJobIT | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-08 | BudgetPrecedenceTest; ScopedControlTest.purePollingDoesNotChangePrecedenceOrInterrupt | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-09 | BudgetPrecedenceTest.explicitReleaseCancellationKeepsNestedCausesAndLaterCloseErrors | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-10 | LocalBudgetRegressionTest.timeoutDuringInvocationCloseIsStickyAndAllResourcesClose; BudgetPrecedenceTest | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-11 | ScopedControlTest.twoCleanupsShareOneGraceBudgetAfterWorkingTimeout | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-12 | BudgetPrecedenceTest.interruptedInvocationStillRunsCleanupAndRestoresInterruptOnReturn | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-13 | BudgetPrecedenceTest.deliberatelyCaughtInvocationFailureAllowsSuccessfulParent | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-14 | RunControlTest; SequentialJobRunnerTest | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-15 | ScopedControlTest.closedTaskAndInvocationHandlesKeepTheirOwnSnapshot | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-16 | ScopedControlTest.closedTaskAndInvocationHandlesKeepTheirOwnSnapshot; cancelledHandleRetainsItsCauseAndBudgetAfterRunEnd | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-17 | ScopedControlTest.purePollingDoesNotChangePrecedenceOrInterrupt | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-18 | CancelCauseReportTest; HardeningLocalJobIT.concreteControlCauseSurvivesTheProductEntry | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-19 | ScopedControlTest.successiveInvocationsShareTheOriginalDeadlineAndFreezeEarlierSuccess; SequentialJobRunnerTest.cancellationBeforeTheFirstTaskMarksEverythingNotRun | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F2-20 | BudgetPrecedenceTest; CancelCauseReportTest | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F3-01 | SetupFailureTest.taskWorkspaceFailureIsTerminalAndPreservesSetupCauseBeforeCleanupErrors; HardeningLocalJobIT | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F3-02 | SetupFailureTest.lostWorkspaceAfterRunStartStillAllowsExternalCleanup | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F3-03 | StartBoundaryTest; SetupCompletionTest.exclusiveLeafFailureIsCodeTwoAndNeverCallsCleanupOrPublishesRunEvents; HardeningLocalJobIT.failedStartHasNoRunReportOrCleanupEffects | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F3-04 | SetupFailureTest; OperationInvocationRunnerTest.cleanupRunsInReverseOrderAndFailureAfterSuccessFailsTheRun | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F3-05 | SetupFailureTest.lostWorkspaceAfterRunStartStillAllowsExternalCleanup; cleanupWithoutFilesDoesNotOpenItsWorkspaceAndFileCleanupFailureIsSecondary | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F3-06 | SetupFailureTest.lostWorkspaceAfterRunStartStillAllowsExternalCleanup; WorkPathOwnershipTest | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F3-07 | HardeningLocalJobIT.setupFailureStillCleansUpAndReportsTheOriginalCause | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F3-08 | SetupCompletionTest.brokenExplicitReportParentDoesNotPreventTaskAndCleanupCompletion; HardeningLocalJobIT | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F3-09 | SetupFailureTest.invocationSetupFailureHasOrdinaryCatchSemanticsAndItsOwnHistory | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F3-10 | HardeningLocalJobIT.assertTerminal; SetupFailureTest | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F3-11 | ContextAccessTest; ScopedControlTest; SetupCompletionTest | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F3-12 | HardeningLocalJobIT.setupFailureStillCleansUpAndReportsTheOriginalCause | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-F4-01 | LibraryScopeTest.tasksAndNestedCallsUseTheirActualIdentitiesThroughClose; LibraryScopeLocalJobIT | B / Komponenten PASS, Gesamtgate ausstehend |
| TC-F4-02 | LibraryScopeTest.tasksAndNestedCallsUseTheirActualIdentitiesThroughClose; LibraryScopeLocalJobIT | B / Komponenten PASS, Gesamtgate ausstehend |
| TC-F4-03 | LibraryScopeTest.tasksAndNestedCallsUseTheirActualIdentitiesThroughClose; LibraryScopeLocalJobIT outer-before/after | B / Komponenten PASS, Gesamtgate ausstehend |
| TC-F4-04 | LibraryScopeTest.preparationFailureIsCorrelatedAndCatchRestoresTaskScope; LibraryScopeLocalJobIT provider-config/factory/close | B / Komponenten PASS, Gesamtgate ausstehend |
| TC-F4-05 | LibraryScopeTest.tasksAndNestedCallsUseTheirActualIdentitiesThroughClose; LibraryScopeLocalJobIT caught | B / Komponenten PASS, Gesamtgate ausstehend |
| TC-F4-06 | LibraryScopeTest.failureAndTimeoutReleaseScopesBeforeCleanupAndTheNextRun | B / Komponenten PASS, Gesamtgate ausstehend |
| TC-F4-07 | LibrarySnapshotTest.nestingRestoresOnlyReservedKeysAndKeepsForeignChanges; LibraryScopeLocalJobIT foreign.provider | B / Komponenten PASS, Gesamtgate ausstehend |
| TC-F4-08 | LibrarySnapshotTest.deferredEventKeepsOriginSnapshotAndTimestampDuringAnotherTask; LibraryScopeLocalJobIT provider-deferred | B / Komponenten PASS, Gesamtgate ausstehend |
| TC-F4-09 | LibrarySnapshotTest.newAndPoolThreadsDoNotInheritOrGuessTheActiveTask; LibraryScopeLocalJobIT foreign logs | B / Komponenten PASS, Gesamtgate ausstehend |
| TC-F4-10 | LibrarySnapshotTest.julHasTheSameInvocationExactlyOnce; relayedJulRecordCannotBorrowConsumersTask; sameThreadRelayedJulRecordCannotBorrowLaterScope; LibraryScopeLocalJobIT | B / Komponenten PASS, Gesamtgate ausstehend |
| TC-F4-11 | LibrarySnapshotTest.libraryFailuresKeepTheirGraphAndMaskSecretsInTextAndJson; LibraryScopeLocalJobIT provider-secret | B / Komponenten PASS, Gesamtgate ausstehend |
| TC-F4-12 | LibrarySnapshotTest.invalidOrForeignEventSnapshotsFallBackToRunOnly; LibraryScopeLocalJobIT provider-invalid | B / Komponenten PASS, Gesamtgate ausstehend |
| TC-F4-13 | LoggingScopeTest.noOpAndBrokenHooksKeepBusinessOutcomeAndCleanup; LibrarySnapshotTest.wrongThreadCloseDegradesWithoutClearingThatThreadsMdcAndOwnerCanStillRestore | B / Komponenten PASS, Gesamtgate ausstehend |
| TC-F4-14 | LibraryScopeLocalJobIT.realRuntimeAdapterKeepsSlf4jAndJulScopesInBothEntryPaths | B / Komponenten PASS, Gesamtgate ausstehend |
| TC-G-01 | HardeningLocalJobIT permissions | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-G-02 | HardeningLocalJobIT timeout + causes | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-G-03 | HardeningLocalJobIT setup + start boundary | A / PASS b7ada759 (erneut in B zu prüfen) |
| TC-G-04 | LibraryScopeLocalJobIT: regulärer SPI-Provider, SCRIPT und isoliertes JAR, ServiceLoader/Provideranzahl | B / Komponenten PASS, Gesamtgate ausstehend |
| TC-G-05 | Root standard/full verify + source-bound audits | B / Gesamtqualifikation ausstehend |
| TC-G-06 | Maven production-module-boundary; SelectedPackageLocalJobIT; ProviderInventory tests | B / Gesamtqualifikation ausstehend |

## Code- und Evidenzbindung

F1: `WorkspaceManager`, `PrivateDiagnosticFiles`, unveränderte Rootvorbereitung.
F2: `RunControl`, `BaseContext`, `FailureArbiter`, Completions/Outcomes, `RunReportWriter`.
F3: `SequentialJobRunner`, `RunStartException`, `JobRuntime`.
F4: `RunEventSink`/`RunEnvironment`, Runner und `OperationInvoker`, `MdcExecutionScope`,
`LibraryIdentity`, `LoggingPipeline`, `LogbackBridge` und der delegierende JobRuntime-Sink.

Externe Evidenzbasis: `/tmp/gretl-hardening-v11-evidence`. Gate-A-Standardarchiv:
`phase-a-b7ada75-standard.tar.gz`, SHA-256
`5598d388a7262e2a7e4cd34d72ae611367ce85001c8cfd57c6cd095382950364`.
Red/Green-Proben: `baseline-v11-red.log`, `task-budget-start-{red,green}.log`,
`f4-red.log` (fehlende Task-ID), `f4-jul-relay-red.log` (fremde JUL-Zuordnung),
`f4-components-all.log` und `f4-process-development-2.log`.
Die endgültigen Kandidaten-/Archivbindungen werden im Qualifikations- und Gate-B-Bericht
dokumentiert. Eine Komponentenprüfung ersetzt diese Freigabe nicht.
