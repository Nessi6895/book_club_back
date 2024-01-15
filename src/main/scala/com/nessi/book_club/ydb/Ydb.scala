package com.nessi.book_club.ydb

import com.nessi.book_club.ydb.model.{TxTask, TxZIO, YdbResult}
import tech.ydb.table.query.Params
import tech.ydb.table.result.ResultSetReader
import tech.ydb.table.settings.ExecuteScanQuerySettings
import tech.ydb.table.transaction.Transaction.Mode as TransactionMode
import zio.{IO, ZIO}
import zio.stream.ZStream
import izumi.reflect.Tag

trait Ydb {
  def execute(query: String, params: Params): TxTask[YdbResult]
  
  def scanZIO[E <: Throwable, T](query: String, params: Params, mapper: ResultSetReader => IO[E, T], settings: ExecuteScanQuerySettings): ZStream[Any, Throwable, T]

  def scan[T](query: String, params: Params, mapper: ResultSetReader => T, settings: ExecuteScanQuerySettings): ZStream[Any, Throwable, T] = {
    val wrappedMapper: ResultSetReader => IO[Nothing, T] = rs => ZIO.succeed(mapper(rs))
    scanZIO[Nothing, T](query, params, wrappedMapper, settings)
  }
  
  def commit: TxTask[Unit]
  
  def abort: TxTask[Unit]

  def runTx[R: Tag, A](mode: TransactionMode = defaultTxMode)(action: TxZIO[R, Throwable, A]): ZIO[R, Throwable, A]

  final def runTx[R: Tag, A](action: TxZIO[R, Throwable, A]): ZIO[R, Throwable, A] = runTx()(action)

  def defaultTxMode: TransactionMode =
    TransactionMode.SERIALIZABLE_READ_WRITE
}
