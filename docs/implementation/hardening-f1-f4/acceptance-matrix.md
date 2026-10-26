# Abnahmematrix – Spezifikation 1.1

Ausgangspunkt: `bc044302829ede67ead27038bfdf2be90d0b0063`. Spezifikationshash: `2b5df1aaaaf614184f26b657be1e4b3c567f9ee5c393df09c1faf9d169febeab`.

F1-Produktkorrektur ist ALREADY_FIXED; fehlende Plattformbelege werden ergänzt. Diese Zuordnung behauptet vor den Kandidatenläufen und unabhängigen Reviews kein PASS. Testfallkennungen sind Orakel und dürfen mehrere bestehende Tests verwenden. Historische Berichte bleiben unverändert.

| Prüffall | Konkreter Nachweis | Gate/Status |
| --- | --- | --- |
| TC-F1-01 | HardeningLocalJobIT.preservesExistingRootsAndProtectsOwnedRunAndDiagnostics | A / Review ausstehend |
| TC-F1-02 | HardeningLocalJobIT; RunReportWriterTest | A / Review ausstehend |
| TC-F1-03 | WorkspacePlatformTest.deniedPosixRootIsNotRepaired; Linux PosixQualification | A / Review ausstehend |
| TC-F1-04 | WorkspaceOwnershipTest.createsPrivateOwnedDirectoriesAndLeavesExistingWorkRootAlone | A / Review ausstehend |
| TC-F1-05 | StartBoundaryTest; WorkspaceOwnershipTest | A / Review ausstehend |
| TC-F1-06 | WorkspacePlatformTest; WorkspaceManager atomic attributes | A / Review ausstehend |
| TC-F1-07 | WorkspacePlatformTest.configuredSymlinkRootPreservesTargetMetadataAndForeignSibling | A / Review ausstehend |
| TC-F1-08 | DiagnosticFilePermissionsTest; DiagnosticPosixQualification | A / Review ausstehend |
| TC-F1-09 | WorkspacePlatformTest.nonPosixZipFileSystemUsesPortableCreationAndStillRejectsCollisions | A / Review ausstehend |
| TC-F1-10 | WorkspaceRetentionTest; WorkPathOwnershipTest | A / Review ausstehend |
| TC-F2-01 | LocalBudgetRegressionTest.actionReturnAfterDeadlineCannotPublishAValue | A / Review ausstehend |
| TC-F2-02 | LocalBudgetRegressionTest.caughtLocalCancellationStillBlocksNewCallsAndReturn | A / Review ausstehend |
| TC-F2-03 | LocalBudgetRegressionTest.expiredPredicateCancelsBeforeBodyEvenWhenItReturnsFalse | A / Review ausstehend |
| TC-F2-04 | LocalBudgetRegressionTest.expiredConfigurationDoesNotConstructAHandler | A / Review ausstehend |
| TC-F2-05 | ScopedControlTest.successiveInvocationsShareTheOriginalDeadlineAndFreezeEarlierSuccess | A / Review ausstehend |
| TC-F2-06 | RunControlTest.childBudgetIsBoundedByParentAndLocalLimit; ScopedControlTest.purePollingDoesNotChangePrecedenceOrInterrupt | A / Review ausstehend |
| TC-F2-07 | LocalBudgetRegressionTest; HardeningLocalJobIT | A / Review ausstehend |
| TC-F2-08 | BudgetPrecedenceTest; ScopedControlTest.purePollingDoesNotChangePrecedenceOrInterrupt | A / Review ausstehend |
| TC-F2-09 | BudgetPrecedenceTest.explicitReleaseCancellationKeepsNestedCausesAndLaterCloseErrors | A / Review ausstehend |
| TC-F2-10 | LocalBudgetRegressionTest.timeoutDuringInvocationCloseIsStickyAndAllResourcesClose; BudgetPrecedenceTest | A / Review ausstehend |
| TC-F2-11 | ScopedControlTest.twoCleanupsShareOneGraceBudgetAfterWorkingTimeout | A / Review ausstehend |
| TC-F2-12 | BudgetPrecedenceTest.interruptedInvocationStillRunsCleanupAndRestoresInterruptOnReturn | A / Review ausstehend |
| TC-F2-13 | BudgetPrecedenceTest.deliberatelyCaughtInvocationFailureAllowsSuccessfulParent | A / Review ausstehend |
| TC-F2-14 | RunControlTest; SequentialJobRunnerTest | A / Review ausstehend |
| TC-F2-15 | ScopedControlTest.closedTaskAndInvocationHandlesKeepTheirOwnSnapshot | A / Review ausstehend |
| TC-F2-16 | ScopedControlTest.closedTaskAndInvocationHandlesKeepTheirOwnSnapshot; cancelledHandleRetainsItsCauseAndBudgetAfterRunEnd | A / Review ausstehend |
| TC-F2-17 | ScopedControlTest.purePollingDoesNotChangePrecedenceOrInterrupt | A / Review ausstehend |
| TC-F2-18 | CancelCauseReportTest; HardeningLocalJobIT.concreteControlCauseSurvivesTheProductEntry | A / Review ausstehend |
| TC-F2-19 | ScopedControlTest.successiveInvocationsShareTheOriginalDeadlineAndFreezeEarlierSuccess; SequentialJobRunnerTest.cancellationBeforeTheFirstTaskMarksEverythingNotRun | A / Review ausstehend |
| TC-F2-20 | BudgetPrecedenceTest; CancelCauseReportTest | A / Review ausstehend |
| TC-F3-01 | SetupFailureTest.taskWorkspaceFailureIsTerminalAndPreservesSetupCauseBeforeCleanupErrors; HardeningLocalJobIT | A / Review ausstehend |
| TC-F3-02 | SetupFailureTest.lostWorkspaceAfterRunStartStillAllowsExternalCleanup | A / Review ausstehend |
| TC-F3-03 | StartBoundaryTest; SetupCompletionTest.exclusiveLeafFailureIsCodeTwoAndNeverCallsCleanupOrPublishesRunEvents; HardeningLocalJobIT.failedStartHasNoRunReportOrCleanupEffects | A / Review ausstehend |
| TC-F3-04 | SetupFailureTest; OperationInvocationRunnerTest.cleanupRunsInReverseOrderAndFailureAfterSuccessFailsTheRun | A / Review ausstehend |
| TC-F3-05 | SetupFailureTest.lostWorkspaceAfterRunStartStillAllowsExternalCleanup; cleanupWithoutFilesDoesNotOpenItsWorkspaceAndFileCleanupFailureIsSecondary | A / Review ausstehend |
| TC-F3-06 | SetupFailureTest.lostWorkspaceAfterRunStartStillAllowsExternalCleanup; WorkPathOwnershipTest | A / Review ausstehend |
| TC-F3-07 | HardeningLocalJobIT.setupFailureStillCleansUpAndReportsTheOriginalCause | A / Review ausstehend |
| TC-F3-08 | SetupCompletionTest.brokenExplicitReportParentDoesNotPreventTaskAndCleanupCompletion; HardeningLocalJobIT | A / Review ausstehend |
| TC-F3-09 | SetupFailureTest.invocationSetupFailureHasOrdinaryCatchSemanticsAndItsOwnHistory | A / Review ausstehend |
| TC-F3-10 | HardeningLocalJobIT.assertTerminal; SetupFailureTest | A / Review ausstehend |
| TC-F3-11 | ContextAccessTest; ScopedControlTest; SetupCompletionTest | A / Review ausstehend |
| TC-F3-12 | HardeningLocalJobIT.setupFailureStillCleansUpAndReportsTheOriginalCause | A / Review ausstehend |
| TC-F4-01 | Phase B: LibraryScopeTest / LibraryLoggingLocalJobIT (noch nicht implementiert) | B / NOT_RUN |
| TC-F4-02 | Phase B: LibraryScopeTest / LibraryLoggingLocalJobIT (noch nicht implementiert) | B / NOT_RUN |
| TC-F4-03 | Phase B: LibraryScopeTest / LibraryLoggingLocalJobIT (noch nicht implementiert) | B / NOT_RUN |
| TC-F4-04 | Phase B: LibraryScopeTest / LibraryLoggingLocalJobIT (noch nicht implementiert) | B / NOT_RUN |
| TC-F4-05 | Phase B: LibraryScopeTest / LibraryLoggingLocalJobIT (noch nicht implementiert) | B / NOT_RUN |
| TC-F4-06 | Phase B: LibraryScopeTest / LibraryLoggingLocalJobIT (noch nicht implementiert) | B / NOT_RUN |
| TC-F4-07 | Phase B: LibraryScopeTest / LibraryLoggingLocalJobIT (noch nicht implementiert) | B / NOT_RUN |
| TC-F4-08 | Phase B: LibraryScopeTest / LibraryLoggingLocalJobIT (noch nicht implementiert) | B / NOT_RUN |
| TC-F4-09 | Phase B: LibraryScopeTest / LibraryLoggingLocalJobIT (noch nicht implementiert) | B / NOT_RUN |
| TC-F4-10 | Phase B: LibraryScopeTest / LibraryLoggingLocalJobIT (noch nicht implementiert) | B / NOT_RUN |
| TC-F4-11 | Phase B: LibraryScopeTest / LibraryLoggingLocalJobIT (noch nicht implementiert) | B / NOT_RUN |
| TC-F4-12 | Phase B: LibraryScopeTest / LibraryLoggingLocalJobIT (noch nicht implementiert) | B / NOT_RUN |
| TC-F4-13 | Phase B: LibraryScopeTest / LibraryLoggingLocalJobIT (noch nicht implementiert) | B / NOT_RUN |
| TC-F4-14 | Phase B: LibraryScopeTest / LibraryLoggingLocalJobIT (noch nicht implementiert) | B / NOT_RUN |
| TC-G-01 | HardeningLocalJobIT permissions | A / Review ausstehend |
| TC-G-02 | HardeningLocalJobIT timeout + causes | A / Review ausstehend |
| TC-G-03 | HardeningLocalJobIT setup + start boundary | A / Review ausstehend |
| TC-G-04 | Phase B: actual extension JAR and SCRIPT/JAR library logging | B / NOT_RUN |
| TC-G-05 | Root standard/full verify + source-bound audits | B / NOT_RUN |
| TC-G-06 | Maven production-module-boundary; SelectedPackageLocalJobIT; ProviderInventory tests | B / NOT_RUN |
