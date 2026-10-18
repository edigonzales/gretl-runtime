parameter('aktiv', type: 'boolean', defaultValue: true,
          description: 'Task ausfuehren')

task('echo', Echo, when: { ctx -> ctx.parameter('aktiv') }) {
    text = 'when-ctx'
}

defaults 'echo'
