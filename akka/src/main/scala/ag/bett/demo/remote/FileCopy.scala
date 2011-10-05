/**
 * Copyright 2011 Franz Bettag <franz@bett.ag>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package ag.bett.demo.remote

import akka.actor._
import net.liftweb.http._
import net.liftweb.actor._
import net.liftweb.util._
import net.liftweb.common._

import scala.util.matching.Regex
import scalax.file.{Path, PathMatcher}
import scalax.file.PathMatcher._

import java.util.Date
import org.joda.time._

import scala.xml._

import java.lang.reflect._
import java.io._


case class LADemoFileCopyRequestList(actor: CometActor)
case class LADemoFileCopyRequestListRemote
case class LADemoFileCopyList(files: Map[String, Int])

case class LADemoFileCopyAbortRequest(actor: CometActor)
case class LADemoFileCopyAbortRequestRemote(actorRef: Long)

sealed trait LADemoFileStat

sealed trait LADemoFileCopyInfo extends LADemoFileStat
case class LADemoFileCopyRequest(actor: CometActor, file: String) extends LADemoFileCopyInfo
case class LADemoFileCopyRequestRemote(actorRef: Long, file: String) extends LADemoFileCopyInfo

case class LADemoFileCopyQueue(waiting: Int) extends LADemoFileCopyInfo
case class LADemoFileCopyStatus(file: String, percent: Int, speed: String, remaining: String) extends LADemoFileCopyInfo
case class LADemoFileCopyDone(success: Boolean, when: DateTime) extends LADemoFileCopyInfo

sealed trait LADemoFileCopyInternal extends LADemoFileStat
case class LADemoFileCopyInternalWait extends LADemoFileCopyInternal
case class LADemoFileCopyInternalStart(source: Path, target: Path, actor: CometActor) extends LADemoFileCopyInternal
case class LADemoFileCopyInternalStartRemote(source: Path, target: Path, actorRef: Long) extends LADemoFileCopyInternal

/* For Akka Remote Actor */
trait LADemoFileCopyRemoteMethods {	
    println("LADemoFileCopyRemoteMethods starting...")

    val copyMaxProcs = 5

    val copyPath = Path("/Volumes/Data/Downloads") //Path("/tmp/i.didnt.configure.jack")
    lazy val copyFileList: LADemoFileCopyList = {
        try {
            // Only list files bigger than 200MB
            var myList: Map[String, Int] = Map()
            if (copyPath.exists && copyPath.isDirectory && copyPath.canWrite)
            copyPath.children().toList
            .filter(_.size.getOrElse(0L).toLong > 200*1024*1024)
            .foreach(f => myList = myList ++ Map(f.name.toString -> (f.size.getOrElse(0L).toLong / 1024 / 1024).toInt))

            (copyPath / "targets").createDirectory(failIfExists=false)
            LADemoFileCopyList(myList)
        } catch { case _ => LADemoFileCopyList(Map())}
    }

    var copyQueue: Map[Long, LADemoFileStat] = Map()

    def copyQueueWithInfo(req: LADemoFileCopyRequestRemote): LADemoFileStat = {
        copyQueue.get(req.actorRef) match {
            case Some(found: LADemoFileStat) =>
            case _ => copyQueue = copyQueue ++ Map(req.actorRef -> req)
        }

        var countQueue = 0
        copyQueue.map(q =>
            if (q._1.equals(req.actorRef)) {
                q._2 match {
                    // We are done! Dequeue
                    case cd: LADemoFileCopyDone =>
                        copyDequeue(req.actorRef)
                        return cd

                    // We have an active Copy running                    
                    case cs: LADemoFileCopyStatus =>
                        return cs

                    // There are people in the queue infront of us
                    case _ if (countQueue >= copyMaxProcs) =>
                        copyQueue = copyQueue ++ Map(req.actorRef -> LADemoFileCopyQueue(countQueue-copyMaxProcs))
                        return LADemoFileCopyInternalWait()

                    // There is nobody infront of us!
                    case _ if (countQueue < copyMaxProcs) =>
                        val target = copyPath / "targets" / scala.util.Random.nextLong.toString
                        return LADemoFileCopyInternalStartRemote(copyPath / req.file, target, req.actorRef)

                    case _ =>
                }
            }
            else countQueue += 1
        )

        return LADemoFileCopyInternalWait()
    }

    def copyDequeue(actorRef: Long) {
        copyQueue = copyQueue - actorRef
    }

    def copyFileStart(req: LADemoFileCopyInternalStartRemote) {
        val args = scala.Array("rsync", "--progress", req.source.path.toString, req.target.path.toString)
        println("File Copy: " + args.mkString(" "))
        val process = Runtime.getRuntime.exec(args)
        val resultBuffer = new BufferedReader(new InputStreamReader(process.getInputStream))
        var line: String = null

        // Parse the output from rsync line-based and send it to the actor
        do {
            line = resultBuffer.readLine
            if (line != null) {
                val RsyncStat = """.*\d+\s+(\d+)%\s+(\d+\.\d+../s)\s+(\d+:\d+:\d+).*""".r
                line match {
                    case RsyncStat(percentS, speed, remaining) =>
                    val percent = try { percentS.toInt } catch { case _ => 0 }
                    copyQueue = copyQueue ++ Map(req.actorRef -> LADemoFileCopyStatus(req.source.name.toString, percent, speed, remaining))
                    case _ => println(line)
                }
            }
        } while (line != null)

        process.waitFor
        resultBuffer.close

        copyQueue = copyQueue ++ Map(req.actorRef -> LADemoFileCopyDone(process.exitValue == 0, new DateTime))
    }


    println("LADemoFileCopyRemoteMethods started successfully")
}
