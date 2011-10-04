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


/* Akka Remote Boot-class */
class LADemoAkkaRemoteActorBoot {
    remote.start("localhost", 2552)
    remote.register("lift-akka-example-service", actorOf[LADemoAkkaRemoteActorService])

}


/* Service implementation */
class LADemoAkkaRemoteActorService extends Actor
    with LADemoStatMethods
    with LADemoFileCopyRemoteMethods {

    override def receive = {
        case LADemoStatGatherRemote =>
            val chan = self.channel
            chan ! sysStatInfo
            self.reply(sysStatInfo)

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
