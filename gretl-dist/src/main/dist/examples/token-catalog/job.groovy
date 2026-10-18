import groovy.json.JsonSlurper
import java.nio.file.Files

parameter('tokenUrl', required: true,
          description: 'Adresse des lokalen Test-Tokenservices')
parameter('catalogUrl', required: true,
          description: 'Adresse des lokalen Test-Dateikatalogs')

action('token') { ctx ->
    def response = ctx.run(HttpDownload) {
        method = 'POST'
        url = parameter('tokenUrl')
        headers = [
            'Authorization': secret('sourceAuth'),
            'Content-Type': 'application/x-www-form-urlencoded'
        ]
        bodyText = 'grant_type=client_credentials'
        output = workPath('token.json')
        maxBytes = 65536L
    }
    def json = new JsonSlurper().parseText(Files.readString(response.file()))
    if (!(json.access_token instanceof String) || json.access_token.isBlank()) {
        throw new IllegalArgumentException('Tokenantwort ohne access_token')
    }
    return ctx.sensitive(json.access_token as String)
}

task('catalog', HttpDownload, dependsOn: 'token') {
    url = parameter('catalogUrl')
    headers = ['Authorization': 'Bearer ' + result('token')]
    output = workPath('catalog.json')
    maxBytes = 1048576L
}

action('selectDownload', dependsOn: 'catalog') {
    def json = new JsonSlurper().parseText(
        Files.readString(result('catalog').file))
    if (!(json.files instanceof List)) {
        throw new IllegalArgumentException('Katalog ohne files-Liste')
    }
    if (json.files.isEmpty()) {
        finishJob('Keine Quelldateien im Katalog')
    }
    def candidates = json.files.collect { item ->
        [date: java.time.LocalDate.parse(item.date as String),
         uri: java.net.URI.create(item.url as String)]
    }
    return candidates.max { it.date }.uri
}

task('download', HttpDownload, dependsOn: 'selectDownload') {
    url = result('selectDownload')
    output = workPath('data.zip')
}

task('unpack', Extract, dependsOn: 'download') {
    archive = result('download').file
    compression = 'ZIP'
    outputDirectory = workPath('out')
}

defaults 'unpack'
