package com.nessi.book_club.ydb.model

import tech.ydb.table.Session
import tech.ydb.table.transaction.TxControl
import tech.ydb.table.transaction.Transaction.{Mode => TransactionMode}

sealed trait Transaction {

  def control: TxControl[_ <: TxControl[_ <: AnyRef]]

  def markCommitted: Transaction

  def isCommitted: Boolean

  def withAutoCommit: Transaction

  def autoCommit: Boolean

  def withTxId(id: String): Transaction

  def txId: Option[String]

  def session: Session

}

object Transaction {

  final case class TransactionWithId(
      id: String,
      session: Session,
      isCommitted: Boolean,
      autoCommit: Boolean)
    extends Transaction {

    override def withTxId(id: String): Transaction = {
      require(id.isEmpty || id == this.id, "Transaction id changed")
      this
    }

    override def txId: Option[String] = Some(id)

    override def control: TxControl[_ <: TxControl[_ <: AnyRef]] =
      TxControl
        .id(id)
        .setCommitTx(autoCommit)
        .asInstanceOf[TxControl[_ <: TxControl[_ <: AnyRef]]]

    override def markCommitted: Transaction = copy(isCommitted = true)

    override def withAutoCommit: Transaction = copy(autoCommit = true)
  }

  final case class TransactionWithMode(
      mode: TransactionMode,
      session: Session,
      isCommitted: Boolean,
      autoCommit: Boolean)
    extends Transaction {

    override def control: TxControl[_ <: TxControl[_ <: AnyRef]] = {
      val tx = mode match {
        case TransactionMode.SERIALIZABLE_READ_WRITE => TxControl.serializableRw()
        case TransactionMode.ONLINE_READ_ONLY => TxControl.onlineRo()
        case TransactionMode.STALE_READ_ONLY => TxControl.staleRo()
        case TransactionMode.SNAPSHOT_READ_ONLY => TxControl.snapshotRo()
      }
      tx.setCommitTx(autoCommit)
        .asInstanceOf[TxControl[_ <: TxControl[_ <: AnyRef]]]
    }

    override def markCommitted: Transaction = copy(isCommitted = true)

    override def withAutoCommit: Transaction = copy(autoCommit = true)

    override def withTxId(id: String): Transaction =
      TransactionWithId(id, session, isCommitted, autoCommit)

    override def txId: Option[String] =
      None
  }

}
