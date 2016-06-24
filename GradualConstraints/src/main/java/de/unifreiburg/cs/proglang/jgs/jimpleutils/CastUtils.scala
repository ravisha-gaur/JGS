package de.unifreiburg.cs.proglang.jgs.jimpleutils

import de.unifreiburg.cs.proglang.jgs.constraints.TypeDomain.Type
import de.unifreiburg.cs.proglang.jgs.signatures.parse.AnnotationParser
import soot.SootMethod

import scala.util.{Failure, Success, Try}

/**
  * Utilities for implementing and using "cast detectors" (i.e., instances of `Cast`)
  */
object CastUtils {

  sealed case class Conversion[Level](val source: Type[Level],
                                      val dest: Type[Level])

  def parseConversion[Level](typeParser : AnnotationParser[Type[Level]], s : String) : Try[Conversion[Level]] = {
    val err = new IllegalArgumentException(s"Unable to parse `${s}' as a type conversion. Expected: [type] ~> [type].")
    s.trim.split("\\s") match {
      case Array(st1, "~>", st2) =>
        val result = for (
          t1 <- typeParser.parse(st1);
          t2 <- typeParser.parse(st2)
        ) yield Conversion(t1, t2)
        result match {
          case Some(conv) => Success(conv)
          case _ => Failure(err)
        }
      case _ => Failure(err)
    }
  }



}