# Задача 1.1 — параллельная факторизация (Scala)

Программа генерирует 1000 случайных чисел, факторизует их двумя способами и
сравнивает время:

* **последовательно** — один поток, обычный перебор делителей до √n;
* **параллельно** — пул из 4 worker-потоков (`Executors.newFixedThreadPool(4)`),
  каждое число — отдельная задача, результаты собираются и складываются
  в общем порядке.

## Требования

* JDK 17+ (установлен: Temurin 17.0.20.1)
* sbt (установлен: пакет sbt 2.0.10 в кеше coursier; версия sbt для проекта —
  1.13.0, она скачивается при первой сборке)

В PATH ничего добавлять не нужно — ниже используются полные пути.

## Запуск

Открыть терминал (PowerShell) и выполнить:

```powershell
cd C:\Users\89242\some_code\npl\1_task_1_scala

# JDK только для текущего сеанса
$env:JAVA_HOME = 'C:\Users\89242\AppData\Local\Coursier\cache\arc\https\github.com\adoptium\temurin17-binaries\releases\download\jdk-17.0.20.1%252B1\OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip\jdk-17.0.20.1+1'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Сборка и запуск
& 'C:\Users\89242\AppData\Local\Coursier\cache\arc\https\github.com\sbt\sbt\releases\download\v2.0.10\sbt-2.0.10.zip\sbt\bin\sbt.bat' run
```