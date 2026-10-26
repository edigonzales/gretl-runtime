import java.nio.file.Files
import java.util.concurrent.locks.LockSupport
parameter('signals', type: 'path', required: true, description: 'Synchronization directory')
parameter('fail', type: 'boolean', defaultValue: false, description: 'Fail before shutdown')
action('cooperate') { ctx ->
    if (parameter('fail')) throw new IllegalStateException('original-business-failure')
    Files.writeString(parameter('signals').resolve('ready'), 'ready')
    while (true) {
        if (Files.exists(parameter('signals').resolve('cancel'))) Thread.currentThread().interrupt()
        ctx.cancellation().throwIfCancellationRequested()
        LockSupport.parkNanos(1000000L)
    }
}
action('never', dependsOn: 'cooperate') {
    Files.writeString(parameter('signals').resolve('unexpected'), 'must not run')
}
cleanup('cooperative-cleanup') {
    if (parameter('fail')) {
        Files.writeString(parameter('signals').resolve('ready'), 'ready')
        while (!Files.exists(parameter('signals').resolve('release'))) LockSupport.parkNanos(1000000L)
    }
    Files.writeString(parameter('signals').resolve('cleaned'), 'cleaned')
    log().info('cleanup-complete')
}
defaults 'never'
