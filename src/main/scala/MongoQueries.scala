import java.io.EOFException
import java.util.Locale

import com.mongodb.ExplainVerbosity
import org.bson.json.JsonWriterSettings
import org.mongodb.scala._
import org.mongodb.scala.bson.{BsonString, BsonValue}
import org.mongodb.scala.model.{Filters, Projections, Sorts}

import scala.annotation.tailrec
import scala.io.StdIn
import scala.jdk.CollectionConverters._

object MongoQueries {
  private val projection = Projections.include("_id", "firstName", "lastName", "gpa")
  private val formattedJson = JsonWriterSettings.builder().indent(true).build()

  def run(): Unit = {
    import StudentDatabase.await

    try StudentDatabase.withDatabase { database =>
      val students = database.getCollection[Document](StudentDatabase.collectionName)

      val courseId = readCourseId()
      val courseQuery = students.find(Filters.equal("courseIds", courseId))
        .projection(projection)
        .sort(Sorts.ascending("lastName"))
      printMatches(await(courseQuery.toFuture()), "Matches")
      showCoursePlan(await(courseQuery.explain[Document](ExplainVerbosity.EXECUTION_STATS).toFuture()))

      val minimumGpa = readMinimumGpa()
      val gpaFilter = Filters.gt("gpa", minimumGpa)
      val byGpa = Sorts.descending("gpa")
      val gpaMatches = students.find(gpaFilter).projection(projection).sort(byGpa)
      printMatches(await(gpaMatches.toFuture()), "GPA matches")

      readOptionalCourse().foreach { course =>
        val both = Filters.and(Filters.equal("courseIds", course), gpaFilter)
        val combinedMatches = students.find(both).projection(projection).sort(byGpa)
        printMatches(await(combinedMatches.toFuture()), "Combined matches")
      }
    } catch {
      case _: EOFException => println("Input ended.")
    }
  }

  private def readInput(prompt: String): String =
    Option(StdIn.readLine(prompt)).getOrElse(throw new EOFException()).trim

  @tailrec
  private def readCourseId(): Int = {
    readInput("Enter course ID: ").toIntOption match {
      case Some(courseId) => courseId
      case None =>
        println("Please enter an integer.")
        readCourseId()
    }
  }

  @tailrec
  private def readMinimumGpa(): Double = {
    readInput("\nEnter minimum GPA (0.0-4.0): ").toDoubleOption match {
      case Some(gpa) if gpa >= 0.0 && gpa <= 4.0 => gpa
      case _ =>
        println("Please enter a number between 0.0 and 4.0.")
        readMinimumGpa()
    }
  }

  @tailrec
  private def readOptionalCourse(): Option[Int] = {
    val input = readInput("\nOptional course ID (Enter to skip): ")
    if (input.isEmpty) None
    else input.toIntOption match {
      case valid @ Some(_) => valid
      case None =>
        println("Please enter an integer or press Enter.")
        readOptionalCourse()
    }
  }

  private def printMatches(matches: Seq[Document], label: String): Unit = {
    matches.foreach { result =>
      val student = result.toBsonDocument
      val id = student.getInt32("_id").getValue
      val firstName = student.getString("firstName").getValue
      val lastName = student.getString("lastName").getValue
      val gpa = String.format(Locale.ROOT, "%.2f", Double.box(student.getDouble("gpa").getValue))
      println(s"$id | $firstName $lastName | GPA: $gpa")
    }
    println(s"$label: ${matches.size}")
  }

  private def showCoursePlan(explanation: Document): Unit = {
    val details = explanation.toBsonDocument
    val winningPlan = details.getDocument("queryPlanner").getDocument("winningPlan")
    val plan = winningPlan.getDocument("queryPlan", winningPlan)
    val stats = details.getDocument("executionStats")

    println("\nWinning plan:")
    println(plan.toJson(formattedJson))
    println(s"Course index used: ${usesCourseIndex(plan)}")
    println(s"Returned: ${stats.getNumber("nReturned").longValue()}")
    println(s"Documents examined: ${stats.getNumber("totalDocsExamined").longValue()}")
    println(s"Index keys examined: ${stats.getNumber("totalKeysExamined").longValue()}")
  }

  private def usesCourseIndex(value: BsonValue): Boolean = {
    if (value.isDocument) {
      val plan = value.asDocument()
      val stage = plan.getString("stage", BsonString("")).getValue
      val indexName = plan.getString("indexName", BsonString("")).getValue
      (stage.endsWith("IXSCAN") && indexName == "courseIds_1") ||
        plan.values().asScala.exists(usesCourseIndex)
    } else if (value.isArray) {
      value.asArray().getValues.asScala.exists(usesCourseIndex)
    } else false
  }
}
