package com.nessi.book_club.utils

import zio.{UIO, ZIO, IO}

object ZIOHelper {
  extension [A](o: A)
    def success: UIO[A] =
        ZIO.succeed(o)
    def failure: IO[A, Nothing] =
        ZIO.fail(o)
}
