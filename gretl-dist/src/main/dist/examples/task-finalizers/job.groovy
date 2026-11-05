import java.nio.file.Files
import java.nio.file.StandardOpenOption

parameter('output', type: 'path', required: true, description: 'Synthetic result directory')
parameter('mode', defaultValue: 'success', description: 'success, fail or wait for cooperative cancellation')

action('importData', finalizedBy: ['release', 'record']) { ctx ->
    def out = parameter('output')
    Files.createDirectories(out)
    ctx.resources().manage({ Files.writeString(out.resolve('owner-closed.txt'), 'closed') } as AutoCloseable)
    Files.writeString(out.resolve('trace.txt'), 'importData\n', StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    Files.writeString(out.resolve('started'), 'owner reached RUNNING')
    if (parameter('mode') == 'fail') throw new IllegalStateException('synthetic-owner-failure')
    if (parameter('mode') == 'wait') {
        while (true) {
            ctx.cancellation().throwIfCancellationRequested()
            java.util.concurrent.locks.LockSupport.parkNanos(1000000L)
        }
    }
}

action('secondOwner', finalizedBy: 'release') {
    Files.writeString(parameter('output').resolve('trace.txt'), 'secondOwner\n', StandardOpenOption.APPEND)
}
apply from: 'tasks/completion.groovy'
cleanup('jobEnd') {
    Files.writeString(parameter('output').resolve('trace.txt'), 'cleanup\n', StandardOpenOption.APPEND)
    Files.writeString(parameter('output').resolve('cleanup.txt'), 'global')
}
defaults 'importData', 'secondOwner'
