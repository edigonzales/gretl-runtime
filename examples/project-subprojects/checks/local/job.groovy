import java.nio.file.Files

action('run') {
    def text = Files.readString(file('input.txt')).strip()
    println text
    return text
}
defaults 'run'
