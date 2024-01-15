package com.nessi.book_club.model

import zio.{IO, ZIO}
import com.nessi.book_club.model.Poll.PollType
import com.nessi.book_club.model.PollMutations.PollMutationError.{
  NoSuchOption,
  ClosedPollMutation,
  RevotingOnFinalPoll
}
import com.nessi.book_club.utils.ZIOHelper.*

object PollMutations {
  extension (p: Poll)
    def vote(option: VoteOption, voter: Voter): IO[PollMutationError, Poll] =
      for
        _ <- ClosedPollMutation(p.id).failure.when(p.closed)
        _ <- NoSuchOption(p.id, option, p.answers.keySet).failure
          .when(!p.answers.keySet.contains(option))
        updatedPoll <-
          if getAnswers(voter).isEmpty then
            p.addVote(option, voter).success
          else if p.`type` == PollType.Final then
            RevotingOnFinalPoll(p.id, getAnswers(voter).head).failure
          else if getAnswers(voter).contains(option) then
            p.removeVote(option, voter).success
          else p.addVote(option, voter).success
      yield updatedPoll.copy(notReading = p.notReading.filterNot(_ == voter))

    def wontRead(voter: Voter): IO[PollMutationError, Poll] =
      for
        _ <- ClosedPollMutation(p.id).failure.when(p.closed)
        updatedAnswersPoll <- 
          if getAnswers(voter).isEmpty then
            p.success
          else if p.`type` == PollType.Final then
            RevotingOnFinalPoll(p.id, getAnswers(voter).head).failure
          else
            getAnswers(voter).foldLeft(p){ case (poll, answer) =>
              poll.removeVote(answer, voter)
            }.success
        updatedNotReadinPoll <- 
          if !p.notReading.contains(voter) then
            updatedAnswersPoll.copy(notReading = p.notReading + voter).success
          else
            updatedAnswersPoll.copy(notReading = p.notReading.filterNot(_ == voter)).success
      yield updatedNotReadinPoll

    def getAnswers(voter: Voter): Set[VoteOption] =
      p.answers
        .filter { case (_, voters) => voters.contains(voter) }
        .map(_._1)
        .toSet

    private def removeVote(option: VoteOption, voter: Voter): Poll =
      p.copy(answers =
        p.answers + (option -> p.answers
          .getOrElse(option, Set.empty)
          .removedAll(Set(voter)))
      )

    private def addVote(option: VoteOption, voter: Voter): Poll =
      p.copy(
        answers = p.answers + (option -> (p.answers
          .getOrElse(option, Set.empty) + voter))
      )

  sealed trait PollMutationError extends Throwable

  object PollMutationError {

    case class NoSuchOption(
        pollId: String,
        option: VoteOption,
        validOptions: Set[VoteOption]
    ) extends PollMutationError

    case class ClosedPollMutation(pollId: String) extends PollMutationError

    case class RevotingOnFinalPoll(pollId: String, chosenOption: VoteOption)
        extends PollMutationError

  }
}
