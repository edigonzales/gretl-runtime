// tag::oauth2-client-credentials[]
parameter('tokenEndpoint', required: true, description: 'OAuth2 token endpoint')
parameter('apiEndpoint', required: true, description: 'API endpoint')
parameter('localHttp', type: 'boolean', defaultValue: false, description: 'Allow HTTP for the local mock')

task('auth', OAuth2ClientCredentials) {
    tokenUrl = parameter('tokenEndpoint')
    clientId = 'job-client'
    clientSecret = secret('oauthClientSecret')
    allowInsecureHttp = parameter('localHttp')
}

task('callBackend', Curl, dependsOn: 'auth') {
    serverUrl = parameter('apiEndpoint')
    method = 'POST'
    expectedStatusCode = 204
    headers = ['Authorization': result('auth').authorization]
    data = '{"probe":true}'
}

defaults 'callBackend'
// end::oauth2-client-credentials[]

action('redactionProbe', dependsOn: 'callBackend') {
    def authorization = result('auth').authorization
    def clear = authorization.reveal().substring('Bearer '.length())
    def clientSecret = secret('oauthClientSecret').reveal()
    def encoded = java.net.URLEncoder.encode(clientSecret, java.nio.charset.StandardCharsets.UTF_8)
    def basic = 'Basic ' + ('job-client:' + encoded).getBytes('UTF-8').encodeBase64().toString()
    log().info('oauth2-masked-handle ' + authorization)
    log().info('oauth2-masked-token ' + clear)
    log().info('oauth2-masked-client ' + clientSecret)
    log().info('oauth2-masked-basic ' + basic)
}
