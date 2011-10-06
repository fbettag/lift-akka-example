What's this?
=======


This project was presented at the Scala LiftOff Conference 2011 in London.
It was part of my Talk [Liftweb in the Real World. A hyper-polygot's tale.](http://goodstuff.im/fbettag-liftweb-in-the-real-world-a-hyper-pol)

It enables you to choose between a **local LiftActor running in your WebServer or** you can select an **Akka Remote Actor** which will allow you to run the main-functionailty on another server.

The goal was to build a sample application, which utilizes Comet to give a user feedback of an rsync-copy operation.


## How to run?

Edit src/main/resources/props/default.props in the lift/ folder and change the path to something with files (e.g. your Downloads folder). It also needs **writing permissions** on that folder in order to write the copied files into a folder called "target" (right inside the specified dir).

After that it's straight on, just run:

```
mvn install -pl akka/
mvn jetty:run -pl lift/
```


## With Akka?

If you simply want a local AkkaActor, then uncomment the lines respectively in lift/src/main/scala/ag/bett/demo/comet/Comet.scala. There are dispatch-Blocks at around lines 53-65 (Stats) and 116-134 (Copy). Please make sure you only use one variant, otherwise it won't compile.

````scala
/* LiftActor Local */
val targetActor = LADemoLiftActor
def reschedule = ActorPing.schedule(targetActor, targetRequest, 5 seconds)

/* Akka Actor Local */
val targetActor = LADemoAkkaActor.actor
def reschedule = Scheduler.scheduleOnce(targetActor, targetRequest, 5, TimeUnit.SECONDS)

/* Akka Actor Remote Bridge (per Comet Actor) */
val targetActor = Actors.actorOf(classOf[LADemoAkkaRemoteBridgeService]).start()    
def reschedule = Scheduler.scheduleOnce(targetActor, targetRequest, 5, TimeUnit.SECONDS)
````

````scala
/* LiftActor Local */
val targetActor = LADemoLiftActor

/* Akka Actor Local */
val targetActor = LADemoAkkaActor.actor

/* Akka Actor Remote Bridge (per Comet Actor) */
val targetActor = Actors.actorOf(classOf[LADemoAkkaRemoteBridgeService]).start()    

def reschedule = request match {
    case Full(a: LADemoFileCopyRequest) =>
    
        /* LiftActor */
        ActorPing.schedule(targetActor, a, 10 seconds)
        
        /* Akka Actor */
        Scheduler.scheduleOnce(targetActor, a, 10, TimeUnit.SECONDS)  // AkkaActor
    case _ =>
}
````


## With Akka RemoteActor?

Edit lift/src/main/resources/props/default.props and change the akka.remote.host to the appropriate hostname or ip. Also edit akka/src/main/scala/ag/bett/demo/remote/FileCopy.scala around line 67 and correct the Path for your files.

```
mvn package -pl akka/
cd akka/
wget [http://akka.io/downloads/akka-microkernel-1.2.zip](http://akka.io/downloads/akka-microkernel-1.2.zip)
unzip akka-microkernel-1.2.zip

cp akka.conf akka-microkernel-1.2/config/
cp target/akka.*-dependencies.jar akka-microkernel-1.2/deploy/

sh ./akka-microkernel-1.2/bin/akka (or chmod +x)
```

On another console start the liftweb application with:

````mvn jetty:run -pl lift/````


## Thanks

Thanks to everybody in the Lift Community and on [Liftweb Google Groups](http://groups.google.com/group/liftweb).


## License

```
  Copyright 2011 Franz Bettag <franz@bett.ag>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

```

