import java.util.concurrent.{Callable, Executors}

object ParallelFactorization {

  val MaxNumber: Long = 1_000_000_000_000L

  // Возвращает список пар (простой множитель, степень).
  def factorize(n: Long): List[(Long, Int)] = {
    def loop(x: Long, d: Long, acc: List[(Long, Int)]): List[(Long, Int)] = {
      if (d * d > x) {
        if (x > 1) acc :+ (x, 1) else acc
      } else if (x % d == 0) {
        var count = 0
        var rest = x
        while (rest % d == 0) {
          rest /= d
          count += 1
        }
        loop(rest, d + 1, acc :+ (d, count))
      } else {
        loop(x, d + 1, acc)
      }
    }
    if (n < 2) Nil else loop(n, 2, Nil)
  }
  
  def generateNumbers(count: Int): List[Long] = {
    val random = new scala.util.Random(42)
    List.fill(count)(2L + random.nextLong(MaxNumber - 1L))
  }

  def factorizeParallel(numbers: List[Long]): List[(Long, List[(Long, Int)])] = {
    val pool = Executors.newFixedThreadPool(4)
    try {
      val futures = numbers.map { n =>
        pool.submit(new Callable[(Long, List[(Long, Int)])] {
          override def call(): (Long, List[(Long, Int)]) = (n, factorize(n))
        })
      }
      // Сначала отправляем все задачи, затем собираем результаты по порядку.
      futures.map(_.get())
    } finally {
      pool.shutdown()
    }
  }

  def main(args: Array[String]): Unit = {
    val numbers = generateNumbers(1000)

    val seqStart = System.nanoTime()
    val seqResults = numbers.map(n => (n, factorize(n)))
    val seqTime = System.nanoTime() - seqStart

    val parStart = System.nanoTime()
    val parResults = factorizeParallel(numbers)
    val parTime = System.nanoTime() - parStart

    require(seqResults == parResults, "Sequential and parallel results differ")

    println("=== Sequential examples ===")
    seqResults.take(2).foreach { case (n, factors) =>
      println(s"$n = ${factors.map { case (p, e) => if (e > 1) s"$p^$e" else s"$p" }.mkString(" * ")}")
    }
    println("=== Parallel examples (4 threads) ===")
    parResults.take(2).foreach { case (n, factors) =>
      println(s"$n = ${factors.map { case (p, e) => if (e > 1) s"$p^$e" else s"$p" }.mkString(" * ")}")
    }
    println(s"Results match: ${parResults.size} numbers")
    println(f"Sequential time: ${seqTime / 1_000_000.0}%.2f ms")
    println(f"Parallel time (4 threads): ${parTime / 1_000_000.0}%.2f ms")
    println(f"Speedup: ${seqTime.toDouble / parTime}%.2fx")
  }
}
