parameter('output', type: 'path', required: true, description: 'Writable output')
parameter('batch', type: 'integer', defaultValue: 2000, description: 'Batch size')
parameter('variant', defaultValue: 'object', description: 'JSON object or array, single or multi-layer')
task('csv-import', CsvImport) {
    database = 'db'; dataFile = file('CsvImport/data1.csv'); tableName = 'csv_import'; firstLineIsHeader = true; batchSize = parameter('batch')
}
task('csv-export', CsvExport) {
    database = 'db'; dataFile = parameter('output').resolve('data.csv'); tableName = 'csv_export'; firstLineIsHeader = true
    attributes = ['t_id', 'Aint', 'adec', 'atext', 'aenum', 'adate', 'atimestamp', 'aboolean']
}
task('shp-import', ShpImport) {
    database = 'db'; dataFile = file('ShpImportBatchSize/data.shp'); tableName = 'shp_import'; batchSize = parameter('batch')
}
task('shp-export', ShpExport) {
    database = 'db'; dataFile = parameter('output').resolve('data.shp'); tableName = 'shp_export'
}
task('gpkg-import', GpkgImport) {
    database = 'db'; dataFile = file('GpkgImport/point.gpkg'); srcTableName = 'Point'; dstTableName = 'gpkg_import'; batchSize = 200; fetchSize = 200
}
task('gpkg-export', GpkgExport) {
    database = 'db'; dataFile = parameter('output').resolve('data.gpkg')
    srcTableName = parameter('variant') == 'multi' ? ['exportdata1', 'exportdata2'] : ['exportdata1']
    dstTableName = parameter('variant') == 'multi' ? ['exportdata1', 'exportdata2'] : ['exportdata1']
}
task('json-import', JsonImport) {
    database = 'db'; jsonFile = file(parameter('variant') == 'array' ? 'JsonImportArray/data.json' : 'JsonImportObject/data.json')
    qualifiedTableName = 'documents'; columnName = 'document'
}
defaults 'csv-import'
