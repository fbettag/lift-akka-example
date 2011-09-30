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

case class LADemoStatGather(actor: CometActor)
case class LADemoStatInfo(procs: Int, memtotal: Int, memused: Int)

trait LADemoStatMethods {
    val sysStatDummy = LADemoStatInfo(5, 1024, 768)

    var sysStatInfoCache = (sysStatDummy, (new DateTime).minusMinutes(1))
    def sysStatInfo: LADemoStatInfo = {
        // Check if the cache is expired
        val cleanDate = (new DateTime).minusSeconds(10)
        if (sysStatInfoCache._2.isAfter(cleanDate))
        return sysStatInfoCache._1

        var meminfo = sysStatExec(List("top", "-l 1"))
        // OSX mode
        if (meminfo._1 == 0) {
            val TopStats = """.*PhysMem: (\d+)M wired, (\d+)M active, (\d+)M inactive, (\d+)M used, (\d+)M free.*""".r
            meminfo._2.mkString match {
                case TopStats(wiredS, activeS, inactiveS, usedS, freeS) =>
                val used = try { usedS.toInt } catch { case _ => sysStatDummy.memused }
                val free = try { freeS.toInt } catch { case _ => sysStatDummy.memtotal - sysStatDummy.memused }
                val total = used + free

                // osx top has 12 lines of header
                sysStatInfoCache = (LADemoStatInfo(meminfo._2.length - 12, total, used), new DateTime)
                return sysStatInfoCache._1
                case _ => return sysStatDummy
            }

            // if it is not working, try Linux fallback.    
        } else {
            try {
                var total = sysStatDummy.memtotal
                val totalMatch = "^MemTotal:\\s+(\\d+).*".r
                var free = sysStatDummy.memused
                val freeMatch = "^MemFree:\\s+(\\d+).*".r
                
                val memstats = sysStatExec(List("cat", "/proc/meminfo"))
                if (memstats._1 != 0) return sysStatDummy
                memstats._2.foreach(f => f match {
                    case totalMatch(totalS) => try { total = totalS.toInt / 1024 } catch { case _ => }
                    case freeMatch(freeS) => try { free = freeS.toInt / 1024 } catch { case _ => }
                    case _ =>
                })
 
                val procstats = sysStatExec(List("ps", "aux"))
                if (procstats._1 != 0) return sysStatDummy
                sysStatInfoCache = (LADemoStatInfo(procstats._2.length -1, total, total-free), new DateTime)
                return sysStatInfoCache._1

            } catch {
                case _ => return sysStatDummy
            }
        }
        
        return sysStatDummy
    }

    def sysStatExec(cmd: List[String]): (Int, List[String]) = {
        val process = Runtime.getRuntime.exec(cmd.toArray)
        val resultBuffer = new BufferedReader(new InputStreamReader(process.getInputStream))
        var line: String = null
        var lineList: List[String] = Nil

        do {
            line = resultBuffer.readLine
            if (line != null) {
                lineList = line :: lineList
            }
        } while (line != null)

        process.waitFor
        resultBuffer.close

        (process.exitValue, lineList.reverse)
    }

}


case class LADemoFileCopyRequestList
case class LADemoFileCopyList(files: List[Path])

case class LADemoFileCopyRequest(actor: CometActor, file: Path)
case class LADemoFileCopyAbortRequest(actor: CometActor)

sealed trait LADemoFileCopyInfo
case class LADemoFileCopyQueue(waiting: Int) extends LADemoFileCopyInfo
case class LADemoFileCopyStatus(file: Path, percent: Int, speed: String, remaining: String) extends LADemoFileCopyInfo
case class LADemoFileCopyDone(success: Boolean) extends LADemoFileCopyInfo

sealed trait LADemoFileCopyInternal
case class LADemoFileCopyInternalWait extends LADemoFileCopyInternal
case class LADemoFileCopyInternalStart(source: Path, target: Path, actor: CometActor) extends LADemoFileCopyInternal

trait LADemoFileCopyMethods {	

    val copyMaxProcs = 5

    val copyPath = Path(Props.get("filecopy.path") openOr "/tmp/i.didnt.configure.jack")
    lazy val copyFileList: LADemoFileCopyList = {
        // Only list files bigger than 200MB
        val myList: List[Path] = {
            if (copyPath.exists && copyPath.isDirectory && copyPath.canWrite)
            copyPath.children().toList.filter(_.size.getOrElse(0L).toLong > 200*1024*1024)
            else List()
        }
        (copyPath / "targets").createDirectory(failIfExists=false)
        LADemoFileCopyList(myList)
    }

    var copyQueue: Map[CometActor, Path] = Map()

    def copyQueueWithInfo(req: LADemoFileCopyRequest): LADemoFileCopyInternal = {
        copyQueue = copyQueue ++ Map(req.actor -> req.file)

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
                    return LADemoFileCopyInternalStart(req.file, target, req.actor)
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
        // make it real slow so people can enjoy ;)
        val args = scala.Array("nice", "-n -20", "rsync", "--progress", req.source.path.toString, req.target.path.toString)
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
        req.actor ! LADemoFileCopyDone(process.exitValue == 0)
    }

}