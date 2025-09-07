package com.wildermods.workspace.decomp;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.util.IOStringConsumer;

public class DecompilerBuilder {

	private int numberOfThreads = 4;
	private Path javadocs;
	private Path decompDest;
	private Collection<Path> jarsToDecomp = new HashSet<Path>();
	private Collection<Path> libraries = new HashSet<Path>();
	private IOStringConsumer logger;
	private Map<String, String> options = new HashMap<>();
	
	public DecompilerBuilder() {}
	
	public DecompilerBuilder setThreadCount(int threadCount) {
		if(threadCount > 0 && threadCount <= Runtime.getRuntime().availableProcessors()) {
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
	
	public DecompilerBuilder addJarsToDecomp(Path... jars) {
		this.jarsToDecomp.addAll(Arrays.asList(jars));
		return this;
	}
	
	public DecompilerBuilder addLibraries(Path... libraries) {
		this.libraries.addAll(Arrays.asList(libraries));
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
	
	public DecompilationMetadata getMetaData() {
		return new DecompilationMetadata(numberOfThreads, javadocs, libraries, logger, options);
	}
	
	public Collection<Path> getJarsToDecomp() {
		return jarsToDecomp;
	}
	
	public Path getDecompDest() {
		return decompDest.resolve("decomp");
	}
	
	public Path getLinemapDest() {
		return decompDest.resolve("decomp").resolve("linemaps");
	}
	
	public WilderWorkspaceDecompiler build() {
		decompDest.getClass(); //throw if null
		return new WilderWorkspaceDecompiler(this);
	}
}
