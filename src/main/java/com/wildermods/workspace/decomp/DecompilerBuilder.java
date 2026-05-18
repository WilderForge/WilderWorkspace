package com.wildermods.workspace.decomp;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.wildermods.workspace.WilderWorkspacePluginImpl.ModuleInfo;
import com.wildermods.workspace.dependency.CapabilityHandler.SourceStrategy;

import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.util.IOStringConsumer;

public class DecompilerBuilder {

	private int numberOfThreads = 4;
	private Path javadocs;
	private Path decompDest;
	private final Set<Path> libraries = new HashSet<>();
	private final Set<Path> sources = new HashSet<>(); // internal
	private IOStringConsumer logger;
	private final Map<String, String> options = new HashMap<>();

	// For module‑based configuration
	private Map<String, ModuleInfo> modules;
	private Path projectRoot;

	public DecompilerBuilder() {}

	// Existing setters (threadCount, javadocs, setDecompDest, addLibraries, setLogger, setOption)
	public DecompilerBuilder setThreadCount(int threadCount) {
		if (threadCount > 0 && threadCount <= Runtime.getRuntime().availableProcessors()) {
			numberOfThreads = threadCount;
		}
		return this;
	}

	public DecompilerBuilder setJavadocs(Path javadocs) {
		this.javadocs = javadocs;
		return this;
	}

	public DecompilerBuilder setDecompDest(Path decompDest) {
		this.decompDest = decompDest;
		return this;
	}

	public DecompilerBuilder addLibraries(Path... libs) {
		for (Path lib : libs) {
			libraries.add(lib);
		}
		return this;
	}

	public DecompilerBuilder setLogger(IOStringConsumer logger) {
		this.logger = logger;
		return this;
	}

	public DecompilerBuilder setOption(String key, String value) {
		options.put(key, value);
		return this;
	}

	// New method: supply modules and project root
	public DecompilerBuilder setModules(Map<String, ModuleInfo> modules, Path projectRoot) {
		this.modules = modules;
		this.projectRoot = projectRoot;
		return this;
	}

	// Internal method to add a source JAR (for decompilation)
	private void addSource(Path jar) {
		sources.add(jar);
	}

	public Collection<Path> getSources() {
		return sources;
	}

	public DecompilationMetadata getMetaData() {
		return new DecompilationMetadata(numberOfThreads, javadocs, libraries, logger, options);
	}

	public Path getDecompDest() {
		return decompDest.resolve("decomp");
	}

	public Path getLinemapDest() {
		return decompDest.resolve("decomp").resolve("linemaps");
	}

	public WilderWorkspaceDecompiler build() {
		if (decompDest == null) {
			throw new IllegalStateException("Decomp destination not set");
		}
		// Process modules if supplied
		if (modules != null && projectRoot != null) {
			for (ModuleInfo info : modules.values()) {
				Path jarPath = projectRoot.resolve(info.relativeJarPath()).normalize();
				if (!jarPath.toFile().exists()) {
					continue;
				}
				SourceStrategy strategy = info.sourceStrategy();
				if ("decompile".equals(strategy.type)) {
					addSource(jarPath);
				} else {
					// treat as library for decompilation context
					libraries.add(jarPath);
				}
			}
		}
		// Now create the decompiler instance
		return new WilderWorkspaceDecompiler(this);
	}
}