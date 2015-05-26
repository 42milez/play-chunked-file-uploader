package models

import org.specs2.mutable.Specification
import play.api.{Application, GlobalSettings}
import play.api.test.{FakeApplication, WithApplication}
import play.api.test.Helpers._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class FileSpec extends Specification {

  import dao.FilesDAO
  import models.{File => FileM}

  def fakeApp = FakeApplication(
    additionalConfiguration = inMemoryDatabase(),
    withGlobal = Some(new GlobalSettings {
      override def onStart(app: Application) {
        def filesDao = new FilesDAO
        val storedFiles = Await.result(filesDao.count(), Duration.Inf)
        if (storedFiles == 0) {
          Await.result(filesDao.insert(new FileM(None, "test1.txt")), Duration.Inf)
          Await.result(filesDao.insert(new FileM(None, "test2.txt")), Duration.Inf)
          Await.result(filesDao.insert(new FileM(None, "test3.txt")), Duration.Inf)
        }
      }
    })
  )

  "File model" should {

    def filesDao = new FilesDAO

    "returns tne number of files stored" in new WithApplication(fakeApp) {
      val count = Await.result(filesDao.count(), Duration.Inf)
      count must equalTo(3)
    }

    "be retrieved by id" in new WithApplication(fakeApp) {
      val file = Await.result(filesDao.findById(1), Duration.Inf)
      file.name must equalTo("test1.txt")
    }

    "be retrieved by name" in new WithApplication(fakeApp) {
      val file = Await.result(filesDao.findByName("test1.txt"), Duration.Inf)
      file.id must equalTo(Some(1))
    }

    "be listed" in new WithApplication(fakeApp) {
      val files = Await.result(filesDao.list(), Duration.Inf)
      files.total must equalTo(3)
      files.items must have length 3
    }
  }
}
