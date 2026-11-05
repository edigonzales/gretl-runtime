// Statically complete, typed two-file pipeline. The IDE never executes it.
apply from: 'tasks/prepare.groovy'
apply from: 'tasks/finish.groovy'
defaults 'finish'
