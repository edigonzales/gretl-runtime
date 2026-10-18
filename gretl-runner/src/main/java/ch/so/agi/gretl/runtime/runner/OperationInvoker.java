package ch.so.agi.gretl.runtime.runner;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.CancelReason;
import ch.so.agi.gretl.runtime.api.ExecutionIdentity;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.TaskCancelledException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Creates and owns every synchronous operation invocation: invocation identity,
 * fresh builder/handler, request/result type checks, resource close and
 * terminal outcome.
 */
public final class OperationInvoker {
    private final RunEnvironment environment;

    OperationInvoker(RunEnvironment environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    private String msg(String key, Object... arguments) {
        return Messages.format(environment.locale(), key, arguments);
    }

    <C, R> R invoke(OperationType<C, R> type, RequestPreparation<C> preparation,
                    TaskFrame parent) throws Exception {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(preparation, "preparation");
        Objects.requireNonNull(parent, "parent");
        environment.control().checkBeforeWork();

        String invocationId = environment.nextInvocationId();
        Path invocationDirectory = environment.workspace()
                .invocationDirectory(parent.taskIndex(), invocationId);
        CloseStack closeStack = new CloseStack();
        ExecutionIdentity identity = environment.identity(parent.definition().id(), invocationId);
        OperationContextImpl context = new OperationContextImpl(
                environment, identity, invocationDirectory, closeStack,
                parent.budget(), type.id());
        Instant startedAt = environment.now();
        long startTicks = environment.monotonicClock().ticks();
        InvocationFrame frame = new InvocationFrame(invocationId, parent, type,
                invocationDirectory, closeStack, context, startedAt, startTicks);

        environment.emit("gretl.operation.started", LogLevel.INFO, identity,
                Optional.of(type.id()), "configuration", null, 0,
                Map.of("operation.symbol", type.symbol()), Optional.empty());

        R result = null;
        Throwable primary = null;
        boolean cancelled = false;
        try {
            C request = preparation.prepare(frame);
            if (request == null) {
                throw new ProviderContractException("NULL_OPERATION_REQUEST",
                        msg("runner.nullOperationRequest", type.id()));
            }
            if (!type.requestType().isInstance(request)) {
                throw new ProviderContractException("REQUEST_TYPE_MISMATCH",
                        msg("runner.requestTypeMismatch", type.id(),
                                type.requestType().getName(), request.getClass().getName()));
            }
            OperationHandler<C, R> handler;
            try {
                handler = type.handlerFactory().get();
            } catch (RuntimeException exception) {
                throw new ProviderContractException("HANDLER_FACTORY_FAILED",
                        msg("runner.handlerFactoryFailed", type.id()), exception);
            }
            if (handler == null) {
                throw new ProviderContractException("NULL_OPERATION_HANDLER",
                        msg("runner.nullOperationHandler", type.id()));
            }
            frame.phase("execution");
            result = handler.execute(request, context);
            if (result == null && type.resultType() != Void.class) {
                throw new ProviderContractException("NULL_OPERATION_RESULT",
                        msg("runner.nullOperationResult", type.id(),
                                type.resultType().getName()));
            }
            if (result != null && !type.resultType().isInstance(result)) {
                throw new ProviderContractException("OPERATION_RESULT_TYPE_MISMATCH",
                        msg("runner.operationResultMismatch", type.id(),
                                result.getClass().getName(), type.resultType().getName()));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            environment.control().requestCancel(CancelReason.INTERRUPTED);
            TaskCancelledException cancellation = new TaskCancelledException(CancelReason.INTERRUPTED);
            cancellation.addSuppressed(interrupted);
            primary = cancellation;
            cancelled = true;
        } catch (TaskCancelledException cancellation) {
            environment.control().requestCancel(cancellation.reason());
            primary = cancellation;
            cancelled = true;
        } catch (Throwable failure) {
            primary = failure;
        }

        if (primary == null) {
            frame.phase("cleanup");
        }
        List<Throwable> closeErrors = frame.closeResources();

        InvocationStatus status;
        FailureInfo primaryInfo = null;
        if (primary != null) {
            status = cancelled ? InvocationStatus.CANCELLED : InvocationStatus.FAILED;
            if (!cancelled) {
                primaryInfo = FailureInfo.from(primary);
            }
            for (Throwable closeError : closeErrors) {
                if (closeError != primary) {
                    primary.addSuppressed(closeError);
                }
            }
        } else if (!closeErrors.isEmpty()) {
            status = InvocationStatus.FAILED;
            primary = closeErrors.get(0);
            primaryInfo = FailureInfo.from(primary);
            for (int index = 1; index < closeErrors.size(); index++) {
                if (closeErrors.get(index) != primary) {
                    primary.addSuppressed(closeErrors.get(index));
                }
            }
        } else {
            status = InvocationStatus.SUCCEEDED;
        }
        Throwable resolvedPrimary = primary;
        List<FailureInfo> secondary = closeErrors.stream()
                .filter(error -> error != resolvedPrimary)
                .map(FailureInfo::from)
                .toList();

        Instant finishedAt = environment.now();
        long endTicks = environment.monotonicClock().ticks();
        InvocationOutcome outcome = frame.outcome(status, primaryInfo, secondary, finishedAt, endTicks);
        parent.addInvocation(outcome);
        environment.emit(
                status == InvocationStatus.SUCCEEDED
                        ? "gretl.operation.finished" : "gretl.operation.failed",
                status == InvocationStatus.SUCCEEDED ? LogLevel.INFO : LogLevel.DEBUG,
                identity, Optional.of(type.id()), frame.phase(), status.name(),
                outcome.durationMillis(), Map.of("operation.symbol", type.symbol()),
                Optional.ofNullable(primaryInfo));

        if (primary != null) {
            throwUnchecked(primary);
        }
        return result;
    }

    private static void throwUnchecked(Throwable failure) throws Exception {
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(failure);
    }
}
