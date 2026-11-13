object StudentRecords {
  def main(args: Array[String]): Unit = {
    args.toList match {
      case "load" :: Nil  => MongoImport.run()
      case "query" :: Nil => MongoQueries.run()
      case _ =>
        Console.err.println("Usage: java -jar student-records.jar <load|query>")
        sys.exit(2)
    }
  }
}
