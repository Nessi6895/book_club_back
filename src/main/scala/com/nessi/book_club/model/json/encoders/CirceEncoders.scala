package com.nessi.book_club.model.json.encoders

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Json
import com.nessi.book_club.model.Poll
import com.nessi.book_club.services.PollsManager.PollsManagerError
import com.nessi.book_club.utils.JsonZIOReader.ParsingError
import com.nessi.book_club.utils.JsonZIOReader.ParsingError.*
import com.nessi.book_club.services.PollsManager.PollsManagerError.{
  PollNotExist,
  PollIsClosed,
  RevotingOnFinalPoll,
  NoSuchOption,
  UnknownError
}

object CirceEncoders {
  given Encoder[Poll] = deriveEncoder[Poll]

  given Encoder[Poll.PollType] = Encoder[String].contramap(_.toString())

  given Encoder[PollsManagerError] = Encoder.instance[PollsManagerError] {
    case PollNotExist(pollId) =>
      Json.obj(
        "errorType" -> Json.fromString("PollNotExist"),
        "message" -> Json.fromString(s"Poll with id `$pollId` does not exist"),
        "pollId" -> Json.fromString(pollId)
      )
    case PollIsClosed(pollId) =>
      Json.obj(
        "errorType" -> Json.fromString("PollIsClosed"),
        "message" -> Json.fromString(s"Poll with id `$pollId` is closed!"),
        "pollId" -> Json.fromString(pollId)
      )
    case RevotingOnFinalPoll(pollId, chosenOption) =>
      Json.obj(
        "errorType" -> Json.fromString("RevotingOnFinalPoll"),
        "message" -> Json.fromString(s"Cannot revote on final poll"),
        "pollId" -> Json.fromString(pollId),
        "chosenOption" -> Json.fromString(chosenOption)
      )
    case NoSuchOption(pollId, option, validOptions) =>
      Json.obj(
        "errorType" -> Json.fromString("NoSuchOption"),
        "message" -> Json.fromString(s"Option does not exists"),
        "pollId" -> Json.fromString(pollId),
        "invalidOption" -> Json.fromString(option),
        "validOptions" -> Json.arr(validOptions.map(Json.fromString).toSeq: _*)
      )
    case UnknownError(inner) =>
      Json.obj(
        "errorType" -> Json.fromString("Unknown"),
        "message" -> Json.fromString(
          s"Unknown error occured, cause: ${inner.getMessage}"
        )
      )
  }
  given Encoder[ParsingError] = Encoder.instance[ParsingError] {
    case IncorrectBody(e) =>
      Json.obj(
        "errorType" -> Json.fromString("IncorrectBody"),
        "message" -> Json.fromString(s"Body of request is not correct, expected json! Cause: $e")
      )
    case NotAJson(body) => 
      Json.obj(
        "errorType" -> Json.fromString("NotAJson"),
        "message" -> Json.fromString(s"Body of request is not Valid json! Body: $body")
      )
    case CannotDecode(json) => 
      Json.obj(
        "errorType" -> Json.fromString("CannotDecode"),
        "message" -> Json.fromString(s"Cannot decode json! Json:\n${json.noSpaces}")
      )
  }
  
}
