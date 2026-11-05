parameter('output', type: 'path', required: true, description: 'Geprüfter UTF-8-Inhalt am Ausgabeort')
apply from: 'tasks/prepare.groovy'
apply(from: "tasks/verify.groovy")
defaults 'verify'
