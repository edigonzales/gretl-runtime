task('validate', IliValidator) {
    dataFile = file('data/valid.xtf')
    modelDirectory = file('models')
}

defaults 'validate'
