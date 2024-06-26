package io.shiftleft.codepropertygraph.generated.nodes

object AstNode {
  object PropertyNames {
    val Code             = "CODE"
    val ColumnNumber     = "COLUMN_NUMBER"
    val LineNumber       = "LINE_NUMBER"
    val Order            = "ORDER"
    val all: Set[String] = Set(Code, ColumnNumber, LineNumber, Order)
  }

  object Properties {
    val Code         = new overflowdb.PropertyKey[String]("CODE")
    val ColumnNumber = new overflowdb.PropertyKey[scala.Int]("COLUMN_NUMBER")
    val LineNumber   = new overflowdb.PropertyKey[scala.Int]("LINE_NUMBER")
    val Order        = new overflowdb.PropertyKey[scala.Int]("ORDER")
  }

  object PropertyDefaults {
    val Code  = "<empty>"
    val Order = -1: Int
  }

  object Edges {
    val Out: Array[String] = Array()
    val In: Array[String]  = Array()
  }

}

trait AstNodeBase extends AbstractNode {
  def code: String
  def columnNumber: Option[scala.Int]
  def lineNumber: Option[scala.Int]
  def order: scala.Int
}

trait AstNodeNew extends NewNode {
  def code_=(value: String): Unit
  def columnNumber_=(value: Option[scala.Int]): Unit
  def lineNumber_=(value: Option[scala.Int]): Unit
  def order_=(value: scala.Int): Unit
  def code: String
  def columnNumber: Option[scala.Int]
  def lineNumber: Option[scala.Int]
  def order: scala.Int
}

trait AstNode extends StoredNode with AstNodeBase {
  import overflowdb.traversal._

}
