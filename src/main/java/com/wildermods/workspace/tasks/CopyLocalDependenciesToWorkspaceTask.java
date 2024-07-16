package com.wildermods.workspace.tasks;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.apache.commons.io.file.PathUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.impldep.org.apache.commons.lang.NotImplementedException;

import com.wildermods.workspace.util.OS;

public class CopyLocalDependenciesToWorkspaceTask extends DefaultTask {
	
	private static final Logger LOGGER = Logging.getLogger(CopyLocalDependenciesToWorkspaceTask.class);
	
	@Input
	private String platform = Platform.steam.name();
	
	@Input
	private String patchline = getProject().getName() + " " + getProject().getVersion();
	
	@Input
	private String destDir = getProject().relativePath("bin");
	
	@Input
	private String decompDir = Path.of(destDir).resolve("decomp").toString();
	
	public static enum Platform {
		
		steam(() -> {return OS.getSteamDefaultDirectory().resolve("common").resolve("Wildermyth");}),
		epic(unknownPlatformLocation("epic")),
		itch(unknownPlatformLocation("itch")),
		gog(unknownPlatformLocation("gog")),
		filesystem(() -> {return null;});
		
		private Callable<Path> dir;
		
		private Platform (Callable<Path> dir) {
			this.dir = dir;
		}
		
		private Path getDefaultInstallDirectory() throws Exception {
			return dir.call();
		}
		
		public static Platform fromString(String input) {
			for(Platform platform : Platform.values()) {
				if(platform.name().equals(input.toLowerCase())) {
					return platform;
				}
			}
			return filesystem;
		}
	}
	
	public static Callable<Path> unknownPlatformLocation(String platform) {
		return (() -> {throw new NotImplementedException("I don't know where the default install directory for Wildermyth is for the " + platform + " platform. Submit a pull request or input a raw path to the installation location.");});
	}
	
	@TaskAction
	public void copyDependencies() throws IOException {
		final Path destDir = Path.of(this.destDir).toAbsolutePath().normalize();
		try {
			Platform selectedPlatform = Platform.fromString(platform);
			LOGGER.info("Platform: " + platform);
			Path installDir;
			if(selectedPlatform != Platform.filesystem) {
				installDir = selectedPlatform.getDefaultInstallDirectory();
				LOGGER.info("Using default " + selectedPlatform + " install for " + OS.getOS() + ", located at " + installDir);
			}
			else {
				installDir = Path.of(platform);
				LOGGER.info("Using custom Wildermyth install located at " + installDir);
			}
			
			if(!Files.exists(installDir)) {
				throw new FileNotFoundException(installDir.toAbsolutePath().normalize().toString());
			}
			if(!Files.isDirectory(installDir)) {
				throw new NotDirectoryException(installDir.toAbsolutePath().normalize().toString());
			}
			
			PathUtils.copyDirectory(installDir.resolve("assets"), destDir.resolve("assets"));
			
			
		}
		catch(Exception e) {
			RuntimeException e2 = new RuntimeException("Failed to copy dependencies.", e);
			LOGGER.error("Failed to copy dependencies.", e2);
		}
	}
	
	private static void copyDirs(Path source, Path dest, String... sub) {
		for(String s : sub) {
			Path from = source.resolve(s);
			if(Files.exists(from)) {
				
			}
		}
	}
	
	public String getPlatform() {
		return platform;
	}
	
	public String getPatchline() {
		return patchline;
	}
	
	public String getDestDir() {
		return destDir;
	}
	
	public String getDecompDir() {
		return decompDir;
	}
	
}
