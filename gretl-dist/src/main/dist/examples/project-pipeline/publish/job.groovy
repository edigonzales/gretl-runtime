action('run', dependsOn: ':checks:local:run') {
    def input = result(':checks:local:run').files.first()
    def output = parameter(':output')
    java.nio.file.Files.createDirectories(output.parent)
    java.nio.file.Files.copy(input, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
}
