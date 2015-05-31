package actors

import akka.actor.{ActorSystem, Props}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.test.WithApplication
import scala.concurrent.duration.DurationInt

import ChunkConcatenatorProtocol.{Chunk, Test}
import ConcurrentUploaderProtocol._
import helper.AkkaHelper.TestEnvironment
import helper.ResumableHelper.{dummyChunk, chunkFirst => c1}

class ChunkConcatenatorSpec extends Specification with Mockito {
  implicit private val timeout: akka.util.Timeout = 1 second

  //////////////////////////////////////////////////////////////////////
  // Test for "receive" function
  //////////////////////////////////////////////////////////////////////

  "A ChunkConcatenator" should {
    "receives a Chunk protocol" >> {
      "and then returns a Result protocol with its status \"done\" when a chunk is uploaded" in {
        new TestEnvironment(ActorSystem("TestSystem-01")) {
          new WithApplication {
            val chunkNumber = c1("resumableChunkNumber").head.toInt
            val chunkSize = c1("resumableChunkSize").head.toInt
            val currentChunkSize = c1("resumableCurrentChunkSize").head.toInt
            val filename = c1("resumableFilename").head
            val identifier = c1("resumableIdentifier").head
            val totalSize = c1("resumableTotalSize").head.toInt
            val actorRef = system.actorOf(ChunkConcatenator.props(filename, totalSize, chunkSize))
            val chunk = Chunk(chunkNumber, chunkSize, currentChunkSize, dummyChunk, filename, identifier, totalSize)
            actorRef !(chunk, self)
            expectMsgPF() {
              case p: Progress => p.status must equalTo("done")
            }
          }
        }
      }
      "and then returns a Result protocol with its status is \"complete\" when all chunks are uploaded" in {
        new TestEnvironment(ActorSystem("TestSystem-02")) {
          new WithApplication {
            val chunkNumber = c1("resumableChunkNumber").head.toInt
            val chunkSize = c1("resumableChunkSize").head.toInt
            val currentChunkSize = c1("resumableCurrentChunkSize").head.toInt
            val filename = c1("resumableFilename").head
            val identifier = c1("resumableIdentifier").head
            val totalSize = c1("resumableTotalSize").head.toInt
            val actorRef = system.actorOf(ChunkConcatenatorSpec.props(filename, totalSize, chunkSize))
            val chunk = Chunk(chunkNumber, chunkSize, currentChunkSize, dummyChunk, filename, identifier, totalSize)
            actorRef !(chunk, self)
            expectMsgPF() {
              case p: Progress => p.status must equalTo("complete")
            }
          }
        }
      }
    }
    "receives a Test protocol" >> {
      "and then returns \"true\" when the chunk was already uploaded" in {
        new TestEnvironment(ActorSystem("TestSystem-03")) {
          new WithApplication {
            val chunkNumber = c1("resumableChunkNumber").head.toInt
            val chunkSize = c1("resumableChunkSize").head.toInt
            val currentChunkSize = c1("resumableCurrentChunkSize").head.toInt
            val filename = c1("resumableFilename").head
            val identifier = c1("resumableIdentifier").head
            val totalSize = c1("resumableTotalSize").head.toInt
            val actorRef = system.actorOf(ChunkConcatenator.props(filename, totalSize, chunkSize))
            val test = Test(chunkNumber)
            actorRef !(test, self)
            expectMsgPF() {
              case r: Boolean => r must equalTo(false)
            }
          }
        }
      }
      "and then returns \"false\" when the chunk is not uploaded yet" in {
        new TestEnvironment(ActorSystem("TestSystem-04")) {
          new WithApplication {

            //////////////////////////////
            // upload a chunk
            val chunkNumber = c1("resumableChunkNumber").head.toInt
            val chunkSize = c1("resumableChunkSize").head.toInt
            val currentChunkSize = c1("resumableCurrentChunkSize").head.toInt
            val filename = c1("resumableFilename").head
            val identifier = c1("resumableIdentifier").head
            val totalSize = c1("resumableTotalSize").head.toInt
            val actorRef = system.actorOf(ChunkConcatenator.props(filename, totalSize, chunkSize))
            val chunk = Chunk(chunkNumber, chunkSize, currentChunkSize, dummyChunk, filename, identifier, totalSize)
            actorRef !(chunk, self)
            expectMsgPF() {
              case p: Progress => p.status must equalTo("done")
            }

            //////////////////////////////
            // check existence for the chunk
            val test = Test(chunkNumber)
            actorRef !(test, self)
            expectMsgPF() {
              case r: Boolean => r must equalTo(true)
            }
          }
        }
      }
    }
  }
}

object ChunkConcatenatorSpec {
  class TestChunkConcatenator(fileName: String, totalSize: Int, chunkSize: Int) extends ChunkConcatenator(fileName, totalSize, chunkSize) {
    override val count = 1
  }
  def props(fileName: String, totalSize: Int, chunkSize: Int): Props =
    Props(new TestChunkConcatenator(fileName, totalSize, chunkSize))
}
