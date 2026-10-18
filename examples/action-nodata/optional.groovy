parameter('extraCheck', type: 'boolean', defaultValue: false,
          description: 'Zusatzprüfung einschalten')

action('optionalCheck', when: { parameter('extraCheck') }) {
    return 'checked'
}

defaults 'optionalCheck'
