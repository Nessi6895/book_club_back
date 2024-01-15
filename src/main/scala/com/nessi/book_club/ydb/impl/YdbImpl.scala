package com.nessi.book_club.ydb.impl

import com.nessi.book_club.ydb.Ydb
import com.nessi.book_club.ydb.model.{
  TxTask,
  TxZIO,
  Transaction,
  TxEnv,
  YdbResult
}
import com.nessi.book_club.ydb.model.Transaction.{
  TransactionWithMode,
  TransactionWithId
}
import com.nessi.book_club.utils.PureConfigHelper.*
import tech.ydb.core.{Result, UnexpectedResultException}
import tech.ydb.table.Session
import tech.ydb.table.query.{DataQueryResult, Params}
import tech.ydb.table.result.ResultSetReader
import tech.ydb.table.settings.{
  ExecuteDataQuerySettings,
  ExecuteScanQuerySettings
}
import tech.ydb.auth.iam.CloudAuthHelper
import tech.ydb.table.transaction.Transaction.Mode as TransactionMode
import tech.ydb.table.{Session, TableClient}
import tech.ydb.table.settings.{CommitTxSettings, RollbackTxSettings}
import tech.ydb.core.grpc.GrpcTransport
import tech.ydb.core.Status
import izumi.reflect.Tag
import pureconfig.*

import zio.stream.ZStream
import zio.{IO, Scope, Task, UIO, ZIO, Exit, ZLayer, ULayer, RLayer}
import zio.Chunk
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiFunction
import java.time.Duration

import YdbImpl._

