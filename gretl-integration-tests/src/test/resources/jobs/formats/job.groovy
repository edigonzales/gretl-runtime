// tag::reference-parameters[]
parameter('output', type: 'path', required: true, description: 'Writable output directory')
parameter('variant', defaultValue: 'single', description: 'Original scenario variant')
// end::reference-parameters[]
task('excel', Csv2Excel) {
    csvFile = file(parameter('variant') == 'empty' ? 'Csv2ExcelEmptyFile/superflous_publication_formats.csv' : 'Csv2Excel/20230124_sap_Gebaeude.csv')
    firstLineIsHeader = true; valueSeparator = ';'
    encoding = parameter('variant') == 'empty' ? 'UTF-8' : 'ISO-8859-1'
    if (parameter('variant') != 'empty') {
        models = 'SO_HBA_Gebaeude_20230111'; modeldir = file('Csv2Excel')
    }
    outputFile = parameter('output').resolve('converted.xlsx')
}
// tag::reference-av2ch[]
task('av2ch', Av2ch) {
    inputFiles = parameter('variant') == 'fail' ? [file('Av2chFail/fubar.itf')] : parameter('variant') == 'set' ?
        [file('Av2chFileSet/252400.itf'), file('Av2chFileSet/254900.itf')] : [file('Av2ch/254900.itf')]
    outputDirectory = parameter('output'); language = 'de'
}
// end::reference-av2ch[]
// tag::reference-av2geobau[]
task('av2geobau', Av2geobau) {
    itfFiles = parameter('variant') == 'set' ? [file('Av2geobauFileSet/empty1.itf'), file('Av2geobauFileSet/empty2.itf')] : [file('Av2geobau/empty.itf')]
    dxfDirectory = parameter('output')
}
// end::reference-av2geobau[]
task('gpkg2shp', Gpkg2Shp) {
    dataFile = file('admin.gpkg'); outputDir = parameter('output')
}
// tag::reference-gpkg2dxf[]
task('gpkg2dxf', Gpkg2Dxf) {
    dataFile = file('admin.gpkg'); outputDir = parameter('output')
}
// end::reference-gpkg2dxf[]
defaults 'excel'
