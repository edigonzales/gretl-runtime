parameter('input', type: 'path', required: true, description: 'Directory containing base.csv and incoming.csv')
parameter('output', type: 'path', required: true, description: 'Directory for the snapshot and validation log')
parameter('model', defaultValue: 'SnapshotMinimum', description: 'INTERLIS model for the snapshot')

def sqlPath = { path ->
    "'" + path.toString().replace("'", "''") + "'"
}

task('append', DuckDbSqlExecutor) {
    inMemory = true
    sqlFiles = [file('append.sql')]
    sqlParameters = [
        base: sqlPath(parameter('input').resolve('base.csv')),
        incoming: sqlPath(parameter('input').resolve('incoming.csv')),
        snapshot: sqlPath(parameter('output').resolve('snapshot.csv'))
    ]
}

task('validate', CsvValidator, dependsOn: 'append') {
    dataFiles = [parameter('output').resolve('snapshot.csv')]
    modelNames = [parameter('model')]
    modelDirectories = [file('.')]
    firstLineIsHeader = true
    valueSeparator = ';'
    valueDelimiter = '"'
    encoding = 'UTF-8'
    logFile = parameter('output').resolve('validation.log')
}
defaults 'validate'
