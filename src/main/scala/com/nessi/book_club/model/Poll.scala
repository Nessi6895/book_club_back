package com.nessi.book_club.model

import Poll.*

final case class Poll(
    id: String,
    `type`: PollType,
    answers: Map[VoteOption, Set[Voter]],
    notReading: Set[Voter],
    closed: Boolean
)

object Poll:

  enum PollType:
    case Initial, Final

  object PollType:
    def fromString(s: String): Option[PollType] =
      PollType.values.find(_.toString == s)
