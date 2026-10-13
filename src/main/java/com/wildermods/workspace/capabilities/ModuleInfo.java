package com.wildermods.workspace.capabilities;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

import com.wildermods.thrixlvault.utils.version.Version;
import com.wildermods.workspace.capabilities.CapabilityHandler.SourceStrategy;

public final record ModuleInfo(String group, String artifact, Version version, Path relativeJarPath, Path projectRoot, SourceStrategy sourceStrategy, List<NativeArtifact> natives) implements Serializable {
	public ModuleInfo {
		natives = List.copyOf(natives);
	}

	public ModuleInfo(String group, String artifact, Version version, Path relativeJarPath, Path projectRoot, SourceStrategy sourceStrategy) {
		this(group, artifact, version, relativeJarPath, projectRoot, sourceStrategy, List.of());
	}

	/** A classified native JAR belonging to this module's version, never a decompilation input. */
	public record NativeArtifact(String classifier, Path relativeJarPath) implements Serializable {}
}
