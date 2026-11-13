import java.nio.file.{AtomicMoveNotSupportedException, Files, Paths, StandardCopyOption}
import java.util.UUID

import com.mongodb.MongoNamespace
import com.mongodb.client.model.RenameCollectionOptions
import org.mongodb.scala._
import org.mongodb.scala.model.Indexes

import scala.util.control.NonFatal

object MongoImport {
  def run(): Unit = {
    val documents = StudentData.readCsv(Paths.get("hw2-data.csv"))
    println(s"Parsed students: ${documents.size}")

    val exportPath = Paths.get("students.json").toAbsolutePath
    val pendingExport = Files.createTempFile(exportPath.getParent, "students-", ".json.tmp")

    try {
      StudentData.writeJsonLines(documents, pendingExport)
      loadCollection(documents)

      try Files.move(pendingExport, exportPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      catch {
        case _: AtomicMoveNotSupportedException =>
          Files.move(pendingExport, exportPath, StandardCopyOption.REPLACE_EXISTING)
      }

      println(s"Exported students: ${documents.size}")
    } finally Files.deleteIfExists(pendingExport)
  }

  private def loadCollection(documents: Vector[Document]): Unit = {
    import StudentDatabase.await

    StudentDatabase.withDatabase { database =>
      await(database.runCommand[Document](Document("ping" -> 1)).toFuture())
      val stagingName = "students_load_" + UUID.randomUUID().toString.replace("-", "")
      val staging = database.getCollection[Document](stagingName)

      try {
        val insertion = await(staging.insertMany(documents).toFuture())
        val inserted = insertion.getInsertedIds.size()
        val stored = await(staging.countDocuments().toFuture())
        require(inserted == documents.size && stored == documents.size, "MongoDB did not store the complete input.")

        await(staging.createIndex(Indexes.ascending("courseIds")).toFuture())
        await(staging.createIndex(Indexes.ascending("gpa")).toFuture())
        val indexes = await(staging.listIndexes[Document]().toFuture())

        val destination = new MongoNamespace(StudentDatabase.databaseName, StudentDatabase.collectionName)
        val replaceExisting = new RenameCollectionOptions().dropTarget(true)
        await(staging.renameCollection(destination, replaceExisting).toFuture())

        println(s"Database: ${StudentDatabase.databaseName}")
        println(s"Collection: ${StudentDatabase.collectionName}")
        println(s"Inserted students: $inserted")
        println(s"Students in MongoDB: $stored")
        println("Indexes:")
        indexes.foreach { index =>
          val bson = index.toBsonDocument
          println(s"${bson.getString("name").getValue} ${bson.getDocument("key").toJson()}")
        }
      } catch {
        case NonFatal(error) =>
          try await(staging.drop().toFuture())
          catch { case NonFatal(cleanupError) => error.addSuppressed(cleanupError) }
          throw error
      }
    }
  }
}
