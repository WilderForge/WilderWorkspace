package com.wildermods.workspace.decompile;

import java.io.File;
import java.io.IOException;

import com.wildermods.workspace.Main;

import cuchaz.enigma.Enigma;
import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.EnigmaProject.DecompileErrorStrategy;
import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.classprovider.ClassProvider;
import cuchaz.enigma.command.Command;
import cuchaz.enigma.source.DecompilerService;
import cuchaz.enigma.source.Decompilers;
import cuchaz.enigma.source.SourceSettings;

public class Decompiler {
	static final Enigma project = Enigma.create();
	private final File jar;
	private File decompJar;
	
	public Decompiler(File jar) {
		this.jar = jar;
	}
	
	public Throwable decompile() {
		try {
			decompJar = getNewOutFile(decompJar);
			ProgressListener progressListener = new Command.ConsoleProgressListener();
			EnigmaProject project = Enigma.create().openJar(jar.toPath(), Main.getDecompilationClasspath(), progressListener);
			project.exportRemappedJar(progressListener).decompile(progressListener, new DecompilerService() {
				@Override
				public cuchaz.enigma.source.Decompiler create(ClassProvider classProvider, SourceSettings settings) {
					return Decompilers.CFR.create(classProvider, settings);
				}}, DecompileErrorStrategy.PROPAGATE).write(decompJar.toPath(), progressListener);
			return null;
		}
		catch(Throwable t) {
			return t;
		}
	}
	
	private static File getNewOutFile(File oldOutDir) throws IOException {
		File outputDir = new File(Main.binDir.getAbsolutePath() + File.separator + "decomp");
		if(!outputDir.exists()) {
			outputDir.mkdirs();
		}
		return outputDir;
	}
	
}