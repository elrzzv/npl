import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

static void collectDirs(Path root, ConcurrentLinkedQueue<Path> queue) {
    Deque<Path> stack = new ArrayDeque<>()
    stack.push(root)
    while (!stack.isEmpty()) {
        Path dir = stack.pop()
        queue.add(dir)
        Files.newDirectoryStream(dir).withCloseable { stream ->
            stream.forEach { child ->
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    stack.push(child)
                }
            }
        }
    }
}

static void worker(ConcurrentLinkedQueue<Path> queue,
                   ConcurrentHashMap<String, AtomicLong> extCount,
                   AtomicLong totalSize, AtomicLong fileCount) {
    Path dir
    while ((dir = queue.poll()) != null) {
        Files.newDirectoryStream(dir).withCloseable { stream ->
            stream.forEach { p ->
                try {
                    if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                        String name = p.getFileName().toString()
                        int dot = name.lastIndexOf('.')
                        String ext = dot > 0 ? name.substring(dot + 1).toLowerCase() : '[нет расширения]'
                        extCount.computeIfAbsent(ext, { k -> new AtomicLong() }).incrementAndGet()
                        totalSize.addAndGet(Files.size(p))
                        fileCount.incrementAndGet()
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }
}

static Map scan(Path root, int nThreads) {
    ConcurrentLinkedQueue<Path> queue = new ConcurrentLinkedQueue<>()
    ConcurrentHashMap<String, AtomicLong> extCount = new ConcurrentHashMap<>()
    AtomicLong totalSize = new AtomicLong()
    AtomicLong fileCount = new AtomicLong()

    long start = System.nanoTime()
    collectDirs(root, queue)
    int dirCount = queue.size()

    List<Thread> threads = (1..nThreads).collect {
        new Thread({ worker(queue, extCount, totalSize, fileCount) } as Runnable)
    }
    threads.each { it.start() }
    threads.each { it.join() }

    long ms = (System.nanoTime() - start) / 1_000_000L
    [ms: ms, extCount: extCount, totalSize: totalSize.get(),
     fileCount: fileCount.get(), dirCount: dirCount]
}

if (args.length < 1) {
    println 'Использование: groovy Main.groovy <каталог> [число потоков]'
    System.exit(1)
}

Path root = Paths.get(args[0])
if (!Files.isDirectory(root)) {
    System.err.println("Это не каталог: $root")
    System.exit(1)
}
int nThreads = args.length > 1 ? Integer.parseInt(args[1]) :
        Runtime.getRuntime().availableProcessors()

println "=== Обход каталога: $root ==="
println "Потоков: $nThreads"

def one = scan(root, 1)
println "1 поток:      ${one.ms} ms"

def many = scan(root, nThreads)
println "$nThreads потоков:  ${many.ms} ms"
double speed = one.ms / Math.max(many.ms as double, 1.0)
println "Ускорение:    ${Math.round(speed * 100) / 100.0}x"

boolean same = one.fileCount == many.fileCount && one.totalSize == many.totalSize
println "Проверка (1 поток == N потоков): ${same ? 'OK' : 'FAIL'}"

println "Папок: ${many.dirCount}, файлов: ${many.fileCount}"
double mb = many.totalSize / 1048576.0
println "Суммарный размер: ${many.totalSize} байт (${Math.round(mb * 10) / 10.0} МБ)"

println 'Топ-10 расширений по количеству файлов:'
many.extCount.entrySet()
        .sort { -it.value.get() }
        .take(10)
        .each { e -> println "  .${e.key} — ${e.value.get()}" }
