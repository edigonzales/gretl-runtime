import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files

parameter('url', type: 'text', required: true, description: 'Export endpoint')
parameter('output', type: 'path', required: true, description: 'Output directory')

action('download') {
    def request = HttpRequest.newBuilder(URI.create(parameter('url')))
        .header('Authorization', 'Bearer ' + secret('exportToken').reveal())
        .timeout(java.time.Duration.ofSeconds(10)).GET().build()
    def response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray())
    switch (response.statusCode()) {
        case 200:
            return Files.write(workPath('export.xtf'), response.body())
        case 204:
            finishJob('No changed export data')
            return
        default:
            throw new IllegalStateException("Export failed with HTTP ${response.statusCode()}")
    }
}
action('process', dependsOn: 'download') {
    def data = Files.readString(result('download'))
    assert data == '<TRANSFER/>'
    Files.writeString(parameter('output').resolve('processed.xtf'), data)
}
defaults 'process'
