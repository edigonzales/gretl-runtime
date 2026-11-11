parameter('url', required: true, description: 'Base URL of the HTTP service')

task('response', Curl) {
    serverUrl = parameter('url') + '/request'
    expectedStatusCode = 200
    outputFile = workPath('response.json')
}

task('readName', JsonRead, dependsOn: 'response') {
    sourceFile = result('response').outputFile
    pointer = '/name'
}

task('sendName', Curl, dependsOn: 'readName') {
    serverUrl = parameter('url') + '/complete'
    method = 'POST'
    expectedStatusCode = 204
    headers = ['Content-Type': 'text/plain; charset=utf-8']
    data = result('readName').value
}

defaults 'sendName'
