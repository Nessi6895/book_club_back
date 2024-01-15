package com.nessi.book_club

import com.nessi.book_club.model.Poll
import com.nessi.book_club.ydb.impl.YdbImpl
import com.nessi.book_club.ydb.impl.YdbImpl.given
import com.nessi.book_club.model.json.decoders.CirceDecoders.given
import com.nessi.book_club.model.json.encoders.CirceEncoders.given
import com.nessi.book_club.services.impl.{YdbPollsDao, LivePollsManager}
import com.nessi.book_club.routes.PollsRouter
import com.nessi.book_club.utils.PureConfigHelper.*
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.parser.*
import zio.*
import zio.http.*

import scala.concurrent.duration.*

object Main extends ZIOAppDefault {

  def run =
    for
      pollsRouter <- ZIO.service[PollsRouter].provide(pollsRouter)
      app = pollsRouter.routes.toHttpApp
      _ <- Server
        .serve(app)
        .provide(Server.default)
    yield ()

  private val pollsRouter: Layer[Throwable, PollsRouter] =
    ZLayer.make[PollsRouter](
      loadLayer[YdbImpl.Config]("ydb-config")
        .mapError(e => new RuntimeException(e.prettyPrint())),
      YdbImpl.layer,
      YdbPollsDao.layer,
      LivePollsManager.layer,
      PollsRouter.layer
    )
}
