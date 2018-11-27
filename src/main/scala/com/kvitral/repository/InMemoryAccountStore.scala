package com.kvitral.repository

import cats.Monad
import cats.data.EitherT
import cats.effect.concurrent.Ref
import cats.syntax.all._
import com.kvitral.model.errors.{
  AccountNotFound,
  AccountServiceErrors,
  AccountsAreTheSame,
  InsufficientBalance
}
import com.kvitral.model.{Account, Transaction}
import com.kvitral.operations.{AccountOperations, LoggingOperations}

import scala.language.higherKinds

class InMemoryAccountStore[F[_]: Monad](
    accountState: Ref[F, Map[Long, Account]],
    logger: LoggingOperations[F])
    extends AccountOperations[F] {

  override def getAccount(i: Long): F[Option[Account]] =
    for {
      account <- accountState.get
    } yield account.get(i)

  override def changeBalance(transaction: Transaction): F[Either[AccountServiceErrors, Unit]] = {
    val transactionResult: EitherT[F, AccountServiceErrors, Unit] = for {
      _ <- validateAccountsIdsEquality(transaction)
      trResult <- EitherT(accountState.modify(state => performTransaction(transaction, state)))
      _ <- EitherT.liftF[F, AccountServiceErrors, Unit](
        logger.info(s"result of transaction is ${trResult.toString}"))
    } yield trResult

    transactionResult.value
  }

  private def performTransaction(
      t: Transaction,
      state: Map[Long, Account]): (Map[Long, Account], Either[AccountServiceErrors, Unit]) = {

    val finishedTransaction = for {
      accountFrom <- Either
        .fromOption(state.get(t.from), AccountNotFound)
        .filterOrElse(_.balance >= t.amount, InsufficientBalance)

      accountTo <- Either.fromOption(state.get(t.to), AccountNotFound)
    } yield {
      state
        .updated(accountFrom.id, accountFrom.copy(balance = accountFrom.balance - t.amount))
        .updated(accountTo.id, accountTo.copy(balance = accountTo.balance + t.amount))
    }

    finishedTransaction match {
      case Left(error) => (state, Left(error))
      case Right(updatedState) => (updatedState, Right(()))
    }
  }

  private def validateAccountsIdsEquality(
      transaction: Transaction): EitherT[F, AccountsAreTheSame.type, Unit] =
    EitherT.cond(transaction.from != transaction.to, (), AccountsAreTheSame)

}

object InMemoryAccountStore {
  def apply[F[_]: Monad](
      accountState: Ref[F, Map[Long, Account]],
      logger: LoggingOperations[F]): InMemoryAccountStore[F] =
    new InMemoryAccountStore(accountState, logger)
}
