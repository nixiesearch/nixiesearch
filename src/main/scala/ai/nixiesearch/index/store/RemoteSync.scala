package ai.nixiesearch.index.store

trait RemoteSync {
  def pushFiles(present: List[String], deletions: List[String]): Unit = ???
}

object Remote
