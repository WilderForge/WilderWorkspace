package com.wildermods.workspace.decompile;

import java.io.File;
import java.io.IOException;

import com.wildermods.workspace.Installation;

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
	private final Installation installation;
	private final File jar;
	private File decompJar;
	
	public Decompiler(Installation installation, File jar) {
		this.jar = jar;
		this.installation = installation;
	}
	
	public Throwable decompile() {
		try {
			decompJar = getNewOutFile(installation);
			ProgressListener progressListener = new Command.ConsoleProgressListener();
			EnigmaProject project = Enigma.create().openJar(jar.toPath(), installation.getDecompilationClasspath(), progressListener);
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
	
	private static File getNewOutFile(Installation installation) throws IOException {
		File outputDir = new File(installation.getInstallationProperties().getBinDir().getAbsolutePath() + File.separator + "decomp");
		if(!outputDir.exists()) {
			outputDir.mkdirs();
		}
		return outputDir;
	}
	
}