package com.wildermods.workspace.decomp;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import net.fabricmc.loom.api.decompilers.DecompilationMetadata;

public class WilderWorkspaceDecompiler {

	private final Fernflower ff;

	WilderWorkspaceDecompiler(DecompilerBuilder builder) {
		DecompilationMetadata metaData = builder.getMetaData();
		Path decompDest = builder.getDecompDest();
		Path linemapDest = builder.getLinemapDest();

		final Map<String, Object> options = new HashMap<>(Map.of(
			IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1",
			IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1",
			IFernflowerPreferences.DUMP_CODE_LINES, "1",
			IFernflowerPreferences.REMOVE_SYNTHETIC, "1",
			IFernflowerPreferences.LOG_LEVEL, "trace",
			IFernflowerPreferences.THREADS, String.valueOf(metaData.numberOfThreads()),
			IFernflowerPreferences.INDENT_STRING, "\t"
		));
		options.putAll(metaData.options());

		IResultSaver saver = new WWThreadSafeResultSaver(
				() -> decompDest,
				() -> linemapDest
		);
		ff = new Fernflower(saver, options, (IFernflowerLogger) metaData.logger());

		// Add libraries
		for (Path library : metaData.libraries()) {
			ff.addLibrary(library.toFile());
		}

		// Add sources to decompile
		for (Path source : builder.getSources()) {
			ff.addSource(source.toFile());
		}
	}

	public void decompile() {
		try {
			ff.decompileContext();
		} finally {
			ff.clearContext();
		}
	}
}