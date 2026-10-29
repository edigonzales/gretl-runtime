package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.TaskContext;
import ch.so.agi.gretl.runtime.runner.CleanupBody;
import ch.so.agi.gretl.runtime.runner.OperationCaller;
import ch.so.agi.gretl.runtime.runner.TaskBody;
import ch.so.agi.gretl.runtime.runner.TaskCondition;
import groovy.lang.Closure;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import java.util.List;

/** Binds stored closures to fresh scopes only at task or invocation time. */
public final class GroovyTaskAdapter {
    private GroovyTaskAdapter() {
    }

    public static <C, R> TaskBody<R> typedBody(
            OperationType<C, R> type, Closure<?> configure, JobDslModel model) {
        return context -> {
            if (!(context instanceof OperationCaller caller)) {
                throw new GroovyDslException("NO_OPERATION_CALLER",
                        msg(model, "error.groovy.noCallerTyped"));
            }
            String taskId = context.identity().taskId().orElse(type.id());
            return caller.invoke(type, (invocationContext, taskContext) ->
                    new TaskConfigurationScope<>(type, configure, model, taskContext,
                            invocationContext).buildRequest());
        };
    }

    public static TaskBody<Object> actionBody(Closure<?> body, JobDslModel model) {
        return context -> {
            ActionScope scope = new ActionScope(context, model);
            String taskId = context.identity().taskId().orElse(null);
            return evaluateClosure(body, scope, scope.contextFacade(), model,
                    "action", taskId);
        };
    }

    public static TaskCondition condition(Closure<?> condition, JobDslModel model) {
        return context -> {
            ActionScope scope = new ActionScope(context, model);
            String taskId = context.identity().taskId().orElse(null);
            Object value = evaluateClosure(condition, scope, scope.contextFacade(), model,
                    "when", taskId);
            if (!(value instanceof Boolean booleanValue)) {
                throw new GroovyDslException("CONDITION_NOT_BOOLEAN",
                        msg(model, "error.groovy.conditionNotBoolean",
                                value == null ? "null" : value.getClass().getName()),
                        taskId, null, model.sourceForTask(taskId),
                        List.of(), null);
            }
            return booleanValue;
        };
    }

    public static CleanupBody cleanupBody(Closure<?> body, JobDslModel model) {
        return context -> {
            CleanupScope scope = new CleanupScope(context, model);
            evaluateClosure(body, scope, model.projectMode() ? scope : context, model, "cleanup", null);
        };
    }

    static Object evaluateClosure(
            Closure<?> closure, Object delegate, Object parameter,
            JobDslModel model, String role, String taskId) throws Exception {
        if (closure == null) {
            throw new GroovyDslException("MISSING_CLOSURE",
                    msg(model, "error.groovy.missingClosure", role), taskId, null,
                    model.currentSource(), List.of(), null);
        }
        Closure<?> copy = (Closure<?>) closure.rehydrate(
                delegate, closure.getOwner(), closure.getThisObject());
        copy.setResolveStrategy(Closure.DELEGATE_ONLY);
        try {
            int parameters = copy.getMaximumNumberOfParameters();
            if (parameters == 0) {
                return copy.call();
            }
            if (parameters == 1) {
                return copy.call(parameter);
            }
            throw new GroovyDslException("CLOSURE_ARITY",
                    msg(model, "error.groovy.closureArity", role, parameters),
                    taskId, null, model.currentSource(),
                    List.of(), null);
        } catch (GroovyDslException exception) {
            GroovyDslException mapped = withSource(exception, model);
            model.remap(mapped);
            throw mapped;
        } catch (MissingPropertyException exception) {
            throw mapped(new GroovyDslException("UNKNOWN_PROPERTY",
                    msg(model, "error.groovy.unknownProperty",
                            exception.getProperty(), role),
                    taskId, exception.getProperty(), model.failureSource(exception),
                    List.of(), exception), model);
        } catch (MissingMethodException exception) {
            throw mapped(new GroovyDslException("UNKNOWN_HELPER",
                    msg(model, "error.groovy.unknownHelper",
                            exception.getMethod(), role),
                    taskId, exception.getMethod(), model.failureSource(exception),
                    List.of(), exception), model);
        } catch (Exception | Error failure) {
            model.remap(failure);
            if (model.projectMode() && failure instanceof ch.so.agi.gretl.runtime.runner.ResultAccessException access) {
                throw new GroovyDslException(access.code(), access.getMessage(), taskId, null,
                        model.failureSource(access), List.of(), access);
            }
            throw failure;
        }
    }

    private static GroovyDslException mapped(GroovyDslException failure, JobDslModel model) {
        model.remap(failure);
        return failure;
    }

    private static String msg(JobDslModel model, String key, Object... arguments) {
        return Messages.format(model.environment().locale(), key, arguments);
    }

    private static GroovyDslException withSource(GroovyDslException exception, JobDslModel model) {
        if (exception.source() != null && exception.source().line() > 0) {
            return exception;
        }
        return new GroovyDslException(exception.code(), exception.getMessage(),
                exception.taskId(), exception.fieldName(),
                model.failureSource(exception),
                exception.available(), exception);
    }
}
