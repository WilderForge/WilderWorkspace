package com.wildermods.workspace.tasks;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import com.wildermods.workspace.decomp.DecompilerBuilder;
import com.wildermods.workspace.decomp.WilderWorkspaceDecompiler;

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
