package io.shiftleft.semanticcpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.Method
import io.shiftleft.passes.{CpgPassV2, DiffGraphHandler}
import io.shiftleft.semanticcpg.language._
import io.shiftleft.semanticcpg.passes.cfgcreation.CfgCreator

/**
  * A pass that creates control flow graphs from abstract syntax trees.
  *
  * Control flow graphs can be calculated independently per method.
  * Therefore, we inherit from `ParallelCpgPass`.
  *
  * Note: the version of OverflowDB that we currently use as a
  * storage backend does not assign ids to edges and this pass
  * only creates edges at the moment. Therefore, we currently
  * do without key pools.
  * */
class CfgCreationPass(cpg: Cpg) extends CpgPassV2[Method] {

  override def partIterator: Iterator[Method] = cpg.method.iterator

  override def runOnPart(diffGraphHandler: DiffGraphHandler, method: Method): Unit =
    new CfgCreator(method).run().foreach(diffGraphHandler.addDiffGraph)

}
