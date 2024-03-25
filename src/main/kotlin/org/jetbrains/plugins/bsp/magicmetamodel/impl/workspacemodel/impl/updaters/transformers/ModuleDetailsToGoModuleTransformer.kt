package org.jetbrains.plugins.bsp.magicmetamodel.impl.workspacemodel.impl.updaters.transformers

import ch.epfl.scala.bsp4j.BuildTarget
import org.jetbrains.bsp.protocol.utils.extractGoBuildTarget
import org.jetbrains.plugins.bsp.magicmetamodel.ModuleNameProvider
import org.jetbrains.plugins.bsp.magicmetamodel.ProjectDetails
import org.jetbrains.plugins.bsp.magicmetamodel.impl.workspacemodel.*
import org.jetbrains.plugins.bsp.magicmetamodel.impl.workspacemodel.ModuleDetails
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.toPath


internal class ModuleDetailsToGoModuleTransformer(
  private val targetsMap: Map<BuildTargetId, BuildTargetInfo>,
  private val projectDetails: ProjectDetails,
  moduleNameProvider: ModuleNameProvider,
  projectBasePath: Path,
) : ModuleDetailsToModuleTransformer<GoModule>(targetsMap, moduleNameProvider) {
  override val type = "GO_MODULE"

  private val sourcesItemToGoSourceRootTransformer = SourcesItemToGoSourceRootTransformer(projectBasePath)

  override fun transform(inputEntity: ModuleDetails): GoModule{
    val goBuildInfo = extractGoBuildTarget(inputEntity.target) ?: error("Transform error, cannot extract GoBuildTarget")

    return GoModule(
      module = toGenericModuleInfo(inputEntity),
      sourceRoots = sourcesItemToGoSourceRootTransformer.transform(inputEntity.sources.map {
        BuildTargetAndSourceItem(
          inputEntity.target,
          it,
        )
      }),
      resourceRoots = emptyList(), //TODO
      importPath = goBuildInfo.importPath,
      root = URI.create(inputEntity.target.baseDirectory).toPath(),
      goDependencies = toGoDependencies(inputEntity)
    )
  }

  override fun toGenericModuleInfo(inputEntity: ModuleDetails): GenericModuleInfo {
    val bspModuleDetails = BspModuleDetails(
      target = inputEntity.target,
      dependencySources = inputEntity.dependenciesSources,
      type = type,
      javacOptions = null,
      pythonOptions = inputEntity.pythonOptions,
      libraryDependencies = inputEntity.libraryDependencies,
      moduleDependencies = inputEntity.moduleDependencies,
      scalacOptions = inputEntity.scalacOptions,
    )
    return bspModuleDetailsToModuleTransformer.transform(bspModuleDetails)
  }

  private fun toGoDependencies(inputEntity: ModuleDetails): List<GoModuleDependency> =
    inputEntity.moduleDependencies
      .asSequence()
      .mapNotNull { targetsMap[it] }
      .map { it.id.toBsp4JTargetIdentifier() }
      .mapNotNull { buildTargetIdentifier -> projectDetails.targets.find { it.id == buildTargetIdentifier } }
      .mapNotNull { buildTargetToGoModuleDependency(it) }
      .toList()

  private fun buildTargetToGoModuleDependency(buildTarget: BuildTarget): GoModuleDependency? =
    extractGoBuildTarget(buildTarget)?.let {
      GoModuleDependency(
        importPath = it.importPath,
        root = URI.create(buildTarget.baseDirectory).toPath()
      )
    }
}

internal class SourcesItemToGoSourceRootTransformer(private val projectBasePath: Path) :
  WorkspaceModelEntityPartitionTransformer<BuildTargetAndSourceItem, GenericSourceRoot> {
  private val sourceRootType = "go-source"
  private val testSourceRootType = "go-test"

  override fun transform(inputEntities: List<BuildTargetAndSourceItem>): List<GenericSourceRoot> {
    val allSourceRoots = super.transform(inputEntities)

    return allSourceRoots.filter { isNotAChildOfAnySourceDir(it, allSourceRoots) }
  }

  private fun isNotAChildOfAnySourceDir(
    sourceRoot: GenericSourceRoot,
    allSourceRoots: List<GenericSourceRoot>,
  ): Boolean =
    allSourceRoots.none { sourceRoot.sourcePath.parent.startsWith(it.sourcePath) }

  override fun transform(inputEntity: BuildTargetAndSourceItem): List<GenericSourceRoot> {
    val rootType = inferRootType(inputEntity.buildTarget)

    return SourceItemToSourceRootTransformer
      .transform(inputEntity.sourcesItem.sources)
      .map { toGoSourceRoot(it, rootType) }
      .filter { it.sourcePath.isPathInProjectBasePath(projectBasePath) }
  }

  private fun inferRootType(buildTarget: BuildTarget): String =
    if (buildTarget.tags.contains("test")) testSourceRootType else sourceRootType

  private fun toGoSourceRoot(
    sourceRoot: SourceRoot,
    rootType: String,
  ): GenericSourceRoot {
    return GenericSourceRoot(
      sourcePath = sourceRoot.sourcePath,
      rootType = rootType,
    )
  }
}
