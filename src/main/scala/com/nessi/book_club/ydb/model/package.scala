package com.nessi.book_club.ydb

import zio.ZIO

package object model {
  
  type TxZIO[R, E, A] = ZIO[TxEnv[R], E, A]
  type TxTask[A] = TxZIO[Any, Throwable, A] 
  
}
