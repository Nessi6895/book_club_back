package com.nessi.book_club.services

import com.nessi.book_club.model.*
import zio.{IO, Task}

import PollsManager.*

trait PollsManager {
  def vote(
      pollId: String,
      option: VoteOption,
      voter: Voter
  ): IO[PollsManagerError, Boolean]

  def notReading(pollId: String, voter: Voter): IO[PollsManagerError, Unit]

  def closePoll(pollId: String): IO[PollsManagerError, Unit]

  def upsertPoll(poll: Poll): IO[PollsManagerError, Unit]

  def getAll: IO[PollsManagerError, Seq[Poll]]

  def getPoll(pollId: String): IO[PollsManagerError, Poll]
}

object PollsManager {

  sealed trait PollsManagerError extends Throwable

  object PollsManagerError {
    final case class PollNotExist(pollId: String) extends PollsManagerError
    final case class PollIsClosed(pollId: String) extends PollsManagerError
    final case class RevotingOnFinalPoll(
        pollId: String,
        chosenOption: VoteOption
    ) extends PollsManagerError
    final case class NoSuchOption(
        pollId: String,
        option: VoteOption,
        validOptions: Set[VoteOption]
    ) extends PollsManagerError
    final case class UnknownError(inner: Throwable) extends PollsManagerError
  }

}
