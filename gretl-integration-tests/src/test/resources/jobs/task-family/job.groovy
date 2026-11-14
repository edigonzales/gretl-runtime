import java.nio.file.Files

parameter('output', type: 'path', required: true, description: 'Observable task outputs')

def generated = ['a', 'b', 'c'].collect { value ->
    def item = value
    action("write_${item}".toString()) {
        def target = workPath('value.txt')
        Files.writeString(target, item)
        Files.writeString(parameter('output').resolve(item + '.txt'), item)
        target
    }
}

action('all', dependsOn: generated) {
    def paths = generated.collect { ref -> result(ref) }
    assert paths.toSet().size() == 3
    assert paths.collect { Files.readString(it) } == ['a', 'b', 'c']
    Files.writeString(parameter('output').resolve('collected.txt'), 'abc')
}

defaults 'all'
