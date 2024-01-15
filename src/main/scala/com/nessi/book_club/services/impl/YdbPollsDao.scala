package com.nessi.book_club.services.impl

import com.nessi.book_club.services.PollsDao
import com.nessi.book_club.model.Poll
import zio.{ZIO, Task, ULayer}
import tech.ydb.core.grpc.GrpcTransport
import tech.ydb.auth.TokenAuthProvider
import tech.ydb.auth.iam.CloudAuthHelper
import tech.ydb.table.{TableClient, SessionRetryContext}
import tech.ydb.table.settings.ExecuteScanQuerySettings
import tech.ydb.table.transaction.TxControl
import tech.ydb.table.query.Params
import tech.ydb.core.Status
import tech.ydb.table.result.ResultSetReader
import java.util.concurrent.atomic.AtomicBoolean
import zio.Chunk
import java.util.function.BiFunction
import tech.ydb.core.UnexpectedResultException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import io.circe.parser.*

import scala.concurrent.duration._
import java.time.Duration
import com.nessi.book_club.model.Poll.PollType
import zio.{RLayer, ZLayer}
import com.nessi.book_club.ydb.Ydb
import zio.stream.ZStream
import tech.ydb.table.values.PrimitiveValue
import io.circe.syntax.*
import java.time.Instant
import io.circe.Decoder

final case class YdbPollsDao private (ydb: Ydb) extends PollsDao {

  override def getPoll(id: String): Task[Option[Poll]] =
    val query = """
    DECLARE $pollId AS Utf8;
    SELECT * from `polls`
    WHERE id = $pollId 
    """.stripMargin
    ydb
      .runTx(
        ydb
          .execute(
            query,
            Params.of(
              "$pollId",
              PrimitiveValue.newText(id)
            )
          )
      )
      .flatMap(r =>
        if r.resultSet.next then
          mapper {
            r.resultSet
          }.map(Some.apply)
        else ZIO.none
      )

  override def upsertPoll(poll: Poll): Task[Unit] =
    val query =
      """
      |DECLARE $pollId as Utf8;
      |DECLARE $type as Utf8;
      |DECLARE $answers as Json;
      |DECLARE $not_reading as Json;
      |DECLARE $closed as Bool;
      |DECLARE $created_at as Timestamp;
      |UPSERT INTO `polls`
      |    ( `id`, `type`, `answers`, `not_reading`, `closed`, `createdAt`)
      |VALUES ($pollId, $type, $answers, $not_reading, $closed, $created_at);
      |
      |""".stripMargin
    val params = Params.of(
      "$pollId",
      PrimitiveValue.newText(poll.id),
      "$type",
      PrimitiveValue.newText(poll.`type`.toString()),
      "$answers",
      PrimitiveValue.newJson(poll.answers.asJson.noSpaces),
      "$not_reading",
      PrimitiveValue.newJson(poll.notReading.asJson.noSpaces),
      "$closed",
      PrimitiveValue.newBool(poll.closed),
      "$created_at",
      PrimitiveValue.newTimestamp(Instant.now)
    )
    ydb
      .runTx(ydb.execute(query, params))
      .unit

  override def getAll: ZStream[Any, Throwable, Poll] =
    ydb.scanZIO(
      "SELECT * FROM polls",
      Params.empty(),
      mapper,
      ExecuteScanQuerySettings.newBuilder().build
    )

  private val mapper: ResultSetReader => Task[Poll] = rs =>
    for {
      id <- ZIO.attempt(rs.getColumn("id").getText)
      `type` <- ZIO
        .attempt(rs.getColumn("type").getText)
        .flatMap(s => ZIO.fromOption(Poll.PollType.fromString(s)))
        .orElseFail(new RuntimeException("Could not parse `type` column"))
      answersJson <- ZIO.fromEither(parse(rs.getColumn("answers").getJson))
      answers <- ZIO.fromEither(
        Decoder[Map[String, Set[String]]].decodeJson(answersJson)
      )
      notReadingJson <- ZIO.fromEither(
        parse(rs.getColumn("not_reading").getJson)
      )
      notReading <- ZIO.fromEither(
        Decoder[Set[String]].decodeJson(notReadingJson)
      )
      closed <- ZIO.attempt(rs.getColumn("closed").getBool())
    } yield Poll(
      id = id,
      `type` = `type`,
      answers = answers,
      notReading = notReading,
      closed = closed
    )

}

object YdbPollsDao {
  def layer: RLayer[Ydb, PollsDao] =
    ZLayer.fromFunction(YdbPollsDao.apply _)
}
