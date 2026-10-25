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
