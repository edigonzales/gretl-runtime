parameter('output', type: 'path', required: true, description: 'Writable output')
parameter('variant', defaultValue: 'single', description: 'Original input variant')
task('ili', IliValidator) {
    dataFile = file(parameter('variant') == 'fail' ? 'IliValidatorFail/Beispiel2a.xtf' : 'IliValidator/Beispiel2a.xtf')
    modelDirectory = file(parameter('variant') == 'fail' ? 'IliValidatorFail' : 'IliValidator')
}
action('ili-report', dependsOn: 'ili') {
    java.nio.file.Files.copy(result('ili').reportFile, parameter('output').resolve('validation.txt'))
}
task('ili-second', IliValidator) {
    dataFile = file('IliValidatorFileSet/Beispiel2b.xtf'); modelDirectory = file('IliValidatorFileSet')
}
task('gpkg', Ili2gpkgImport) {
    databaseFile = parameter('output').resolve('result.gpkg'); defaultSrsCode = '2056'
    if (parameter('variant') == 'set') {
        dataFiles = [file('Ili2gpkgImportFileSet/Beispiel2a.xtf'), file('Ili2gpkgImportFileSet/Beispiel2b.xtf')]
        datasetNames = ['DatasetA', 'DatasetB']
        modelDirectories = [file('Ili2gpkgImportFileSet')]
    } else {
        dataFiles = [file('Ili2gpkgImport/ch.so.agi.av_gb_admin_einteilung_edit_2020-08-20.xtf')]
        modelDirectories = [file('Ili2gpkgImport'), file('models')]
    }
}
task('duck-schema', Ili2duckdbImportSchema) {
    databaseFile = parameter('output').resolve('result.duckdb'); modelNames = ['GB2AV']; modelDirectories = [file('Ili2duckdbImportSchema')]
    createBasketCol = true; createTidCol = true
}
task('duck-import', Ili2duckdbImport, dependsOn: 'duck-schema') {
    databaseFile = parameter('output').resolve('result.duckdb'); modelNames = ['GB2AV']; modelDirectories = [file('Ili2duckdbImport')]
    dataFiles = [file('Ili2duckdbImport/VOLLZUG_SO0200002401_1531_20180105113131.xml')]; datasetNames = ['original']; importTid = true
}
task('duck-export', Ili2duckdbExport, dependsOn: 'duck-import') {
    databaseFile = parameter('output').resolve('result.duckdb'); dataFiles = [parameter('output').resolve('export.xtf')]; datasetNames = ['original']
}
defaults 'ili-report'

task('ngk', IliValidator) {
    dataFile = file('IliValidatorNgk/NGK_SO_Testbeddata.xtf'); modelDirectory = file('ngk-local'); validationConfig = file('ngk-local/validation.ini')
}
