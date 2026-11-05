task('prepare', Gzip) {
    // Ressourcen bleiben relativ zum Jobroot, auch in einem verschachtelten Fragment.
    dataFile = file('data/input.txt')
    gzipFile = workPath('input.txt.gz')
    overwrite = true
}
