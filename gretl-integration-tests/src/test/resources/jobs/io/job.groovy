parameter('output', type: 'path', required: true, description: 'Writable output')
parameter('url', defaultValue: 'http://unused.invalid', description: 'Local HTTP resource')
parameter('variant', defaultValue: 'file', description: 'Original variant')
task('download', HttpDownload) {
    url = parameter('url'); output = parameter('output').resolve('download.bin')
}
task('curl', Curl) {
    serverUrl = parameter('url'); expectedStatusCode = 200; expectedBody = 'success'
    if (parameter('variant') == 'form') formData = [topic: 'npl', file: file('payload.txt')]
    if (parameter('variant') == 'data') data = 'grant_type=client_credentials'
    if (parameter('variant') == 'binary') dataBinary = file('payload.txt')
    outputFile = parameter('output').resolve('response.txt')
}
task('extract', Extract) {
    archive = file('payload.zip'); compression = 'ZIP'; outputDirectory = parameter('output')
}
task('xsl', XslTransformer) {
    xmlFiles = parameter('variant') == 'set' ? [file('XslTransformerFileSet/MeldungAnGeometer_G-0098981_20230214_104054_Koordinaten.xml'),
        file('XslTransformerFileSet/MeldungAnGeometer_mehrere_gebaeude_mehrere_grundstuecke.xml')] :
        [file('XslTransformerFile/MeldungAnGeometer_G-0098981_20230214_104054_Koordinaten.xml')]
    if (parameter('variant') == 'resource') xslResource = 'eCH0132_to_SO_AGI_SGV_Meldungen_20221109.xsl'
    else xslFile = file('XslTransformerFile/eCH0132_to_SO_AGI_SGV_Meldungen_20221109.xsl')
    outDirectory = parameter('output')
}
defaults 'extract'
task('curl-original', Curl) {
    serverUrl = parameter('url'); user = 'fooUser'; password = secret('curlPassword')
    if (parameter('variant') == 'planregister') {
        headers = ['Content-Type': 'application/xml', 'Content-Encoding': 'gzip']; dataBinary = file('foo.xml.gz'); expectedStatusCode = 202
    } else if (parameter('variant') == 'download') {
        expectedStatusCode = 200; outputFile = parameter('output').resolve('README.md')
    } else {
        formData = [topic: 'npl_waldgrenzen', lv95_file: file('test.xtf.zip'), publish: 'true', replace_all: 'true']
        expectedStatusCode = 200; expectedBody = '"success":true'
    }
}
