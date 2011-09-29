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

import ag.bett.demo.lib.{ExecHelper => EH, DateTimeHelpers => DTH}

import net.liftweb.http._
import net.liftweb.actor._
import net.liftweb.http.S._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import net.liftweb.common._

import java.util.Date
import org.joda.time._


object LADemoLiftActor extends LiftActor
    with LADemoStatMethods
    with LADemoFileCopy {

    protected def messageHandler = {
        case a: LADemoStatGather =>
            a.actor ! this.sysStatInfo

        case LADemoFileCopyRequestList =>
            reply(copyFileList)

        case a: LADemoFileCopyRequest =>
            a.actor ! copyQueueWithInfo(a)
    }

}