case class YdbImpl(tableClient: TableClient, timeout: FiniteDuration)
    extends Ydb {

  override def execute(query: String, params: Params) = {
    for {
      txEnv <- ZIO.service[TxEnv[_]]
      tx <- txEnv.getTx
      _ <- ZIO
        .dieMessage(s"Transaction already committed: $tx")
        .when(tx.isCommitted)
      result: Result[DataQueryResult] <- {
        ZIO
          .fromCompletionStage {
            tx.session
              .executeDataQuery(
                query,
                tx.control,
                params,
                new ExecuteDataQuerySettings().setTimeout(
                  Duration.ofMillis(timeout.toMillis)
                )
              )
          }
          .tapError(e => ZIO.attempt(println(s"Error: $e")))
      }
      res <- TxTask.apply(result.getValue)
      newTx <- TxTask.apply(
        if (tx.autoCommit) tx.markCommitted else tx.withTxId(res.getTxId())
      )
      _ <- txEnv.setTx(newTx)
    } yield {
      val sets = (0 until res.getResultSetCount).map(res.getResultSet).toList
      YdbResult(sets, result.getStatus)
    }
  }.uninterruptible

  override def scanZIO[E <: Throwable, T](
      query: String,
      params: Params,
      mapper: ResultSetReader => IO[E, T],
      settings: ExecuteScanQuerySettings
  ): ZStream[Any, Throwable, T] =
    for {
      session <- ZStream.scoped(managedSession)
      res <- ZStream.asyncInterrupt[Any, Throwable, T] { cb =>
        val isRunning = new AtomicBoolean(true)
        val buffer = scala.collection.mutable.Buffer.empty[T]
        val consume: ResultSetReader => Unit = r =>
          while r.next do
            if isRunning.get() then
              cb(mapper(r).map(Chunk.single).mapError(Some(_)))
            else throw StopException

        val cf =
          session.executeScanQuery(query, params, settings).start(consume(_))

        cf.handle[Unit](new BiFunction[Status, Throwable, Unit] {
          override def apply(status: Status, err: Throwable): Unit = {
            if status.isSuccess then cb(ZIO.fail(None))
            else if err == null then
              cb(
                ZIO.fail(
                  Some(
                    new UnexpectedResultException(
                      s"Failed to execute scan query: $query",
                      status
                    )
                  )
                )
              )
            else cb(ZIO.fail(Some(err)))
          }
        })
        Left(ZIO.succeed[Any](isRunning.set(false)))
      }
    } yield res

  override def runTx[R: Tag, A](
      mode: TransactionMode
  )(action: TxZIO[R, Throwable, A]) =
    for {
      r <- ZIO.environment[R]
      res <- ZIO.scoped(
        for {
          session <- managedSession
          transaction = TransactionWithMode(mode, session, false, false)
          tx <- TxEnv.make(transaction, r)
          exit <- runTransaction[R, A](tx, action)
          res <- ZIO.done(exit)
        } yield res
      )
    } yield res

  private def runTransaction[R: Tag, A](
      env: TxEnv[R],
      action: TxZIO[R, Throwable, A]
  ): UIO[Exit[Throwable, A]] = {
    ZIO.uninterruptibleMask { restore =>
      for {
        res <- restore(action).provide(ZLayer.succeed(env)).exit
        tx <- env.getTx
        commitResult <-
          if (tx.isCommitted) ZIO.unit.exit
          else {
            val shouldCommit = res match {
              case Exit.Success(_) => true
              case Exit.Failure(_) => false
            }

            if (shouldCommit) then
              commit.exit.provide(ZLayer.succeed(env): ULayer[TxEnv[Any]])
            else abort.exit.provide(ZLayer.succeed(env): ULayer[TxEnv[Any]])

          }
      } yield res <* commitResult
    }
  }

  override def commit: TxTask[Unit] =
    for {
      env <- ZIO.service[TxEnv[_]]
      tx <- env.getTx
      _ <- tx.txId match {
        case Some(txId) =>
          ZIO.succeed("Commiting!") *>
            ZIO.fromCompletionStage(
              tx.session.commitTransaction(
                txId,
                new CommitTxSettings().setTimeout(
                  Duration.ofMillis(timeout.toMillis)
                )
              )
            )
        case None => ZIO.unit
      }
      _ <- env.setTx(tx.markCommitted)
    } yield ()

  override def abort: TxTask[Unit] =
    for {
      env <- ZIO.service[TxEnv[_]]
      tx <- env.getTx
      _ <- tx.txId match {
        case Some(txId) =>
          ZIO.fromCompletionStage(
            tx.session.rollbackTransaction(txId, new RollbackTxSettings())
          )
        case None => ZIO.unit
      }
      _ <- env.setTx(tx.markCommitted)
    } yield ()

  private def getSession: Task[Session] =
    for {
      sessionResult <- ZIO.fromCompletionStage(
        tableClient.createSession(Duration.ofMillis(timeout.toMillis))
      )
      session <-
        if (sessionResult.getStatus.isSuccess) {
          ZIO.succeed(sessionResult.getValue)
        } else {
          ZIO.fail(
            new UnexpectedResultException(
              "Failed to acquire session",
              sessionResult.getStatus
            )
          )
        }
    } yield session

  private def releaseSession(session: Session): UIO[Unit] =
    ZIO
      .attempt(session.close())
      .onError(* => ZIO.succeed(()))
      .ignore
      .fork
      .unit

  protected[ydb] def managedSession: ZIO[Scope, Throwable, Session] =
    getSession.withFinalizer(s => releaseSession(s))
}

object YdbImpl {

  private case object StopException extends Throwable

  case class Config(
      connString: String,
      certificateFilePath: String,
      timeout: FiniteDuration
  )

  given ConfigReader[Config] = ConfigReader.fromCursor[Config] { cursor =>
    for {
      connString <- cursor.fluent.at("connection-string").asString
      certificateFilePath <- cursor.fluent.at("certificate-file").asString
      timeout <- cursor.fluent.at("timeout").asFiniteDuration
    } yield Config(
      connString = connString,
      certificateFilePath = certificateFilePath,
      timeout = timeout
    )
  }

  def layer: ZLayer[Config, Throwable, Ydb] = ZLayer.fromZIO {
    for
      config <- ZIO.service[Config]
      authProvider <- ZIO.attempt(
        CloudAuthHelper.getServiceAccountFileAuthProvider(
          config.certificateFilePath
        )
      )
      transport <- ZIO.attempt(
        GrpcTransport
          .forConnectionString(config.connString)
          .withAuthProvider(authProvider)
          .build()
      )
      tableClient <- ZIO.attempt(
        TableClient
          .newClient(transport)
          .build()
      )
    yield YdbImpl(tableClient, config.timeout)
  }

}
