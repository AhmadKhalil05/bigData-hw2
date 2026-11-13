<img width="1594" height="987" alt="image" src="https://github.com/user-attachments/assets/15d9c436-2b1a-4a90-a32c-2949cd289582" />



# Homework 2 — Student Records with MongoDB and Spark SQL

The Scala loader reads the original 100 student records, stores them in `university.students`, and exports them to `students.json`. Spark SQL reads that export with an explicit schema and writes the per-course results to Parquet. This is Option B from the assignment.

## Run the project

Run the commands from the extracted `BigData_HW2_Scala` directory. Start your local MongoDB server at `127.0.0.1:27017`. The bundled MongoDB program runs on Java 11 or newer. Run the Spark phase with your working Scala Spark Shell. The supplied results were generated with Spark 4.1.2 and Scala 2.13.17.

```bash
java -jar student-records.jar load
java -jar student-records.jar query
```

For the demonstration, enter `103` for course ID, `3.5` for minimum GPA, and `103` for the optional course. The expected match counts are **36**, **49**, and **9**. Press Enter at the optional prompt to skip the combined query.

The load command reads and validates the CSV before changing MongoDB. It inserts the records with a single `insertMany` call, creates indexes on `courseIds` and `gpa`, then publishes the prepared collection as `university.students`. Repeating the command replaces the collection and refreshes the JSON export. The server address can be set with `MONGODB_URI`.

The query command prints the winning execution plan and reports whether it uses `courseIds_1`. The recorded course 103 query used `IXSCAN` on this multikey index.

Start Spark Shell in the same project directory.

```bash
spark-shell --master "local[*]"
```

At the `scala>` prompt, load the analysis.

```scala
:load spark/spark_sql.scala
```

The script prints the schema, SQL statements, and their results. It registers the `students` and `enrollments` temporary views, writes `course_stats.parquet`, and reads it back to verify the saved rows. The final output is `Saved courses: 5` and `Results match: true`.

## Source files

| Path | Role |
| --- | --- |
| `src/main/scala/StudentData.scala` | Parse the unquoted course array, build typed BSON documents, and write JSON Lines. |
| `src/main/scala/MongoImport.scala` | Bulk load the students and create the indexes. |
| `src/main/scala/MongoQueries.scala` | Validate input, run the three interactive lookups, and inspect the course query plan. |
| `src/main/scala/StudentDatabase.scala` | Share the Scala driver connection and wait for database operations. |
| `src/main/scala/StudentRecords.scala` | Start the selected `load` or `query` command. |
| `spark/spark_sql.scala` | Run the nine analytical statements and verify the Parquet round trip. |
| `hw2-data.csv`, `students.json`, `course_stats.parquet/` | Source records, the exported records, and the saved course summary. |
| `results/` | Recorded output from the MongoDB and Spark runs. |

To rebuild the runnable JAR from the Scala sources, run `sbt assembly`. The resulting file is `target/scala-2.13/student-records.jar`. The build pins Scala 2.13.17 and MongoDB Scala Driver 5.5.1.

## Results

| Operation | Result |
| --- | ---: |
| Records in MongoDB and in the JSON export | 100 |
| Course 103 matches | 36 |
| GPA strictly greater than 3.5 | 49 |
| Course 103 and GPA strictly greater than 3.5 | 9 |
| Student-course rows after `explode` | 218 |
| Students taking one, two, and three courses | 8, 66, 26 |
| Rows in the top three GPA ranks per course | 94 |
| Pairs with the same first and last names | 7 |
| Students above the average of each enrolled course | 49 |
| Saved course-summary rows | 5 |

MongoDB stores the birth date as a BSON Date and the course IDs as an integer array. The JSON export writes the same birth date as an ISO 8601 string. The Spark schema reads it as a timestamp and the course IDs as `ARRAY<INT>`.

MongoDB serves the indexed lookups. Spark groups and joins the same dataset for analysis. The course 103 and GPA lookups return the same counts in both systems.

The ranking uses `DENSE_RANK` to include every student tied within the first three GPA ranks in each course. A student can appear in multiple courses, so the 94 results are enrollment rows. For the comparison with all enrolled-course averages, the query compares each student with the highest average among their courses before rounding for display.
