// from http://code.google.com/p/scala-utilities
package ag.bett.demo.lib

import java.lang.reflect._
import java.io._

object ExecHelper {
    var currDir : Option[File] = None

    val runTime = Runtime.getRuntime   

    def showClass [T] (inputClass : java.lang.Class[T], verbose : Boolean, indent : Int) : Unit = {
        val methods = inputClass.getDeclaredMethods
        val publicMethods = methods.filter(m => Modifier.isPublic(m.getModifiers))

        val className = inputClass.getCanonicalName

        val indentString = (0 until indent).foldLeft("")((x,y) => x +"  ")

        println (indentString + className + "\n" + indentString + "------------")

        if (verbose) {
            publicMethods.foreach(m => println (indentString + m.toString.replaceAll(className + "\\.", "")))
            val publicFields = inputClass.getDeclaredFields.filter(f => Modifier.isPublic(f.getModifiers))
            println (indentString + "---Public Fields---")
            publicFields.foreach(f => println (indentString + f))
            if (inputClass.getSuperclass != null) {
                println("")
                showClass (inputClass.getSuperclass, verbose, indent + 1)
            }
        } else {
            val names = publicMethods.map(m => m.getName).toList.removeDuplicates
            names.foreach(name => println (indentString + name + "( )"))
        }


    }

    def ? (obj : AnyRef, verbose : Boolean) = {
        val classToShow = obj.getClass
        showClass(classToShow, verbose, 0)
    }

    def ? (obj : AnyRef) : Any = {
        ? (obj, false)
    }

    def ?? (obj : AnyRef) = {
        ? (obj, true)
    }

    def exec (cmd : String) = {
        val process = if (currDir.isDefined) runTime.exec (cmd, null, currDir.get) else runTime.exec(cmd)
        val resultBuffer = new BufferedReader(new InputStreamReader(process.getInputStream))
        var line : String = null

        do {
            line = resultBuffer.readLine
            if (line != null) {
                println (line)
            }
        } while (line != null)

        process.waitFor
        resultBuffer.close
        process.exitValue
    }


    def execp (cmd : String) = {
        val process = if (currDir.isDefined) runTime.exec (cmd, null, currDir.get) else runTime.exec(cmd)
        val resultBuffer = new BufferedReader(new InputStreamReader(process.getInputStream))
        var line : String = null
        var lineList : List[String] = Nil


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


    def execp (cmd : String, outFile:String) = {
        val process = if (currDir.isDefined) runTime.exec (cmd, null, currDir.get) else runTime.exec(cmd)
        val resultBuffer = new BufferedReader(new InputStreamReader(process.getInputStream))
        val outputWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile)))
        var line : String = null

        do {
            line = resultBuffer.readLine
            if (line != null) {
                outputWriter.write(line)
                outputWriter.newLine
            }
        } while (line != null)

        process.waitFor
        resultBuffer.close
        outputWriter.close

        process.exitValue
    }

    def cwd (dir : String) = {
        currDir = Some(new File(dir))
    }

    def exists(name : String) = {
        (new File(name)).exists
    }

    case class hex(value : Int) { override def toString = "0x" + value.toHexString }
}
