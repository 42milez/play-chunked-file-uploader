package dao

import scala.concurrent.Future
import play.api.Play
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfig}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.H2Driver.api._
import slick.driver.JdbcProfile
import models.{File => FileM}

trait FilesComponent { self: HasDatabaseConfig[JdbcProfile] =>
  import driver.api._
  class Files(tag: Tag) extends Table[FileM](tag, "FILE") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def name = column[String]("NAME")
    def * = (id.?, name) <> (FileM.tupled, FileM.unapply)
  }
}

class FilesDAO extends FilesComponent with HasDatabaseConfig[JdbcProfile] {
  protected val dbConfig = DatabaseConfigProvider.get[JdbcProfile](Play.current)
  private val files = TableQuery[Files]

  def count(): Future[Int] =
    db.run(files.length.result)

  def insert(file: FileM): Future[Unit] =
    db.run(files += file).map(_ => ())

  //  def list(page: Int = 0, pageSize: Int = 10): Future[Page[File]] = {
  //    val offset = pageSize * page
  //    val q = (for {
  //      file <- files
  //    } yield (file.id, file.name)).drop(offset).take(pageSize)
  //    for {
  //      totalRows <- count()
  //      list = q.result.map (rows => rows.collect { case (id: Long, name: String) => File(Some(id), name) })
  //      result <- db.run(list)
  //    } yield Page(result, page, offset, totalRows)
  //  }
}
