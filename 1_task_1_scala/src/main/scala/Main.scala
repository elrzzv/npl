import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import java.util.concurrent.{ExecutorService, Executors}

object ParallelFactorization {

  val pool: ExecutorService = Executors.newFixedThreadPool(4)
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(pool)
  
  // Факторизация числа: возвращает список (простое, степень)
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
  
  def main(args: Array[String]): Unit = {
    val random = new scala.util.Random(42)
    val numbers = List.fill(1000)(2L + random.nextLong(10000000000000000L))
    
    // Последовательная версия
    println("=== Последовательная версия ===")
    val seqStart = System.currentTimeMillis()
    val seqResults = numbers.map(n => (n, factorize(n)))
    val seqTime = System.currentTimeMillis() - seqStart
    
    seqResults.take(5).foreach { case (n, factors) =>
      println(s"$n = ${factors.map { case (p, e) => if (e > 1) s"$p^$e" else s"$p" }.mkString(" * ")}")
    }
    println(s"Время: $seqTime ms")
    
    // Параллельная версия
    println("\n=== Параллельная версия (4 потока) ===")
    val parStart = System.currentTimeMillis()
    val futures = numbers.map(n => Future { (n, factorize(n)) })
    val parResults = Await.result(Future.sequence(futures), 60.seconds)
    val parTime = System.currentTimeMillis() - parStart
    
    parResults.take(5).foreach { case (n, factors) =>
      println(s"$n = ${factors.map { case (p, e) => if (e > 1) s"$p^$e" else s"$p" }.mkString(" * ")}")
    }
    println(s"Время: $parTime ms")
    println(s"Ускорение: ${"%.2f".format(seqTime.toDouble / parTime)}x")
    
    pool.shutdown()
  }
}