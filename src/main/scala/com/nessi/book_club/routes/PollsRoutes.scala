package com.nessi.book_club.routes

import com.nessi.book_club.services.PollsManager
import com.nessi.book_club.services.PollsManager.PollsManagerError
import com.nessi.book_club.model.Poll
import com.nessi.book_club.model.json.encoders.CirceEncoders.given
import com.nessi.book_club.model.json.decoders.CirceDecoders.given
import com.nessi.book_club.routes.PollsRouter.*
import com.nessi.book_club.utils.ZIOHelper.*
import com.nessi.book_club.utils.JsonZIOReader.*
import com.nessi.book_club.model.request.*
import io.circe.{Json, Encoder, Decoder}
import io.circe.parser.*
import zio.http.*
import zio.{ZIO, URLayer, ZLayer, Task}
import zio.http.codec.PathCodec
import com.nessi.book_club.model.request.CreatePollRequest

final case class PollsRouter private (private val manager: PollsManager):
  def routes: Routes[Any, Nothing] =
    Routes(
      Method.GET / "polls" / "all" -> handler(
        for polls <- manager.getAll
            .mapError(_.toError)
        yield Response.json(
          Json
            .arr(
              polls.map(p => summon[Encoder[Poll]].apply(p)): _*
            )
            .noSpaces
        )
      ),
      Method.DELETE / "polls" / string("pollId") -> Handler
        .fromFunctionZIO[(String, Request)] { case (pollId, _) =>
          manager
            .closePoll(pollId)
            .as(Response.ok)
            .catchAll(_.toError.success)
        },
      Method.GET / "polls" / string("pollId") -> Handler
        .fromFunctionZIO[(String, Request)] { case (pollId, _) =>
          manager
            .getPoll(pollId)
            .map(poll =>
              Response.json(summon[Encoder[Poll]].apply(poll).noSpaces)
            )
            .catchAll(_.toError.success)
        },
      Method.POST / "polls" / "new" -> Handler.fromFunctionZIO[Request] { r =>
        for
          poll <- readBody[CreatePollRequest](r)
            .map(_.toPoll)
            .mapError(_.toError)
          _ <- manager.upsertPoll(poll).mapError(_.toError)
        yield Response.ok
      },
      Method.POST / "polls" / "skip" -> Handler.fromFunctionZIO[Request] { r =>
        for
          req <- readBody[NotReadingRequest](r)
            .mapError(_.toError)
          _ <- manager
            .notReading(req.pollId, req.voter)
            .mapError(_.toError)
        yield Response.ok
      },
      Method.POST / "polls" / "vote" -> Handler.fromFunctionZIO[Request] { r =>
        for
          req <- readBody[VoteRequest](r)
            .mapError(_.toError)
          _ <- manager
            .vote(req.pollId, req.option, req.voter)
            .mapError(_.toError)
        yield Response.ok
      }
    )

object PollsRouter:
  val layer: URLayer[PollsManager, PollsRouter] =
    ZLayer.fromFunction(PollsRouter.apply _)

  extension [T: Encoder](value: T)
    private def toError: Response =
      Response
        .json(
          summon[Encoder[T]].apply(value).noSpaces
        )
        .status(Status.BadRequest)
