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

package ag.bett.demo.comet

import ag.bett.demo.remote._
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


/* For Local Actors */
trait LADemoFileCopyMethods {	
    println("LADemoFileCopyMethods starting...")

    val copyMaxProcs = 5

    val copyPath = Path(Props.get("filecopy.path") openOr "/tmp/i.didnt.configure.jack")
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

    var copyQueue: Map[CometActor, Path] = Map()

    def copyQueueWithInfo(req: LADemoFileCopyRequest): LADemoFileCopyInternal = {
        copyQueue = copyQueue ++ Map(req.actor -> Path(req.file))

        var countQueue = 0
        copyQueue.map(q =>
            if (q._1.equals(req.actor)) {
                // There are people in the queue infront of us
                if (countQueue >= copyMaxProcs) {
                    req.actor ! LADemoFileCopyQueue(countQueue-copyMaxProcs)
                    return LADemoFileCopyInternalWait()
                }
                // There is nobody infront of us!
                if (countQueue < copyMaxProcs) {
                    val target = copyPath / "targets" / scala.util.Random.nextLong.toString
                    return LADemoFileCopyInternalStart(copyPath / req.file, target, req.actor)
                }
            }
            else countQueue += 1
        )

        return LADemoFileCopyInternalWait()
    }

    def copyDequeue(actor: CometActor) {
        copyQueue = copyQueue - actor
    }

    def copyFileStart(req: LADemoFileCopyInternalStart) {
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
                    req.actor ! LADemoFileCopyStatus(req.source.name, percent, speed, remaining)
                    case _ => println(line)
                }
            }
        } while (line != null)

        process.waitFor
        resultBuffer.close

        // Remove the actor from the queue and tell the frontend that we're finished.
        copyQueue = copyQueue - req.actor
        req.actor ! LADemoFileCopyDone(process.exitValue == 0, new DateTime)
    }

    println("LADemoFileCopyMethods started successfully")
}
