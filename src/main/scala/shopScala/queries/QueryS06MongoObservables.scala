package shopScala.queries

import java.util.concurrent.CountDownLatch

import org.mongodb.scala._
import org.mongodb.scala.model.Filters
import shopScala.util.Constants._
import shopScala.util.Util._
import shopScala.util._

object QueryS06MongoObservables extends App {

  object dao {

    val client: MongoClient = MongoClient()
    val db: MongoDatabase = client.getDatabase(SHOP_DB_NAME)
    val usersCollection: MongoCollection[Document] = db.getCollection(USERS_COLLECTION_NAME)
    val ordersCollection: MongoCollection[Document] = db.getCollection(ORDERS_COLLECTION_NAME)

    def findUserByName(name: String): Observable[Option[User]] = {
      usersCollection
        .find(Filters.eq("_id", name))
        .first()
        .map(doc => User(doc))
        .collect()
        .map(seq => seq.headOption)
    }

    def findOrdersByUsername(username: String): Observable[Seq[Order]] = {
      ordersCollection
        .find(Filters.eq("username", username))
        .map(doc => Order(doc))
        .collect()
    }
  }   // end dao


  def logIn(credentials: Credentials): Observable[String] = {
    dao.findUserByName(credentials.username)
      // the Exception thrown by checkUserLoggedIn is swallowed by the MongoObservable Implementation
      .map(optUser => checkUserLoggedIn(optUser, credentials))
      .map(user => {
        println("user -> user.name")
        user.name
      })
  }

  private def processOrdersOf(username: String): Observable[Result] = {
    println("processOrdersOf: " + username)
    dao.findOrdersByUsername(username)
      .map(orders => new Result(username, orders))
  }

  def eCommerceStatistics(credentials: Credentials): Unit = {

    println("--- Calculating eCommerce statistings for user \"" + credentials.username + "\" ...")

    val latch: CountDownLatch = new CountDownLatch(1)

    try {
      logIn(credentials)
        .flatMap(username => processOrdersOf(username))
        .subscribe(new Observer[Result] {

          override def onNext(result: Result): Unit = {
            println("=== onNext")
            result.display()
          }

          override def onError(t: Throwable): Unit = {
            println("=== onError")
            t.printStackTrace()
            latch.countDown()
          }

          override def onComplete(): Unit = {
            println("=== onComplete")
            latch.countDown()
          }
        })
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        latch.countDown()
    }

    latch.await()
  }

  eCommerceStatistics(Credentials(LISA, "password"))
  Thread sleep 2000L
  eCommerceStatistics(Credentials(LISA, "bad password"))
  Thread sleep 2000L
  eCommerceStatistics(Credentials(LISA.toUpperCase, "password"))
}
