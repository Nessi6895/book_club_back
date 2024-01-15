package com.nessi.book_club.services.impl

import com.nessi.book_club.services.PollsManager
import zio.{IO, ZIO}
import com.nessi.book_club.services.PollsManager.PollsManagerError
import com.nessi.book_club.model.{Voter, VoteOption, Poll}
import com.nessi.book_club.services.PollsDao
import com.nessi.book_club.model.PollMutations.*
import com.nessi.book_club.services.impl.LivePollsManager.*
import com.nessi.book_club.utils.ZIOHelper.*
import zio.{ZLayer, URLayer, Task}

final case class LivePollsManager private (dao: PollsDao) extends PollsManager {

  override def upsertPoll(poll: Poll): IO[PollsManagerError, Unit] =
    dao
      .upsertPoll(poll)
      .mapError(PollsManagerError.UnknownError.apply)
  override def vote(
      pollId: String,
      option: VoteOption,
      voter: Voter
  ): IO[PollsManagerError, Boolean] =
    for
      poll <- getPoll(pollId)
      updatedPoll <- poll.vote(option, voter).mapError(_.toLocalError)
      _ <- dao
        .upsertPoll(updatedPoll)
        .mapError(PollsManagerError.UnknownError.apply)
      isAddedVote <- updatedPoll.getAnswers(voter).contains(option).success
    yield isAddedVote

  override def closePoll(pollId: String): IO[PollsManagerError, Unit] =
    for
      poll <- getPoll(pollId)
      _ <- dao
        .upsertPoll(poll.copy(closed = true))
        .mapError(PollsManagerError.UnknownError.apply)
    yield ()

  override def notReading(
      pollId: String,
      voter: Voter
  ): IO[PollsManagerError, Unit] =
    for
      poll <- dao
        .getPoll(pollId)
        .mapError(PollsManagerError.UnknownError.apply)
        .someOrFail(PollsManagerError.PollNotExist(pollId))
      updatedPoll <- poll.wontRead(voter).mapError(_.toLocalError)
      _ <- dao
        .upsertPoll(updatedPoll)
        .mapError(PollsManagerError.UnknownError.apply)
    yield ()

  override def getAll: IO[PollsManagerError, Seq[Poll]] =
    dao.getAll.runCollect
      .mapError(PollsManagerError.UnknownError.apply)

  override def getPoll(pollId: String): IO[PollsManagerError,Poll] =
    dao
      .getPoll(pollId)
      .mapError(PollsManagerError.UnknownError.apply)
      .someOrFail(PollsManagerError.PollNotExist(pollId))

}

object LivePollsManager {
  extension (e: PollMutationError)
    private def toLocalError: PollsManagerError =
      e match
        case PollMutationError.RevotingOnFinalPoll(id, chosenOption) =>
          PollsManagerError.RevotingOnFinalPoll(id, chosenOption)
        case PollMutationError.NoSuchOption(id, option, valid) =>
          PollsManagerError.NoSuchOption(id, option, valid)
        case PollMutationError.ClosedPollMutation(id) =>
          PollsManagerError.PollIsClosed(id)

  val layer: URLayer[PollsDao, PollsManager] =
    ZLayer.fromFunction(LivePollsManager.apply _)
}
