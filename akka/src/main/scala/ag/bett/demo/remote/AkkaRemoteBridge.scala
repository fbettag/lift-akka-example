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
import java.util.concurrent.TimeUnit

class LADemoAkkaRemoteBridgeService extends Actor {
    println("starting proxy")

    // Remote Actor
    val remoteHost = Props.get("akka.remote.host") openOr("127.0.0.1")
    val remotePort = Props.get("akka.remote.port").openOr("2552").toInt
    val remoteActor = remote.actorFor("lift-akka-example-service", remoteHost, remotePort)
    val actorRef = scala.util.Random.nextLong

    protected def replyFromRemote(x: Any): Option[Any] =
        remoteActor !! x

    protected def sendToComet(cometActor: CometActor, x: Option[Any]) {
        x match {
            case Some(a: Any) => cometActor ! a
            case _ =>
        }
    }

    protected def sendToComet(cometActor: CometActor, x: Any) {
        sendToComet(cometActor, Some(x))
    }


    // Akka dispatching (rewriting for Remote)
    override def receive = {
        case a: LADemoStatGather =>
            sendToComet(a.actor, replyFromRemote(LADemoStatGatherRemote))

        case a: LADemoFileCopyRequestList =>
            sendToComet(a.actor, replyFromRemote(LADemoFileCopyRequestListRemote))

        // We are doing polling over Akka Remote Actor an push useful stuff to the CometActor
        case a: LADemoFileCopyRequest =>
            replyFromRemote(LADemoFileCopyRequestRemote(actorRef, a.file)) match {

                // Requeue if it started copying!
                case Some("starting ;)") =>
                    Scheduler.scheduleOnce(self, a, 500, TimeUnit.MILLISECONDS)
                    
                // Catch done, forward it and don't requeue
                case Some(cd: LADemoFileCopyDone) =>
                    sendToComet(a.actor, cd)
                    
                // Requeue for polling if there comes a status (done etc doesnt do polling)
                case Some(cs: LADemoFileCopyStatus) =>
                    sendToComet(a.actor, cs)
                    Scheduler.scheduleOnce(self, a, 1, TimeUnit.SECONDS)
                    
                // Pass anything else back
                case Some(any: Any) =>
                    sendToComet(a.actor, any)
                    
                case _ =>
            }

        case a: LADemoFileCopyAbortRequest =>
            remoteActor ! LADemoFileCopyAbortRequestRemote(actorRef)
            self.stop()
    }

}
