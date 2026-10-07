# Задача 1.4 — параллельный обход дерева каталогов (Groovy)

Все подкаталоги рекурсивно собираются в `ConcurrentLinkedQueue`, затем N потоков
берут каталоги из очереди и складывают статистику: количество файлов по
расширениям (`ConcurrentHashMap`) и суммарный размер (`AtomicLong`).
Печатается топ-10 расширений, общий размер и время для 1 и N потоков.

## Запуск

```powershell
cd C:\Users\89242\some_code\npl\1_task_4_groovy
& 'C:\Users\89242\AppData\Local\groovy\groovy-4.0.33\bin\groovy.bat' Main.groovy 'C:\Users\89242\some_code\npl'
```

Второй аргумент — число потоков (по умолчанию все ядра). Нужен JDK 17
(установлен, `JAVA_HOME` уже прописан).
