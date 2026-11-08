module Main where

import Control.Concurrent
import Control.Concurrent.STM
import Control.Monad (forM, forM_, when)
import Data.Bits (shiftR, xor)
import Data.Word (Word64)
import System.CPUTime (getCPUTime)
import Data.Time.Clock.POSIX (getPOSIXTime)
import System.Exit (exitFailure)

nAccounts :: Int
nAccounts = 8

nThreads :: Int
nThreads = 4

opsPerThread :: Int
opsPerThread = 20000

minStart, maxStart :: Int
minStart = 1000
maxStart = 10000

maxAmount :: Int
maxAmount = 200

-- Простой генератор случайных чисел (LCG + xorshift),
nextSeed :: Word64 -> Word64
nextSeed s = s * 6364136223846793005 + 1442695040888963407

randomUp :: Word64 -> Int -> (Int, Word64)
randomUp s n =
  let s' = nextSeed s
      v = (s' `xor` (s' `shiftR` 33)) `rem` fromIntegral (max 1 n)
  in (fromIntegral v, s')

initialSeed :: IO Word64
initialSeed = do
  t <- getCPUTime
  posix <- getPOSIXTime
  let nanos = floor (posix * 1e9) :: Integer
      s = fromIntegral nanos `xor` fromIntegral t `xor` 0x9E3779B97F4A7C15
  pure (nextSeed s)

-- Один перевод: снимаем сумму со счёта from и кладём на to.
-- Вся операция атомарна; при конфликте STM повторяет транзакцию.
transfer :: [TVar Int] -> Int -> Int -> Int -> STM ()
transfer accounts from to amount = do
  fromBal <- readTVar (accounts !! from)
  when (fromBal >= amount) $ do
    writeTVar (accounts !! from) (fromBal - amount)
    toBal <- readTVar (accounts !! to)
    writeTVar (accounts !! to) (toBal + amount)

-- Поток: opsPerThread попыток случайного перевода.
worker :: MVar () -> [TVar Int] -> Word64 -> IO ()
worker done accounts seed0 = do
  let loop _ 0 = pure ()
      loop s i = do
        let (f, s1) = randomUp s nAccounts
            (t, s2) = randomUp s1 nAccounts
            (a, s3) = randomUp s2 maxAmount
        when (f /= t) $
          atomically $ transfer accounts f t (a + 1)
        loop s3 (i - 1)
  loop seed0 opsPerThread
  putMVar done ()

readBalances :: [TVar Int] -> IO [Int]
readBalances accounts = mapM readTVarIO accounts

main :: IO ()
main = do
  putStrLn "=== Bank transfers via STM ==="
  seed0 <- initialSeed

  -- Создаём счета со случайными балансами
  accounts <- forM [0 .. nAccounts - 1] $ \i -> do
    let (v, _) = randomUp (nextSeed (seed0 + fromIntegral i * 2654435761)) (maxStart - minStart + 1)
    newTVarIO (minStart + v)

  before <- readBalances accounts
  let sumBefore = sum before
  putStrLn $ "Accounts (" ++ show nAccounts ++ "): " ++ show before
  putStrLn $ "Sum before: " ++ show sumBefore

  -- Запускаем nThreads потоков и ждём их завершения через MVar
  done <- newEmptyMVar
  forM_ [0 .. nThreads - 1] $ \i -> forkIO $
    worker done accounts (nextSeed (seed0 + fromIntegral i * 40503 + 7))
  forM_ [0 .. nThreads - 1] $ \_ -> takeMVar done

  after <- readBalances accounts
  let sumAfter = sum after

  putStrLn $ "Accounts after " ++ show (nThreads * opsPerThread)
          ++ " transfer attempts: " ++ show after
  putStrLn $ "Sum after:  " ++ show sumAfter
  if sumBefore == sumAfter && all (>= 0) after
    then putStrLn "CHECK: sum unchanged, balances non-negative -- OK"
    else do
      putStrLn "CHECK: FAILED -- sum changed or a balance is negative"
      exitFailure
