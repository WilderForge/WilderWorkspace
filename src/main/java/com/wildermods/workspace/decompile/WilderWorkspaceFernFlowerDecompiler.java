package com.wildermods.workspace.decompile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.decompilers.LoomInternalDecompiler;
import net.fabricmc.loom.decompilers.fernflower.FernflowerLogger;
import net.fabricmc.loom.decompilers.fernflower.ThreadSafeResultSaver;
import net.fabricmc.loom.util.IOStringConsumer;

public final class WilderWorkspaceFernFlowerDecompiler implements LoomDecompiler {

	@Override
	public void decompile(Path compiledJar, Path sourcesDestination, Path linemapDestination, DecompilationMetadata metaData) {
		final Map<String, Object> options = new HashMap<>(
			Map.of(
				IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1",
				IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1",
				IFernflowerPreferences.REMOVE_SYNTHETIC, "1",
				IFernflowerPreferences.LOG_LEVEL, "trace",
				IFernflowerPreferences.THREADS, String.valueOf(metaData.numberOfThreads()),
				IFernflowerPreferences.INDENT_STRING, "\t"
			)
		);
				
		options.putAll(metaData.options());

		IResultSaver saver = new ThreadSafeResultSaver(sourcesDestination::toFile, linemapDestination::toFile);
		Fernflower ff = new Fernflower(WilderWorkspaceFernFlowerDecompiler::getBytecode, saver, options, logger(metaData));

		for (Path library : metaData.libraries()) {
			ff.addLibrary(library.toFile());
		}

		ff.addSource(compiledJar.toFile());
		ff.decompileContext();
		
		

	}

	public static byte[] getBytecode(String externalPath, String internalPath) throws IOException {
		File file = new File(externalPath);
		
		if(internalPath == null) {
			return InterpreterUtil.getBytes(file);
		}
		else {
			try(ZipFile archive = new ZipFile(file)) {
				ZipEntry entry = archive.getEntry(internalPath);
				
				if(entry == null) {
					throw new IOException("Entry not found: " + internalPath);
				}
				
				return InterpreterUtil.getBytes(archive, entry);
			}
		}
	}
	
	private static FernflowerLogger logger(DecompilationMetadata metadata) {
		IOStringConsumer logConsumer = metadata.logger();
		LoomInternalDecompiler.Logger internalLogger = new LoomInternalDecompiler.Logger() {

			@Override
			public void accept(String arg0) throws IOException {
				logConsumer.accept(arg0);
			}

			@Override
			public void error(String arg0) {
				try {
					logConsumer.accept(arg0);
				}
				catch(IOException e) {
					e.printStackTrace();
				}
			}
			
		};
		return new FernflowerLogger(internalLogger);
	}
	
}
