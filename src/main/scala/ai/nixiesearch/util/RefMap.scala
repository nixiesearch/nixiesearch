package ai.nixiesearch.util

import cats.effect.{IO, Ref}

case class RefMap[K, V](refmap: Ref[IO, Map[K, V]]) {
  def keys(): IO[List[K]]                                 = refmap.get.map(_.keys.toList)
  def values(): IO[List[V]]                               = refmap.get.map(_.values.toList)
  def get(key: K): IO[Option[V]]                          = refmap.get.map(_.get(key))
  def set(key: K, value: V): IO[Unit]                     = refmap.update(map => map + (key -> value))
  def update(key: K, f: Option[V] => Option[V]): IO[Unit] = refmap.update(map => map.updatedWith(key)(f))
}

object RefMap {
  def of[K, V](map: Map[K, V]): IO[RefMap[K, V]] = Ref.of[IO, Map[K, V]](map).map(RefMap.apply)
}
