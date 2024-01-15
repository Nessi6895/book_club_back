package com.nessi.book_club.ydb.model

import zio.{Ref, UIO, ZEnvironment, ZIO}
import com.nessi.book_club.ydb.model.Transaction

trait TxEnv[+R] {
  def env: UIO[ZEnvironment[R]]

  def getTx: UIO[Transaction]
  def setTx(tx: Transaction): UIO[Unit]

  final def modifyTx(m: Transaction => Transaction): UIO[Unit] =
    getTx.flatMap(tx => setTx(m(tx)))
}

object TxEnv {

  class SimpleTxEnv[R] private[ydb](private[ydb] val ref: Ref[Transaction], r: ZEnvironment[R]) extends TxEnv[R] {
    override def env: UIO[ZEnvironment[R]] = ZIO.succeed(r)

    override def getTx: UIO[Transaction] = ref.get

    override def setTx(tx: Transaction): UIO[Unit] = ref.set(tx)
  }

  def make[R](tx: Transaction, env: ZEnvironment[R]): UIO[TxEnv[R]] =
    for {
      ref <- Ref.make(tx)
    } yield new SimpleTxEnv(ref, env)


}
