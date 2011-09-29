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

import ag.bett.demo.lib.{ExecHelper, DateTimeHelpers => DTH}

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
    import ExecHelper._

    val sysStatDummy = LADemoStatInfo(5, 1024, 768)

    var sysStatInfoCache = (sysStatDummy, (new DateTime).minusMinutes(1))
    def sysStatInfo: LADemoStatInfo = {
        
        // Check if the cache is expired
        val cleanDate = (new DateTime).minusSeconds(10)
        if (sysStatInfoCache._2.isAfter(cleanDate))
            return sysStatInfoCache._1
        
        var meminfo = execp("top -l 1")
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
            meminfo = execp("top -n 1")

            // dont really have anything else.. Dummy mode.
            if (meminfo._1 != 0) {
                return sysStatDummy
            }            

            val ProcStats = """.*Mem:\s+(\d+)k total,\s+(\d+)k used,\s+(\d+)k free.*""".r
            meminfo._2.mkString match {
                case ProcStats(totalS, usedS, freeS) =>
                    val used = try { usedS.toInt } catch { case _ => sysStatDummy.memused }
                    val free = try { freeS.toInt } catch { case _ => sysStatDummy.memtotal - sysStatDummy.memused }
                    val total = try { freeS.toInt } catch { case _ => sysStatDummy.memtotal }

                    // normal linux top has 7 lines of header
                    sysStatInfoCache = (LADemoStatInfo(meminfo._2.length - 7, total, used), new DateTime)
                    return sysStatInfoCache._1
                case _ => return sysStatDummy
            }            
        }
    }

}


case class LADemoFileCopyRequestList
case class LADemoFileCopyList(files: List[Path])

case class LADemoFileCopyRequest(actor: CometActor, file: Path)
case class LADemoFileCopyQueued(active: Boolean, lastPing: DateTime, file: Path)

sealed trait LADemoFileCopyInfo
case class LADemoFileCopyQueue(waiting: Int) extends LADemoFileCopyInfo
case class LADemoFileCopyStatus(file: Path, percent: Int, speed: String, remaining: String) extends LADemoFileCopyInfo
case class LADemoFileCopyDone(success: Boolean) extends LADemoFileCopyInfo

trait LADemoFileCopy {
    import ExecHelper._

    val copyPath = Path(Props.get("filecopy.path") openOr "/tmp/i.didnt.configure.jack")
    lazy val copyFileList: LADemoFileCopyList = {
        val myList: List[Path] = {
            if (copyPath.exists && copyPath.isDirectory && copyPath.canWrite)
                copyPath.children().toList
            else List()
        }
        (copyPath / "targets").createDirectory(failIfExists=false)
        LADemoFileCopyList(myList)
    }

    var copyQueue: Map[CometActor, LADemoFileCopyQueued] = Map()

    def copyQueueWithInfo(req: LADemoFileCopyRequest): LADemoFileCopyInfo = {
        copyQueue = copyQueue.filter(_._2.lastPing.isAfter((new DateTime).minusSeconds(30)))
        copyQueue = copyQueue ++ (copyQueue.get(req.actor) match {
            case Some(cq: LADemoFileCopyQueued) =>
                Map(req.actor -> LADemoFileCopyQueued(cq.active, (new DateTime), cq.file))
            case _ =>
                Map(req.actor -> LADemoFileCopyQueued(false, (new DateTime), req.file))
        })

        var countQueue = 0
        var countActive = 0
        copyQueue.map(q =>
            if (q._1.equals(req.actor)) {
                
                // There are people in the queue infront of us
                if (countQueue > 0)
                    return LADemoFileCopyQueue(countActive)
                
                if (countActive < 5)
                    return copyFileStart(req.file, copyPath / "targets" / scala.util.Random.nextLong.toString, req.actor)
            }
            else if (q._2.active)
                countActive += 1
            else countQueue += 1
        )
        
        LADemoFileCopyQueue(0)
    }

    def copyFileStart(source: Path, target: Path, actor: CometActor): LADemoFileCopyInfo = {
        val cmd = "rsync --progress %s %s".format(source.path, target.path)
        val process = Runtime.getRuntime.exec(cmd)
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
                        actor ! LADemoFileCopyStatus(source.name, percent, speed, remaining)
                    case _ =>
                }
            }
        } while (line != null)

        process.waitFor
        resultBuffer.close
        
        // Fire this first, so there won't be any requests occuring
        actor ! LADemoFileCopyDone(process.exitValue == 0)
        
        // Remove the actor from the queue
        copyQueue = copyQueue - actor
        
        // Dummy-wise, make really really sure the comet actor stops requesting.
        LADemoFileCopyDone(process.exitValue == 0)
    }

}