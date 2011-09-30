What's this?
=======


This project was presented at the Scala LiftOff Conference 2011 in London.
It was part of my Talk [Liftweb in the Real World. A hyper-polygot's tale.](http://goodstuff.im/fbettag-liftweb-in-the-real-world-a-hyper-pol)

It enables you to choose between a **local LiftActor running in your WebServer or** you can select an **Akka Remote Actor** which will allow you to run the main-functionailty on another server.

The goal was to build a sample application, which utilizes Comet to give a user feedback of an rsync-copy operation.


## How to run?

Edit src/main/resources/props/default.props and change the path to something with files (e.g. your Downloads folder). It also needs **writing permissions** on that folder in order to write the copied files into a folder called "target" (right inside the specified dir).

After that it's straight on, just run:

```
mvn jetty:run
```


## With Akka?

If you simply want a local AkkaActor, then uncomment the 4 lines respectively in src/main/scala/ag/bett/demo/comet/Traitor.scala. If you search for "Akka", you'll find 2 blocks in both of the traits, simple (un)commeting will suffice.


## With Akka RemoteActor?

Edit src/main/resources/props/default.props and change the akka.remote.host to the appropriate hostname or ip.

```
cd akka
mvn package
cp target/akka.backend*.jar your/path/to/akka/deploy/
your/path/to/akka/bin/akka
```


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

