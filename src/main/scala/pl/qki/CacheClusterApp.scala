package pl.qki

object CacheClusterApp extends App {
  CacheRouter.main(Seq("2551").toArray)
  CacheWorker.main(Seq("2552").toArray)
  CacheWorker.main(Array.empty)
  CacheWorker.main(Array.empty)
}
