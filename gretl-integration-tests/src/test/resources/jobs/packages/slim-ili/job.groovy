parameter('output', type: 'path', required: true, description: 'Result directory')
task('validate', IliValidator) {
    dataFile = file('original/IliValidator/Beispiel2a.xtf')
    modelDirectory = file('original/IliValidator')
}
action('copy', dependsOn: 'validate') {
    java.nio.file.Files.copy(result('validate').reportFile, parameter('output').resolve('validation.txt'))
}
defaults 'copy'
