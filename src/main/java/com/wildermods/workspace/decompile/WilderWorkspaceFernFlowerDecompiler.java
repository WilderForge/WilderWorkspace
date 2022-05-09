package com.wildermods.workspace.decompile;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.decompilers.fernflower.FernFlowerUtils;
import net.fabricmc.loom.decompilers.fernflower.FernflowerLogger;
import net.fabricmc.loom.decompilers.fernflower.ThreadSafeResultSaver;

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
		Fernflower ff = new Fernflower(FernFlowerUtils::getBytecode, saver, options, new FernflowerLogger(metaData.logger()));

		for (Path library : metaData.libraries()) {
			ff.addLibrary(library.toFile());
		}

		ff.addSource(compiledJar.toFile());
		ff.decompileContext();

	}

}
