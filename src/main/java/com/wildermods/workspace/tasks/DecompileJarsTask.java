package com.wildermods.workspace.tasks;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import com.wildermods.workspace.GameJars;
import com.wildermods.workspace.decomp.DecompilerBuilder;
import com.wildermods.workspace.decomp.WilderWorkspaceDecompiler;

import net.fabricmc.loom.decompilers.ClassLineNumbers;
import net.fabricmc.loom.decompilers.LineNumberRemapper;

public class DecompileJarsTask extends DefaultTask {

	@Input
	private String compiledDir;
	
	@Input
	private String decompDir;
	
	@TaskAction
	public void decompile() throws IOException {

		Path compiledDir = Path.of(this.compiledDir);
		DecompilerBuilder b = new DecompilerBuilder(this);
		b.setDecompDest(Path.of(decompDir));
		HashMap<String, Path> compiledJars = new HashMap<String, Path>();
		HashMap<String, Path> lineMappedJarDests = new HashMap<String, Path>();
		
		/*
		 * Add jars to decompile
		 */
		Files.walkFileTree(compiledDir, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				if(attrs.isSymbolicLink() || (!dir.getFileName().toString().equals("lib") && !dir.equals(compiledDir))) { //the only files we want to decompile are located in the '.' and './lib'
					return FileVisitResult.SKIP_SUBTREE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				switch(file.getFileName().toString()) { //We only want to decompile these 5 files.
					case "devvotes-client.jar":
					case "gameEngine-1.0.jar":
					case "server-1.0.jar":
					case "scratchpad.jar":
					case "wildermyth.jar":
						System.out.println("Adding " + file.toAbsolutePath().normalize().toString() + " as input for the decompiler.");
						compiledJars.put(file.getFileName().toString(), file);
						lineMappedJarDests.put(file.getFileName().toString(), compiledDir.resolve(GameJars.fromPath(file).getPath()));
						b.addJarsToDecomp(file.normalize().toAbsolutePath());
						return FileVisitResult.CONTINUE;
				}
				return FileVisitResult.CONTINUE;
			}
			
		});
		
		/*
		 * Add remaining jars as libraries
		 */
		Files.walkFileTree(compiledDir, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				if(attrs.isSymbolicLink() || (!dir.getFileName().toString().equals("lib") && !dir.equals(compiledDir))) { //the only files we want to add as libraries are located in the '.' and './lib'
					return FileVisitResult.SKIP_SUBTREE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				switch(file.getFileName().toString()) { //want every jar file except these 5 files.
					case "devvotes-client.jar":
					case "gameEngine-1.0.jar":
					case "server-1.0.jar":
					case "scratchpad.jar":
					case "wildermyth.jar":
						System.out.println("Skipping " + file.toAbsolutePath().normalize().toString() + " as library for the decompiler.");
						return FileVisitResult.CONTINUE;
				}
				System.out.println("Adding " + file.toAbsolutePath().normalize().toString() + " as library for the decompiler.");
				b.addLibraries(file.normalize().toAbsolutePath());
				return FileVisitResult.CONTINUE;
			}
			
		});
		
		WilderWorkspaceDecompiler decompiler = b.build();
		decompiler.decompile();
		
		Path linemapDir = Path.of(decompDir).resolve("linemaps");
		if(Files.exists(linemapDir)) {
			Files.walkFileTree(Path.of(decompDir).resolve("linemaps"), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					if(!dir.equals(linemapDir)) { //there are no subdirectories we want to visit
						return FileVisitResult.SKIP_SUBTREE;
					}
					return FileVisitResult.CONTINUE;
				}
				
				@Override
				public FileVisitResult visitFile(Path linemap, BasicFileAttributes attrs) throws IOException {
					System.out.println("Found linemap " + linemap.getFileName());
					String jarName = StringUtils.removeEnd(linemap.getFileName().toString(), ".linemap");
					Path unmappedJar = compiledJars.get(jarName);
					if(Files.exists(unmappedJar)) {
						System.out.println("Found jar to remap: " + unmappedJar.getFileName());
						try {
							remap(linemap, unmappedJar, lineMappedJarDests.get(jarName));
						} catch (Throwable e) {
							throw new IOException(e);
						}
					}
					else {
						throw new FileNotFoundException(unmappedJar.normalize().toAbsolutePath().toString());
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
		else {
			System.out.println("Did not find linemap dir at " + linemapDir);
		}

		
	}
	
	private void remap(Path linemap, Path jarToRemap, Path remappedJarDest) throws Throwable {
		System.out.println("Remapping " + jarToRemap + " to " + remappedJarDest);
		ClassLineNumbers lineNumbers = ClassLineNumbers.readMappings(linemap);
		LineNumberRemapper remapper = new LineNumberRemapper(lineNumbers);
		if(Files.notExists(remappedJarDest)) {
			//Files.createFile(remappedJarDest);
		}
		remapper.process(jarToRemap, remappedJarDest);
	}

	public String getCompiledDir() {
		return compiledDir;
	}

	public void setCompiledDir(String compiledDir) {
		this.compiledDir = compiledDir;
	}

	public String getDecompDir() {
		return decompDir;
	}

	public void setDecompDir(String decompDir) {
		this.decompDir = decompDir;
	}
	
}
