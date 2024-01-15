package com.nessi.book_club.services

import com.nessi.book_club.model.Poll

import zio.Task
import zio.stream.ZStream

trait PollsDao {
  def upsertPoll(poll: Poll): Task[Unit]
  def getPoll(id: String): Task[Option[Poll]]
  def getAll: ZStream[Any, Throwable, Poll]
}
