package scala.virtualization.lms
package internal

import util.GraphUtil
import java.io.{File, PrintWriter}
import scala.reflect.RefinedManifest
import scala.collection.mutable.{Map => MMap}

trait GenericCodegen extends Traversal {
  val IR: Expressions
  import IR._

  // TODO: should some of the methods be moved into more specific subclasses?
  
  def kernelFileExt = ""
  def emitKernelHeader(syms: List[Sym[Any]], vals: List[Sym[Any]], vars: List[Sym[Any]], resultType: String, resultIsVar: Boolean, external: Boolean)(implicit stream: PrintWriter): Unit = {}
  def emitKernelFooter(syms: List[Sym[Any]], vals: List[Sym[Any]], vars: List[Sym[Any]], resultType: String, resultIsVar: Boolean, external: Boolean)(implicit stream: PrintWriter): Unit = {}
  
  var analysisResults: MMap[String,Any] = null.asInstanceOf[MMap[String,Any]]
  
  // Initializer
  def initializeGenerator(buildDir:String, args: Array[String], _analysisResults: MMap[String,Any]): Unit = { analysisResults = _analysisResults }
  def finalizeGenerator(): Unit = {}
  def kernelInit(syms: List[Sym[Any]], vals: List[Sym[Any]], vars: List[Sym[Any]], resultIsVar: Boolean): Unit = {}

  def emitDataStructures(path: String): Unit = {}
 
  def dataPath = {
    "data" + java.io.File.separator
  }
  
  def symDataPath(sym: Sym[Any]) = {
    dataPath + sym.id
  }
 
  def emitData(sym: Sym[Any], data: Seq[Any]) {
    val outDir = new File(dataPath)
    outDir.mkdirs()
    val outFile = new File(symDataPath(sym))
    val stream = new PrintWriter(outFile)
    
    for(v <- data) {
      stream.println(v)
    }
    
    stream.close()
  }
  
  // exception handler
  def exceptionHandler(e: Exception, outFile:File, kstream:PrintWriter): Unit = {
      kstream.close()
      outFile.delete
  }
  
  // optional type remapping (default is identity)
  def remap(s: String): String = s
  def remap[A](s: String, method: String, t: Manifest[A]) : String = remap(s, method, t.toString)
  def remap(s: String, method: String, t: String) : String = s + method + "[" + remap(t) + "]"    
  def remap[A](m: Manifest[A]): String = m match {
    case rm: RefinedManifest[A] =>  "AnyRef{" + rm.fields.foldLeft(""){(acc, f) => {val (n,mnf) = f; acc + "val " + n + ": " + remap(mnf) + ";"}} + "}"
    case _ if m.erasure == classOf[Variable[Any]] =>
        remap(m.typeArguments.head)
    case _ =>
      // call remap on all type arguments
      val targs = m.typeArguments
      if (targs.length > 0) {
        val ms = m.toString
        ms.take(ms.indexOf("[")+1) + targs.map(tp => remap(tp)).mkString(",") + "]"
      }
      else m.toString    
  }
  def remapImpl[A](m: Manifest[A]): String = remap(m)
  //def remapVar[A](m: Manifest[Variable[A]]) : String = remap(m.typeArguments.head)

  def hasMetaData: Boolean = false
  def getMetaData: String = null

  def getDSLHeaders: String = null


  // ----------

  def emitBlock(y: Block[Any])(implicit stream: PrintWriter): Unit = {
    val deflist = buildScheduleForResult(getBlockResultFull(y)) // need actual Reify node here, not its embedded exp
    
    for (TP(sym, rhs) <- deflist) {
      emitNode(sym, rhs)
    }
  }

  def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter): Unit = {
    throw new GenerationFailedException("don't know how to generate code for: " + rhs)
  }
  
  def emitValDef(sym: Sym[Any], rhs: String)(implicit stream: PrintWriter): Unit
    
  def emitSource[A,B](f: Exp[A] => Exp[B], className: String, stream: PrintWriter)(implicit mA: Manifest[A], mB: Manifest[B]): List[(Sym[Any], Any)] // return free static data in block
      
  def quote(x: Exp[Any]) : String = x match {
    case Const(s: String) => "\""+s+"\""
    case Const(null) => "null" // why is null getting lifted now? something to do with Equal
    case Const(f: Float) => "%1.10f".format(f) + "f"
    case Const(l: Long) => l.toString + "L"
    case Const(z) => z.toString
    case Sym(n) => "x"+n
    case null => "null"
    case _ => throw new RuntimeException("could not quote " + x)
  }

}



trait GenericNestedCodegen extends NestedTraversal with GenericCodegen {
  val IR: Expressions with Effects
  import IR._

  override def emitBlock(result: Block[Any])(implicit stream: PrintWriter): Unit = {
    focusBlock(result) {
      emitBlockFocused(result)
    }
  }
  
  def emitBlockFocused(result: Block[Any])(implicit stream: PrintWriter): Unit = {
    focusExactScope(result) { levelScope =>
      for (TP(sym, rhs) <- levelScope)
        emitNode(sym, rhs)
    }
  }

  override def emitNode(sym: Sym[Any], rhs: Def[Any])(implicit stream: PrintWriter) = rhs match {
//    case Read(s) =>
//      emitValDef(sym, quote(s))
    case Reflect(s, u, effects) =>
      emitNode(sym, s)
    case Reify(s, u, effects) =>
      // just ignore -- effects are accounted for in emitBlock
    case _ => super.emitNode(sym, rhs)
  }




}
