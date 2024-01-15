package com.nessi.book_club.utils

import JsonZIOReader.ParsingError.*
import io.circe.{Decoder, Json}
import io.circe.parser.*
import zio.http.Request
import zio.{ZIO, IO}

object JsonZIOReader:
  def readBody[T](r: Request)(using Decoder[T]): IO[ParsingError, T] =
    for
      body <- r.body.asString
        .mapError(IncorrectBody.apply)
      json <- ZIO
        .fromEither(parse(body))
        .orElseFail(NotAJson(body))
      value <- ZIO
        .fromEither(summon[Decoder[T]].decodeJson(json))
        .orElseFail(CannotDecode(json))
    yield value

  sealed trait ParsingError extends Throwable

  object ParsingError:
    case class IncorrectBody(inner: Throwable) extends ParsingError

    case class NotAJson(body: String) extends ParsingError
  
    case class CannotDecode(json: Json) extends ParsingError
