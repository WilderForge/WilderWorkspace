package com.wildermods.workspace.decompile;

import java.nio.file.Path;

import com.wildermods.workspace.ConsumerLogger;
import com.wildermods.workspace.Installation;
import com.wildermods.workspace.WriteRule;

import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.decompilers.LineNumberRemapper;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.FileSystemUtil.Delegate;
import net.fabricmc.loom.util.gradle.ThreadedSimpleProgressLogger;

public class DecompileWriteRule<Decompiler extends LoomDecompiler> extends WriteRule {

	private Decompiler decompiler;
	private Path decompFolder;
	private DecompilationMetadata metaData;
	
	public DecompileWriteRule(Decompiler decompiler, String regex) {
		super(regex);
		this.decompiler = decompiler;
	}
	
	public DecompileWriteRule<Decompiler> setDecompFolder(Path dest) {
		decompFolder = dest;
		return this;
	}
	
	public DecompileWriteRule<Decompiler> setMetaData(DecompilationMetadata metaData) {
		this.metaData = metaData;
		return this;
	}

	@Override
	@SuppressWarnings("rawtypes")
	public Throwable write(Installation installation, Path origin, Path sourceDest) {
		
		if(decompFolder == null) {
			return new NullPointerException("decompFolder was not set");
		}
		
		Path linemapDest = decompFolder.resolve("linemaps").resolve(origin.getFileName() + ".linemap");
		Path decompDest = decompFolder.resolve(origin.getFileName());
		
		System.out.println("Decompiling " + origin.getFileName() + " to " +
		decompDest);
		
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
	
	public void remap(Path linemap, Path jarToRemap, Path remappedJarDest) throws Throwable {
		LineNumberRemapper remapper = new LineNumberRemapper();
		remapper.readMappings(linemap.toFile());
		ThreadedSimpleProgressLogger logger = new ThreadedSimpleProgressLogger(new ConsumerLogger());
		
		try(Delegate in = FileSystemUtil.getJarFileSystem(jarToRemap.toFile(), true);
			Delegate out = FileSystemUtil.getJarFileSystem(remappedJarDest.toFile(), true);) 
		{
			remapper.process(logger, in.get().getPath("/"), out.get().getPath("/"));
		}
	}

}
