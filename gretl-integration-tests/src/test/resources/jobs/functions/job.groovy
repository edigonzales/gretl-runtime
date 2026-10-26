parameter('output', type: 'path', required: true, description: 'Writable output')
parameter('url', required: true, description: 'Locally controlled HTTP resource')
action('input') {
    java.nio.file.Files.writeString(parameter('output').resolve('resource.csv'), parameter('url') + '\n')
    java.nio.file.Files.writeString(parameter('output').resolve('resource.xtf'),
        '<TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3"><HEADERSECTION SENDER="fixture" VERSION="2.3"><MODELS><MODEL NAME="FunctionProbe" VERSION="2026-10-25" URI="https://example.invalid/probe"/></MODELS></HEADERSECTION><DATASECTION><FunctionProbe.Data BID="b1"><FunctionProbe.Data.Resource TID="o1"><url>' + parameter('url') + '</url></FunctionProbe.Data.Resource></FunctionProbe.Data></DATASECTION></TRANSFER>')
}
task('validate', CsvValidator, dependsOn: 'input') {
    dataFiles = [parameter('output').resolve('resource.csv')]
    modelNames = ['FunctionProbe']; modelDirectories = [file('.')]
    firstLineIsHeader = false; logFile = parameter('output').resolve('validation.log')
}
defaults 'validate'

task('ili-function', IliValidator, dependsOn: 'input') {
    dataFile = parameter('output').resolve('resource.xtf'); modelDirectory = file('.')
}
