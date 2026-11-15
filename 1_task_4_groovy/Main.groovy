import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

// Собираем каталоги стеком, не переходя по символическим ссылкам.
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

// Каждый поток забирает из общей очереди отдельный каталог.
static void worker(ConcurrentLinkedQueue<Path> queue,
                   ConcurrentHashMap<String, AtomicLong> extCount,
                   AtomicLong totalSize, AtomicLong fileCount) {
    Path dir
    while ((dir = queue.poll()) != null) {
        Files.newDirectoryStream(dir).withCloseable { stream ->
            stream.forEach { p ->
                if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    String name = p.getFileName().toString()
                    int dot = name.lastIndexOf('.')
                    String ext = dot > 0 && dot < name.length() - 1 ?
                            name.substring(dot + 1).toLowerCase(Locale.ROOT) : '[no extension]'
                    long size = Files.size(p)
                    extCount.computeIfAbsent(ext, { k -> new AtomicLong() }).incrementAndGet()
                    totalSize.addAndGet(size)
                    fileCount.incrementAndGet()
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
    ConcurrentLinkedQueue<Exception> errors = new ConcurrentLinkedQueue<>()

    long start = System.nanoTime()
    collectDirs(root, queue)
    int dirCount = queue.size()

    List<Thread> threads = (1..nThreads).collect {
        new Thread({
            try {
                worker(queue, extCount, totalSize, fileCount)
            } catch (Exception e) {
                errors.add(e)
            }
        } as Runnable)
    }
    threads.each { it.start() }
    threads.each { it.join() }
    if (!errors.isEmpty()) {
        throw new IOException('Could not read a file or directory', errors.peek())
    }

    double ms = (System.nanoTime() - start) / 1_000_000.0
    // После join счётчики уже не меняются; сохраняем обычные числовые значения.
    Map counts = extCount.collectEntries { ext, count -> [(ext): count.get()] }
    [ms: ms, extCount: counts, totalSize: totalSize.get(),
     fileCount: fileCount.get(), dirCount: dirCount]
}

static boolean sameStats(Map first, Map second) {
    first.fileCount == second.fileCount && first.totalSize == second.totalSize &&
            first.dirCount == second.dirCount && first.extCount == second.extCount
}

static Map benchmark(Path root, int nThreads) {
    // Прогреваем оба варианта до начала замеров.
    3.times {
        scan(root, 1)
        scan(root, nThreads)
    }
    List oneRuns = []
    List manyRuns = []
    // Чередуем порядок, чтобы один вариант не запускался всегда первым.
    5.times { run ->
        if (run % 2 == 0) {
            oneRuns.add(scan(root, 1))
            manyRuns.add(scan(root, nThreads))
        } else {
            manyRuns.add(scan(root, nThreads))
            oneRuns.add(scan(root, 1))
        }
    }
    if (!(oneRuns + manyRuns).every { sameStats(oneRuns[0], it) }) {
        throw new IOException('Directory contents changed between scans')
    }
    // Медиана
    [one: oneRuns.sort { it.ms }[2], many: manyRuns.sort { it.ms }[2]]
}

if (args.length < 1 || args.length > 2) {
    println 'Usage: groovy Main.groovy <directory> [threads]'
    System.exit(1)
}

Path root = Paths.get(args[0])
if (!Files.isDirectory(root)) {
    System.err.println("Not a directory: $root")
    System.exit(1)
}

if (args.length > 1 && (!args[1].isInteger() || args[1].toInteger() < 1)) {
    System.err.println('Thread count must be a positive integer')
    System.exit(1)
}

int nThreads = args.length > 1 ? args[1].toInteger() :
        Runtime.getRuntime().availableProcessors()

println "=== Directory scan: $root ==="
println "Threads: $nThreads"
println 'Timing: median of 5 runs after warm-up'

def results = benchmark(root, nThreads)
def one = results.one
println "1 thread: ${Math.round(one.ms * 100) / 100.0} ms"

def many = results.many
println "$nThreads threads: ${Math.round(many.ms * 100) / 100.0} ms"
double speed = one.ms / many.ms
println "Speedup: ${Math.round(speed * 100) / 100.0}x"

boolean same = sameStats(one, many)
println "Results match: ${same ? 'OK' : 'FAIL'}"
if (!same) System.exit(1)

println "Directories: ${many.dirCount}, files: ${many.fileCount}"
double mb = many.totalSize / 1048576.0
println "Total size: ${many.totalSize} bytes (${String.format(Locale.ROOT, '%.1f', mb)} MiB)"

println 'Top 10 extensions:'
many.extCount.entrySet()
        .sort { -it.value }
        .take(10)
        .each { e -> println "  ${e.key}: ${e.value}" }
