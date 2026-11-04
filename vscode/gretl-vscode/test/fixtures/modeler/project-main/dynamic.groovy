// Static analysis only: never execute this fixture.
def chosen=System.getProperty('gretl.dynamic.name')
action(chosen) { println 'dynamic' }
action('known') { println 'known' }
