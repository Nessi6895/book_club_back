package com.nessi.book_club.ydb.model

import tech.ydb.core.Status
import tech.ydb.table.result.ResultSetReader

case class YdbResult(resultSets: List[ResultSetReader], status: Status) {
  def resultSet: ResultSetReader = resultSets.head
}
