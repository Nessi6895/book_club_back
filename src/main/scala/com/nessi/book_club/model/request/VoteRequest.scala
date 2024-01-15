package com.nessi.book_club.model.request

import com.nessi.book_club.model.{Voter, VoteOption}

final case class VoteRequest(
    pollId: String,
    voter: Voter,
    option: VoteOption
)
