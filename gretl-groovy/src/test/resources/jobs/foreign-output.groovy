task('echo', Echo) {
    text = 'x'
}

action('say', dependsOn: 'echo') {
    System.out.println('foreign-out')
    println 'product-out'
    return 'ok'
}

defaults 'say'
