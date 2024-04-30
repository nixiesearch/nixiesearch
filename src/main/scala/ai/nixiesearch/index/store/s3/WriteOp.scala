package ai.nixiesearch.index.store.s3

enum WriteOp {
  case Put(fileName: String) extends WriteOp
  case Delete(fileName: String) extends WriteOp
}
