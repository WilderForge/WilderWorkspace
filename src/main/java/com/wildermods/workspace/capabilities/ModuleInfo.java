package com.wildermods.workspace.capabilities;

import java.io.Serializable;
import java.nio.file.Path;

import com.wildermods.thrixlvault.utils.version.Version;
import com.wildermods.workspace.capabilities.CapabilityHandler.SourceStrategy;

public final record ModuleInfo(String group, String artifact, Version version, Path relativeJarPath, Path projectRoot, SourceStrategy sourceStrategy) implements Serializable {}
