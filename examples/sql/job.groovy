task('tabellenAktualisieren', SqlExecutor) {
    database = 'edit'
    scripts = [file('sql/update.sql')]
}

defaults 'tabellenAktualisieren'
