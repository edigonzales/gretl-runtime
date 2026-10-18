parameter('text', required: true, description: 'Text')
parameter('aktiv', type: 'boolean', defaultValue: true,
          description: 'Task ausfuehren')

task('echo', Echo, when: { parameter('aktiv') }) {
    text = parameter('text')
}

defaults 'echo'
