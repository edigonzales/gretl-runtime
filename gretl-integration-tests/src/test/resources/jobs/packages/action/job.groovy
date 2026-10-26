parameter('output', type: 'path', required: true, description: 'Result directory')
action('success') {
    java.nio.file.Files.copy(file('message.txt'), parameter('output').resolve('message.txt'))
}
action('failure') { throw new IllegalStateException('selected-package-business-failure') }
cleanup('complete') { java.nio.file.Files.writeString(parameter('output').resolve('cleaned.txt'), 'cleaned') }
defaults 'success'
