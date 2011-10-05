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
import ag.bett.demo.lib._
import ag.bett.demo.lib.{DateTimeHelpers => DTH}

import net.liftweb.http._
import net.liftweb.actor._
import net.liftweb.http.S._
import net.liftweb.http.js._
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsCmds._
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.js.JsCmd
import net.liftweb.http.SHtml._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import net.liftweb.common._
import net.liftweb.json._
import net.liftweb.json.Serialization.write
import akka.actor._

import scalax.file.Path

import java.util.Date
import java.util.concurrent.TimeUnit

import org.joda.time._

import scala.xml._


class StatComet extends CometActor {

    /* LiftActor Local */
    //val targetActor = LADemoLiftActor
    //def reschedule = ActorPing.schedule(targetActor, targetRequest, 5 seconds)

    /* Akka Actor Local */
    //val targetActor = LADemoAkkaActor.actor
    //def reschedule = Scheduler.scheduleOnce(targetActor, targetRequest, 5, TimeUnit.SECONDS)
    
    /* Akka Actor Remote Bridge (per Comet Actor) */
    val targetActor = Actors.actorOf(classOf[LADemoAkkaRemoteBridgeService]).start()    
    def reschedule = Scheduler.scheduleOnce(targetActor, targetRequest, 5, TimeUnit.SECONDS)


    override def defaultPrefix = Full("EasyPeasyStatsWithComet")

    // Make json-serialization for "write" work.
    implicit val formats = Serialization.formats(NoTypeHints)

    // If this actor is not used for 2 minutes, destroy it
    override def lifespan: Box[TimeSpan] = Full(2 minutes)

    val targetRequest = LADemoStatGather(this)

    targetActor ! targetRequest

    // Comet dispatcher, this will receive msgs from the Akka Backend
    override def lowPriority = {
        case a: LADemoStatInfo =>
            cachedStat = Full(a)
            partialUpdate(
                JsRaw("console.log('LADemoStatInfo: ' + %s)".format(write(a))) &
                SetHtml("comet_stats", cssSel.apply(defaultHtml))
            )
            reschedule
    }

    // Cached reply from the backend.
    // Otherwise everybody after a pageload has to wait
    // 10 seconds (worst case) to get some rendering.
    private var cachedStat: Box[LADemoStatInfo] = Empty

    // Return type required to be RenderOut, implicit?
    def render = cssSel

    // Simply separate this
    def cssSel: CssSel =
        ".last_updated *" #>        DTH.now.toString("yyyy-MM-dd HH:mm:ss") &
        (cachedStat match {
            case Full(a: LADemoStatInfo) =>
                ".stat_procs *" #>        "%s procs".format(a.procs) &
                ".stat_memtotal *" #>     "%s MB".format(a.memtotal) &
                ".stat_memused *" #>      "%s MB".format(a.memused)
            case _ => ".dummy" #> ""
        })

}



class CopyComet extends CometActor {
    
    /* LiftActor Local */
    //val targetActor = LADemoLiftActor

    /* Akka Actor Local */
    //val targetActor = LADemoAkkaActor.actor
    
    /* Akka Actor Remote Bridge (per Comet Actor) */
    val targetActor = Actors.actorOf(classOf[LADemoAkkaRemoteBridgeService]).start()    

    def reschedule = request match {
        case Full(a: LADemoFileCopyRequest) =>
        
            /* LiftActor */
            //ActorPing.schedule(targetActor, a, 10 seconds)
            
            /* Akka Actor */
            Scheduler.scheduleOnce(targetActor, a, 10, TimeUnit.SECONDS)  // AkkaActor
        case _ =>
    }


    override def defaultPrefix = Full("NiceFileCopyWithComet")

    // Make json-serialization for "write" work.
    implicit val formats = Serialization.formats(NoTypeHints)

    // If this actor is not used for 10 seconds, destroy it
    override def lifespan: Box[TimeSpan] = Full(10 seconds)

    targetActor ! LADemoFileCopyRequestList(this)

    var fileList: Map[String, Int] = Map()
    
    def thankYouPartial(a: String): JsCmd = thankYouPartial(<strong>{a}</strong>)
    def thankYouPartial(a: NodeSeq): JsCmd = SetHtml("comet_copy_list", a)

    // Comet dispatcher, this will receive msgs from the Akka Backend
    override def lowPriority = {
        case a: LADemoFileCopyList =>
            fileList = a.files
            partialUpdate(
                JsRaw("console.log('LADemoFileCopyList: %s')".format(a.toString)) &
                thankYouPartial(cssSel.apply(defaultHtml))
            )         
   
        case a: LADemoFileCopyQueue =>
            cachedStat = Full(a)
            reschedule
            partialUpdate(
                JsRaw("console.log('LADemoFileCopyQueue: %s')".format(a.toString)) &
                thankYouPartial("You are number %s in queue!".format(a.waiting + 1)) &
                JsRaw("lademo.copyStatus(%s)".format(write(a)))
            )
        
        case a: LADemoFileCopyStatus =>
            cachedStat = Full(a)
            partialUpdate(
                JsRaw("console.log('LADemoFileCopyStatus: %s')".format(a.toString)) &
                thankYouPartial(Group(
                    <div id="progressbar"></div>

                    <strong>Copy for file '{a.file.name}' @ {a.speed} in progress! {a.remaining} remaining.</strong>
                )) &
                JsRaw("lademo.copyStatus(%s)".format(write(a)))
            )
        
        case a: LADemoFileCopyDone =>
            request = Empty
            partialUpdate(
                JsRaw("console.log('LADemoFileCopyDone: %s')".format(a.toString)) &
                thankYouPartial(
					if (a.success) "Successfully copied!"
					else "Failed to copy :( Please make sure the Filename does not contain any weird chars") &
	            JsRaw("lademo.copyStatus(%s)".format(write(a)))
            )
    }

    def startFileCopy(file: String): JsCmd = {
        request = Full(LADemoFileCopyRequest(this, file))
        targetActor ! (request.open_!)
        Noop
    }

	override def localShutdown() {
		targetActor ! LADemoFileCopyAbortRequest(this)
	}

    // Cached reply from the backend.
    // Otherwise everybody after a pageload has to wait
    // 10 seconds (worst case) to get some rendering.
    private var cachedStat: Box[LADemoFileCopyInfo] = Empty
    private var request: Box[LADemoFileCopyRequest] = Empty

    // Return type required to be RenderOut, implicit?
    def render = cssSel

    // Simply separate this
    def cssSel: CssSel =
        ".last_updated *" #>        DTH.now.toString("yyyy-MM-dd HH:mm:ss") &
        (request match {
            case Full(a: LADemoFileCopyRequest) =>
                "*" #>              <strong>Waiting for backend response..</strong>
            case _ if (fileList.toList.length == 0) =>
                "*" #>              <strong>No files found! Check the filecopy.path setting in .props!</strong>
            case _  =>
                "li *" #>           fileList.map(f =>
                    "a" #> a(() => startFileCopy(f._1), Text("%s %sMB".format(f._1, f._2)))
                )
        })

}
