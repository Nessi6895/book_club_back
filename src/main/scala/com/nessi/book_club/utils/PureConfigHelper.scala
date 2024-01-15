package com.nessi.book_club.utils

import pureconfig.{FluentConfigCursor, ConfigReader, ConfigSource}
import pureconfig.error.{ConfigReaderFailures, ThrowableFailure}
import zio.{ZLayer, ZIO, Tag}

import scala.concurrent.duration.*
import scala.util.{Try, Success, Failure}

object PureConfigHelper {
  extension (c: FluentConfigCursor)
    def asFiniteDuration: ConfigReader.Result[FiniteDuration] =
      c.asString.flatMap { s =>
        Try(Duration(s))
          .flatMap { d =>
            if d.isFinite then Success(FiniteDuration(d.length, d.unit))
            else Failure(new IllegalArgumentException(s"`$d` is not a finite duration!"))
          }
          .toEither
          .left
          .map(t => ConfigReaderFailures(ThrowableFailure(t, None)))
      }

  def loadLayer[T: Tag](prefix: String)(using ConfigReader[T]): ZLayer[Any, ConfigReaderFailures, T] = 
    ZLayer.fromZIO(ZIO.fromEither(ConfigSource.default.at(prefix).load[T]))
}
