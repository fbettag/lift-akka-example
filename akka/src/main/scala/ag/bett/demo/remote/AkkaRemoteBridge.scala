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
import akka.actor.Actor._
import net.liftweb.http._
import net.liftweb.actor._
import net.liftweb.util._
import net.liftweb.common._

import org.joda.time._


class LADemoAkkaRemoteBridgeService extends Actor {
    println("starting proxy")

    // Remote Actor
    val remoteHost = Props.get("akka.remote.host") openOr("127.0.0.1")
    val remotePort = Props.get("akka.remote.port").openOr("2552").toInt
    val remoteActor = remote.actorFor("lift-akka-example-service", remoteHost, remotePort)

    private def replyFromRemote(x: Any): Option[Any] =
        remoteActor !! x

    // Comet Actor
    
    private var cometActor: Option[CometActor] = None
    private def sendToComet(cometActor: CometActor, x: Option[Any]) {
        x match {
            case Some(a: Any) => cometActor ! a
            case _ =>
        }
    }


    // Akka dispatching (rewriting for Remote)
    override def receive = {
        case a: LADemoStatGather =>
            println("proxy in: " + a)
            cometActor = Some(a.actor)
            sendToComet(a.actor, replyFromRemote(LADemoStatGatherRemote))

        case a: LADemoStatInfo =>
            cometActor match {
                case Some(ca: CometActor) =>
                    println("found cometactor!")
                    ca ! a
                case _ => println(" no cometactor found :(")
            }
        // case LADemoFileCopyRequestList =>
        //     self.reply(copyFileList)
        // 
        // case a: LADemoFileCopyRequest =>
        //     copyQueueWithInfo(a) match {
        //         case stat: LADemoFileCopyInternalStart =>
        //             LAScheduler.execute(() => copyFileStart(stat))
        //         case _ =>
        //     }
        // 
        // case a: LADemoFileCopyInternalStart =>
        //     copyFileStart(a)
        // 
        // case a: LADemoFileCopyAbortRequest =>
        //     copyDequeue(a.actor)
    }

}
