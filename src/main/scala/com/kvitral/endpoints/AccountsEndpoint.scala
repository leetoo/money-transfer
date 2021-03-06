package com.kvitral.endpoints

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import cats.Monad
import cats.syntax.functor._
import cats.syntax.applicative._
import com.kvitral.model.{ErrorMessage, SuccessMessage, Transaction}
import com.kvitral.services.AccountsService
import com.kvitral.transformers.EffectToRoute
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.language.higherKinds

class AccountsEndpoint[F[_]: Monad](accountService: AccountsService[F])(
    implicit effectToRoute: EffectToRoute[F]) {

  val accountsRoute: F[Route] = path("accounts") {
    get {
      parameters('id.as[Long]) { id =>
        val res = accountService
          .getAccount(id)
          .map(_.left.map(err => ErrorMessage(s"Couldn`t find account with id $id", err)))
        effectToRoute.toRoute(res)
      }
    } ~ post {
      entity(as[Transaction]) { tr =>
        val res = accountService
          .changeBalance(tr)
          .map(
            _.left
              .map(err => ErrorMessage("something went wrong", err))
              .map(_ => SuccessMessage("OK")))

        effectToRoute.toRoute(res)
      }
    }
  }.pure[F]

}

object AccountsEndpoint {
  def apply[F[_]: Monad: EffectToRoute](accountService: AccountsService[F]): AccountsEndpoint[F] =
    new AccountsEndpoint(accountService)
}
