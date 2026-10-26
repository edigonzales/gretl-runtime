parameter('schema', required: true, description: 'Isolated fixture schema')
task('seed', SqlExecutor) {
    database = 'db'
    scripts = [file('seed.sql')]
}
task('copy', Db2Db, dependsOn: 'seed') {
    sourceDatabase = 'db'
    targetDatabase = 'db'
    transfers = [[sqlFile: file('select.sql'), targetTable: 'colors_copy', deleteAllRows: true]]
}
task('update', SqlExecutor, dependsOn: 'copy') {
    database = 'db'
    statements = [[sql: 'update colors_copy set name=? where id=?', parameters: ['Grün', 2]]]
}
task('cancel-copy', Db2Db) {
    sourceDatabase = 'db'
    targetDatabase = 'db'
    fetchSize = 1
    batchSize = 1
    transfers = [[sqlFile: file('slow.sql'), targetTable: 'colors_copy', deleteAllRows: true]]
}
action('never', dependsOn: 'cancel-copy') {
    throw new AssertionError('successor-must-not-run')
}
task('geometry', DuckDbSqlExecutor) {
    inMemory = true
    installExtensions = true
    sqlFiles = [file('noop.sql')]
    sources = [[type: 'postgres', alias: 'src', database: 'db',
        tables: [[table: parameter('schema') + '.geo_source', alias: 'geometry']]]]
}
defaults 'update'
parameter('variant', defaultValue: 'list', description: 'Scalar or repeated SQL parameters')
task('parameter-sql', SqlExecutor) {
    database = 'db'
    statements = (parameter('variant') == 'scalar' ? ['ele1'] : ['ele1', 'ele2']).collect { value ->
        [sql: 'insert into parameter_values(title) values (?)', parameters: [value]]
    }
}
task('parameter-copy', Db2Db) {
    sourceDatabase = 'db'; targetDatabase = 'db'; fetchSize = 1; batchSize = 1
    transfers = [[sqlFile: file('parameter-select.sql'), targetTable: 'parameter_copy', deleteAllRows: false]]
    if (parameter('variant') == 'scalar') sqlParameters = [table: 'source1']
    else sqlParameterSets = [[table: 'source1'], [table: 'source2']]
}
task('federation', DuckDbSqlExecutor) {
    inMemory = true; installExtensions = true; sqlFiles = [file('federation.sql')]
    sources = [[type: 'postgres', alias: 'src', database: 'db', tables: [[table: parameter('schema') + '.source1', alias: 'colors']]]]
    targets = [[type: 'postgres', alias: 'tgt', database: 'db']]
    exports = [[type: 'postgres', name: 'out', target: 'tgt', table: parameter('schema') + '.federated',
        query: 'select * from result.records', mode: 'replace', create: true]]
}
task('typed-copy', Db2Db) {
    sourceDatabase = 'db'; targetDatabase = 'db'; fetchSize = 1; batchSize = 1
    transfers = [[sqlFile: file('sql/typed-select.sql'), targetTable: 'target_data', deleteAllRows: true]]
}
task('typed-back', Db2Db, dependsOn: 'typed-copy') {
    sourceDatabase = 'db'; targetDatabase = 'db'
    transfers = [[sqlFile: file('sql/typed-back.sql'), targetTable: 'source_data', deleteAllRows: true]]
}
