package com.wildermods.workspace.decomp;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import com.wildermods.workspace.util.GradlePrintStreamLogger;

import net.fabricmc.loom.api.decompilers.DecompilationMetadata;

public class WilderWorkspaceDecompiler {

	private final Fernflower ff;
	private final DecompilerBuilder builder;
	private final DecompilationMetadata metaData;
	
	WilderWorkspaceDecompiler(DecompilerBuilder builder) {
		this.builder = builder;
		this.metaData = builder.getMetaData();
		
		final Map<String, Object> options = new HashMap<>(
				Map.of(
					IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1",
					IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1",
					IFernflowerPreferences.DUMP_CODE_LINES, "1",
					IFernflowerPreferences.REMOVE_SYNTHETIC, "1",
					IFernflowerPreferences.LOG_LEVEL, "trace",
					IFernflowerPreferences.THREADS, String.valueOf(metaData.numberOfThreads()),
					IFernflowerPreferences.INDENT_STRING, "\t"
					//IFabricJavadocProvider.PROPERTY_NAME, new WilderWorkspaceJavadocProvider(metaData.javaDocs().toFile())
				)
		);
		
		options.putAll(metaData.options());
		
		IResultSaver saver = new WWThreadSafeResultSaver(
				() -> builder.getDecompDest(), 
				() -> builder.getLinemapDest()
			);
		ff = new Fernflower(saver, options, (GradlePrintStreamLogger)metaData.logger());
		
		for(Path library : metaData.libraries()) {
			ff.addLibrary(library.toFile());
		}
		
		for(Path compiledJar : builder.getJarsToDecomp()) {
			ff.addSource(compiledJar.toFile());
		}
		
	}

	public void decompile() {
		try {
			ff.decompileContext();
		}
		finally {
			ff.clearContext();
		}
	}

}
