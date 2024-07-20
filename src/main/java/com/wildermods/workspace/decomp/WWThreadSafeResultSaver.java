package com.wildermods.workspace.decomp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipOutputStream;

import net.fabricmc.loom.decompilers.vineflower.ThreadSafeResultSaver;

public class WWThreadSafeResultSaver extends ThreadSafeResultSaver {
	
	private final Supplier<Path> outputDir;
	private final Supplier<Path> lineMapDir;
	
	public WWThreadSafeResultSaver(Supplier<Path> outputDir, Supplier<Path> lineMapDir) {
		super(outputDir.get()::toFile, lineMapDir.get()::toFile);
		this.outputDir = outputDir;
		this.lineMapDir = lineMapDir;
	}

	@Override
	public void createArchive(String path, String archiveName, Manifest manifest) {
		Path outputDir = this.outputDir.get();
		Path lineMapDir = this.lineMapDir.get();
		String key = path + "/" + archiveName;
		File file = outputDir.resolve(archiveName).normalize().toAbsolutePath().toFile();

		try {
			
			if(!Files.exists(outputDir)) {
				Files.createDirectories(outputDir);
			}
			file.createNewFile();
			
			FileOutputStream fos = new FileOutputStream(file);
			ZipOutputStream zos = manifest == null ? new ZipOutputStream(fos) : new JarOutputStream(fos, manifest);
			outputStreams.put(key, zos);
			saveExecutors.put(key, Executors.newSingleThreadExecutor());
		} catch (IOException e) {
			throw new RuntimeException("Unable to create archive: " + file, e);
		}

		if (lineMapDir != null) {
			File lineMapFile = lineMapDir.resolve(archiveName + ".linemap").normalize().toAbsolutePath().toFile();
			try {
				if(!Files.exists(lineMapDir)) {
					Files.createDirectories(lineMapDir);
				}
				lineMapFile.createNewFile();
				lineMapWriter = new PrintWriter(new FileWriter(lineMapFile));
			} catch (IOException e) {
				throw new RuntimeException("Unable to create line mapping file: " + lineMapFile, e);
			}
		}
	}

}
