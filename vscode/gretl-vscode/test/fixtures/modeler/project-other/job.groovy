parameter('other',description:'Other root',defaultValue:'isolated')
action('download') { println parameter('other') }
defaults 'download'
