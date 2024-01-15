package com.nessi.book_club.model.json.decoders

import com.nessi.book_club.model.Poll
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.DecodingFailure
import com.nessi.book_club.model.Poll.PollType
import com.nessi.book_club.model.request.{CreatePollRequest, NotReadingRequest, VoteRequest}

object CirceDecoders:

  given Decoder[CreatePollRequest] = deriveDecoder

  given Decoder[NotReadingRequest] = deriveDecoder

  given Decoder[VoteRequest] = deriveDecoder

  given Decoder[Poll.PollType] = Decoder[String].flatMap { s =>
    Poll.PollType.fromString(s) match
      case None =>
        Decoder.failed(
          DecodingFailure(
            s"Not a valid PollType, required one of ${PollType.values
                .mkString("[", ", ", "]")}, got: $s",
            List.empty
          )
        )
      case Some(t) => Decoder.const(t)
  }
