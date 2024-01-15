package com.nessi.book_club.model.request

import com.nessi.book_club.model.Poll.PollType
import com.nessi.book_club.model.{Poll, Voter}

final case class CreatePollRequest(
    pollId: String,
    `type`: PollType,
    options: Set[String]
) {
  def toPoll: Poll =
    Poll(
      id = pollId,
      `type` = `type`,
      answers = options.map(_ -> Set.empty[Voter]).toMap,
      Set.empty[Voter],
      closed = false
    )
}
