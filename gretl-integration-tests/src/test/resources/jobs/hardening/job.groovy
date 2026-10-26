import ch.so.agi.gretl.runtime.api.TaskCancelledException
import java.nio.file.Files

parameter('output', type: 'path', required: true, description: 'Independent assertion outputs')

action('permissions') {
    Files.writeString(workPath('owned.txt'), 'owned')
}

action('caughtTimeout', timeout: 'PT0.1S') { ctx ->
    def output = parameter('output')
    ctx.resources().manage({ Files.writeString(output.resolve('closed'), 'closed') } as AutoCloseable)
    while (!ctx.budget().expired()) Thread.onSpinWait()
    try {
        ctx.cancellation().throwIfCancellationRequested()
    } catch (TaskCancelledException expected) {
        Files.writeString(output.resolve('caught'), expected.reason().name())
    }
    Files.writeString(output.resolve('returned'), 'body-returned')
}

action('closeTimeout', timeout: 'PT0.1S') { ctx ->
    def output = parameter('output')
    def budget = ctx.budget()
    ctx.resources().manage({
        while (!budget.expired()) Thread.onSpinWait()
        Files.writeString(output.resolve('closed'), 'closed-after-budget')
    } as AutoCloseable)
}

action('blockWorkspace') {
    def runRoot = workPath('anchor').parent.parent
    Files.writeString(runRoot.resolve('task-000004'), 'occupied-task-workspace')
    Files.writeString(runRoot.resolve('cleanup-000'), 'occupied-cleanup-workspace')
}
action('blockedTask', dependsOn: 'blockWorkspace') {
    Files.writeString(parameter('output').resolve('unexpected'), 'must-not-run')
}

action('originalFailure') {
    def runRoot = workPath('anchor').parent.parent
    Files.writeString(runRoot.resolve('cleanup-000'), 'occupied-cleanup-workspace')
    throw new IllegalStateException('original-hardening-failure')
}

cleanup('externalCleanup') {
    Files.writeString(parameter('output').resolve('cleaned'), 'cleaned-without-workspace')
    log().info('hardening-cleanup-complete')
}
defaults 'permissions'
