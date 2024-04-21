package org.jetbrains.plugins.bsp.magicmetamodel.impl.workspacemodel.impl.updaters.transformers

import ch.epfl.scala.bsp4j.ResourcesItem
import org.jetbrains.plugins.bsp.magicmetamodel.impl.workspacemodel.ResourceRoot
import java.nio.file.Path

internal class ResourcesItemToGoResourceRootTransformer(private val projectBasePath: Path) :
  WorkspaceModelEntityPartitionTransformer<ResourcesItem, ResourceRoot> {
  override fun transform(inputEntity: ResourcesItem): List<ResourceRoot> =
    inputEntity.resources
      .map(this::toResourceRoot)
      .distinct()

  private fun toResourceRoot(resourcePath: String) =
    ResourceRoot(
      resourcePath = RawUriToDirectoryPathTransformer.transform(resourcePath),
      rootType = "",
    )
}
