/*
 * Copyright (c) 2013 Functional Streams for Scala
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package fs2
package io
package file

import scala.concurrent.duration._

import cats.effect.IO

import java.nio.file.{Files => JFiles, _}

class WatcherSuite extends Fs2Suite with BaseFileSuite {
  group("supports watching a file") {
    test("for modifications") {
      Stream
        .resource(tempFile)
        .flatMap { f =>
          Files[IO]
            .watch(f, modifiers = modifiers)
            .takeWhile(
              {
                case Watcher.Event.Modified(_, _) => false
                case _                            => true
              },
              true
            )
            .concurrently(smallDelay ++ Stream.eval(modify(f)))
        }
        .compile
        .drain
    }
    test("for deletions") {
      Stream
        .resource(tempFile)
        .flatMap { f =>
          Files[IO]
            .watch(f, modifiers = modifiers)
            .takeWhile(
              {
                case Watcher.Event.Deleted(_, _) => false
                case _                           => true
              },
              true
            )
            .concurrently(smallDelay ++ Stream.eval(IO(JFiles.delete(f))))
        }
        .compile
        .drain
    }
  }

  group("supports watching a directory") {
    test("static recursive watching") {
      Stream
        .resource(tempDirectory)
        .flatMap { dir =>
          val a = dir.resolve("a")
          val b = a.resolve("b")
          Stream.eval(IO(JFiles.createDirectory(a)) >> IO(JFiles.write(b, Array[Byte]()))) ++
            Files[IO]
              .watch(dir, modifiers = modifiers)
              .takeWhile {
                case Watcher.Event.Modified(_, _) => false
                case _                            => true
              }
              .concurrently(smallDelay ++ Stream.eval(modify(b)))
        }
        .compile
        .drain
    }
    test("dynamic recursive watching") {
      Stream
        .resource(tempDirectory)
        .flatMap { dir =>
          val a = dir.resolve("a")
          val b = a.resolve("b")
          Files[IO]
            .watch(dir, modifiers = modifiers)
            .takeWhile {
              case Watcher.Event.Created(_, _) => false
              case _                           => true
            }
            .concurrently(
              smallDelay ++ Stream
                .eval(IO(JFiles.createDirectory(a)) >> IO(JFiles.write(b, Array[Byte]())))
            )
        }
        .compile
        .drain
    }
  }

  private def smallDelay: Stream[IO, Nothing] =
    Stream.sleep_[IO](1000.millis)

  // Tries to load the Oracle specific SensitivityWatchEventModifier to increase sensitivity of polling
  private val modifiers: Seq[WatchEvent.Modifier] = {
    try {
      val c = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier")
      Seq(c.getField("HIGH").get(c).asInstanceOf[WatchEvent.Modifier])
    } catch {
      case _: Throwable => Nil
    }
  }
}
