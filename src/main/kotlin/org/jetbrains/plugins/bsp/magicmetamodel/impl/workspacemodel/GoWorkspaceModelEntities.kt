package org.jetbrains.plugins.bsp.magicmetamodel.impl.workspacemodel

import org.jetbrains.plugins.bsp.magicmetamodel.impl.ModuleState
import org.jetbrains.plugins.bsp.magicmetamodel.impl.toState
import java.nio.file.Path

public data class GoModuleDependency(
  val importPath: String,
  val root: Path,
)

public data class GoModule(
  val module: GenericModuleInfo,
  val importPath: String,
  val root: Path,
  val goDependencies: List<GoModuleDependency>,
) : WorkspaceModelEntity(), Module {
  override fun toState(): ModuleState = ModuleState(
    module = module.toState(),
    goAddendum = GoAddendum(importPath, root, goDependencies).toState(),
  )

  override fun getModuleName(): String = module.name
}

public data class GoAddendum(
  var importPath: String? = null,
  var root: Path? = null,
  val goDependencies: List<GoModuleDependency>,
)
