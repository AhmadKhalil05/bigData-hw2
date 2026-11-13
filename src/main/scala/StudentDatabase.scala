import org.mongodb.scala.{MongoClient, MongoDatabase}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object StudentDatabase {
  val databaseName = "university"
  val collectionName = "students"

  def withDatabase[A](run: MongoDatabase => A): A = {
    val uri = sys.env.getOrElse("MONGODB_URI", "mongodb://127.0.0.1:27017")
    val client = MongoClient(uri)
    try run(client.getDatabase(databaseName))
    finally client.close()
  }

  def await[A](operation: Future[A]): A = Await.result(operation, 45.seconds)
}
