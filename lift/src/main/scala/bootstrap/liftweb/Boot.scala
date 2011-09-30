package bootstrap.liftweb

import net.liftweb.util._
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.provider._
import net.liftweb.sitemap._
import net.liftweb.sitemap.Loc._
import Helpers._


class Boot {
    def boot {

        // where to search snippet
        LiftRules.addToPackages("ag.bett.demo")

        // Build SiteMap
        def sitemap() = SiteMap(Menu("Home") / "index")

        /*
        * Show the spinny image when an Ajax call starts
        */
        LiftRules.ajaxStart =
            Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)

        /*
        * Make the spinny image go away when it ends
        */
        LiftRules.ajaxEnd =
            Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)

        LiftRules.early.append(makeUtf8)

        //LiftRules.loggedInTest = Full(() => User.loggedIn_?)

        //S.addAround(DB.buildLoanWrapper)
    }

    /**
    * Force the request to be UTF-8
    */
    private def makeUtf8(req: HTTPRequest) {
        req.setCharacterEncoding("UTF-8")
    }
}
