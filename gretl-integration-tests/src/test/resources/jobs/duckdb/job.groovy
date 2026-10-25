parameter('output', type: 'path', required: true, description: 'Writable output')
task('sql', DuckDbSqlExecutor) {
    database = parameter('output').resolve('foo.duckdb'); installExtensions = true
    sqlFiles = [file('create_insert.sql'), file('import_parquet.sql')]
    sqlParameters = [pwd: "'" + file('.').toString().replace("'", "''") + "'"]
    exports = [[type: 'parquet', name: 'rows', query: 'select * from tbl order by i',
        file: parameter('output').resolve('tbl.parquet'), overwrite: false]]
}
defaults 'sql'
