import java.nio.file.Files
import java.security.MessageDigest
import java.util.HexFormat

parameter('output', type: 'path', required: true, description: 'Independent assertion outputs')

task('database', SqlExecutor) {
    database = 'edit'
    statements = [[sql: 'CREATE TABLE plaintext_probe (id INTEGER)', parameters: []]]
}

action('probe', dependsOn: 'database') {
    def value = secret('dbPassword').reveal()
    def digest = MessageDigest.getInstance('SHA-256').digest(value.getBytes('UTF-8'))
    Files.writeString(parameter('output').resolve('digest.txt'), HexFormat.of().formatHex(digest))
    log().info('plaintext-probe ' + value)
    throw new IllegalStateException('plaintext-failure ' + value,
            new IllegalArgumentException('plaintext-cause ' + value))
}

defaults 'probe'
