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


case class LADemoStatGather(actor: CometActor)
case class LADemoStatGatherRemote
case class LADemoStatInfo(procs: Int, memtotal: Int, memused: Int)


/* This is universal for both actor types. */
trait LADemoStatMethods {
    println("LADemoStatMethods starting...")

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

    println("LADemoStatMethods started successfully")
}
