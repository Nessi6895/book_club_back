package com.nessi.book_club.ydb.model

import zio.ZIO

object TxTask {
  def apply[A](a: => A): TxTask[A] = ZIO.attempt(a)
}

