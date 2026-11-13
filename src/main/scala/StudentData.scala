import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.time.{Instant, LocalDate, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.Locale

import org.bson.json.{JsonMode, JsonWriterSettings}
import org.mongodb.scala.Document
import org.mongodb.scala.bson.{BsonArray, BsonDateTime, BsonDouble, BsonInt32, BsonString}

import scala.io.Source
import scala.util.Using
import scala.util.control.NonFatal

object StudentData {
  private val exportDateFormat = DateTimeFormatter
    .ofPattern("uuuu-MM-dd'T'HH:mm:ssxxx", Locale.ROOT)
    .withZone(ZoneOffset.UTC)

  private val jsonSettings = JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build()

  def readCsv(path: Path): Vector[Document] = {
    val documents = Using.resource(Source.fromFile(path.toFile, UTF_8.name())) { source =>
      source.getLines().drop(1).zipWithIndex.collect {
        case (line, index) if line.trim.nonEmpty => parseStudent(line.trim, index + 2)
      }.toVector
    }

    require(documents.nonEmpty, "The CSV contains no students.")
    val ids = documents.map(_.toBsonDocument.getInt32("_id").getValue)
    require(ids.distinct.size == ids.size, "Student IDs must be unique.")
    documents
  }

  private def parseStudent(line: String, lineNumber: Int): Document = {
    try {
      val fields = line.split(",", -1)
      require(fields.length >= 6, "Expected a student ID, names, date, course array, and GPA.")

      val courseArray = fields.slice(4, fields.length - 1).mkString(",").trim
      require(courseArray.startsWith("[") && courseArray.endsWith("]"), "Expected a bracketed course array.")
      val courseContents = courseArray.substring(1, courseArray.length - 1).trim
      val courseIds = if (courseContents.isEmpty) Vector.empty[Int]
      else courseContents.split(",", -1).iterator.map(_.trim.toInt).toVector

      val birthDate = LocalDate.parse(fields(3).trim).atStartOfDay(ZoneOffset.UTC).toInstant
      val gpa = fields.last.trim.toDouble
      require(gpa >= 0.0 && gpa <= 4.0, "GPA must be between 0.0 and 4.0.")

      Document(
        "_id" -> BsonInt32(fields(0).trim.toInt),
        "firstName" -> BsonString(fields(1)),
        "lastName" -> BsonString(fields(2)),
        "dateOfBirth" -> BsonDateTime(birthDate.toEpochMilli),
        "courseIds" -> BsonArray.fromIterable(courseIds.map(BsonInt32(_))),
        "gpa" -> BsonDouble(gpa)
      )
    } catch {
      case NonFatal(error) =>
        throw new IllegalArgumentException(s"Invalid student on CSV line $lineNumber: ${error.getMessage}", error)
    }
  }

  def writeJsonLines(documents: Seq[Document], path: Path): Unit = {
    Using.resource(Files.newBufferedWriter(path, UTF_8)) { writer =>
      documents.foreach { student =>
        val date = Instant.ofEpochMilli(student.toBsonDocument.getDateTime("dateOfBirth").getValue)
        val exported = student + ("dateOfBirth" -> BsonString(exportDateFormat.format(date)))
        writer.write(exported.toJson(jsonSettings))
        writer.newLine()
      }
    }
  }
}
