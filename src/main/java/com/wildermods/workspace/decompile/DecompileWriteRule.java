package com.wildermods.workspace.decompile;

import java.nio.file.Path;

import com.wildermods.workspace.ConsumerLogger;
import com.wildermods.workspace.Installation;
import com.wildermods.workspace.WriteRule;

import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.decompilers.LineNumberRemapper;
import net.fabricmc.loom.util.gradle.ThreadedSimpleProgressLogger;

public class DecompileWriteRule<Decompiler extends LoomDecompiler> extends WriteRule {

	private Decompiler decompiler;
	private Path source;
	private Path decompFolder;
	private String name;
	private DecompilationMetadata metaData;
	
	public DecompileWriteRule(Decompiler decompiler, String regex) {
		super(regex);
		this.decompiler = decompiler;
	}
	
	public DecompileWriteRule<Decompiler> setSource(Path source) {
		this.source = source;
		return this;
	}
	
	public DecompileWriteRule<Decompiler> setDecompFolder(Path dest) {
		decompFolder = dest;
		return this;
	}
	
	public DecompileWriteRule<Decompiler> setName(String name) {
		this.name = name;
		return this;
	}
	
	public DecompileWriteRule<Decompiler> setMetaData(DecompilationMetadata metaData) {
		this.metaData = metaData;
		return this;
	}

	@Override
	@SuppressWarnings("rawtypes")
	public Throwable write(Installation installation, Path origin, Path sourceDest) {
		
		Path linemapDest = decompFolder.resolve("linemaps").resolve(origin.getFileName() + ".linemap");
		Path decompDest = decompFolder.resolve(origin.getFileName());
		
		System.out.println("Decompiling " + origin.getFileName() + " to " +
		sourceDest);

		
		if(source == null) {
			return new NullPointerException("source was not set");
		}
		
		if(decompFolder == null) {
			return new NullPointerException("decompFolder was not set");
		}
		
		if(name == null) {
			return new NullPointerException("name was not set");
		}
		
		if(metaData == null) {
			return new NullPointerException("metaData was not set");
		}
		
		try {
			decompiler.decompile(origin, decompDest, linemapDest, metaData);
			System.out.println("Finished Decompiling... Creating remapped jar in " + sourceDest);
			remap(linemapDest, origin, sourceDest);
		}
		catch(Throwable t) {
			return t;
		}

		return null;
	}
	
	public void remap(Path linemap, Path jarToRemap, Path remappedJarDest) {
		LineNumberRemapper remapper = new LineNumberRemapper();
		remapper.readMappings(linemap.toFile());
		try {
			ThreadedSimpleProgressLogger logger = new ThreadedSimpleProgressLogger(new ConsumerLogger());
			remapper.process(logger, jarToRemap, remappedJarDest);
		}
		catch(Throwable t) {
			throw new Error(t);
		}
	}

}
