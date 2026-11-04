import java.nio.file.Files
import java.util.Arrays
import java.util.zip.GZIPInputStream

action('verify', dependsOn: 'prepare') { ctx ->
    byte[] expected = Files.readAllBytes(ctx.file('data/input.txt'))
    byte[] actual
    new GZIPInputStream(Files.newInputStream(result('prepare').file)).withCloseable { stream ->
        actual = stream.readAllBytes()
    }
    if (!Arrays.equals(expected, actual)) {
        throw new IllegalStateException('Der dekomprimierte Inhalt stimmt nicht mit der Eingabe überein')
    }
    def destination = parameter('output')
    Files.createDirectories(destination.toAbsolutePath().parent)
    Files.write(destination, actual)
}

cleanup('release') {
    println 'Script-Includes: gemeinsamer Abschluss'
}
