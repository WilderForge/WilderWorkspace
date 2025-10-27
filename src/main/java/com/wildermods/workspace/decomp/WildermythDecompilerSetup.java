package com.wildermods.workspace.decomp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;

import com.wildermods.workspace.GameJars;
import com.wildermods.workspace.util.FileHelper;

import net.fabricmc.loom.decompilers.ClassLineNumbers;
import net.fabricmc.loom.decompilers.LineNumberRemapper;

public class WildermythDecompilerSetup {

	private final DecompilerBuilder builder;
	
	public WildermythDecompilerSetup(DecompilerBuilder builder) {
		this.builder = builder;
	}
	
	public void decompile(Path compiledDir, Path decompDir) throws IOException {
		DecompilerBuilder b = builder;
		b.setDecompDest(decompDir);
		HashMap<String, Path> compiledJars = new HashMap<String, Path>();
		HashMap<String, Path> decompiledJarDests = new HashMap<String, Path>();
		
		/*
		 * Add jars to decompile
		 */
		Files.walkFileTree(compiledDir, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				//the only files we want to decompile are located in the '.', './lib', and "./unmapped" directories
				if(attrs.isSymbolicLink() || (
					!dir.getFileName().toString().equals("lib") && 
					!dir.equals(compiledDir) && 
					!dir.getFileName().toString().equals("unmapped"))) { 
						return FileVisitResult.SKIP_SUBTREE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if(FileHelper.shouldBeRemapped(file)) {
					System.out.println("Adding " + file.toAbsolutePath().normalize().toString() + " as input for the decompiler.");
					compiledJars.put(file.getFileName().toString(), file);
					decompiledJarDests.put(file.getFileName().toString(), compiledDir.resolve(GameJars.fromPath(file).getPath()));
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
				//the only files we want to add as libraries are located in the '.' and './lib'
				if(attrs.isSymbolicLink() || (
						!dir.getFileName().toString().equals("lib") && 
						!dir.equals(compiledDir) && 
						!dir.getFileName().toString().equals("unmapped"))) { 
							return FileVisitResult.SKIP_SUBTREE;
					}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if(FileHelper.shouldBeRemapped(file)) {
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
		
		Path linemapDir = decompDir.resolve("decomp").resolve("linemaps");
		if(Files.exists(linemapDir)) {
			Files.walkFileTree(linemapDir, new SimpleFileVisitor<Path>() {
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
							remap(linemap, unmappedJar, decompiledJarDests.get(jarName));
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
	
    /**
     * Remaps the line numbers of the specified JAR file using the provided line map.
     * 
     * @param linemap the path to the line map file
     * @param jarToRemap the path to the JAR file to remap
     * @param remappedJarDest the destination path for the remapped JAR file
     * @throws Throwable if an error occurs during remapping
     */
	private void remap(Path linemap, Path jarToRemap, Path remappedJarDest) throws Throwable {
		System.out.println("Remapping " + jarToRemap + " to " + remappedJarDest);
		ClassLineNumbers lineNumbers = ClassLineNumbers.readMappings(Files.newBufferedReader(linemap));
		LineNumberRemapper remapper = new LineNumberRemapper(lineNumbers);
		if(Files.notExists(remappedJarDest)) {
			Files.createDirectories(remappedJarDest.getParent());
		}
		remapper.process(jarToRemap, remappedJarDest);
	}
	
}
