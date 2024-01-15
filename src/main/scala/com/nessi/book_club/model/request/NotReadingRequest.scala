package com.nessi.book_club.model.request

import com.nessi.book_club.model.Voter

final case class NotReadingRequest(
    pollId: String,
    voter: Voter
)
