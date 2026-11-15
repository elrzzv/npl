import java.nio.file.Files
import java.nio.file.Paths

// Создаём отдельный пример; существующие каталоги не перезаписываем.
def root = Paths.get('target', 'demo')
if (Files.exists(root)) {
    System.err.println('Demo directory already exists: target/demo')
    System.exit(1)
}
Files.createDirectories(root.parent)
Files.createDirectory(root)

def random = new Random(42)
def extensions = ['txt', 'csv', 'json', 'log', 'dat']
long totalSize = 0

// Разные каталоги позволяют распределить работу между потоками.
50.times { directory ->
    def dir = Files.createDirectory(root.resolve("dir-$directory"))
    20.times { file ->
        String ext = extensions[random.nextInt(extensions.size())]
        byte[] content = new byte[1 + random.nextInt(4096)]
        random.nextBytes(content)
        Files.write(dir.resolve("file-$file.$ext"), content)
        totalSize += content.length
    }
}
println 'Created: target/demo (1000 files, 50 subdirectories)'
println "Total size: $totalSize bytes"
