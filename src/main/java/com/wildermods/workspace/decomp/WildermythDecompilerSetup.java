package com.wildermods.workspace.decomp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.wildermods.workspace.capabilities.GameProject;
import com.wildermods.workspace.capabilities.ModuleInfo;

import net.fabricmc.loom.decompilers.ClassLineNumbers;
import net.fabricmc.loom.decompilers.LineNumberRemapper;

public class WildermythDecompilerSetup {

	private final DecompilerBuilder builder;
	private final GameProject project;
	private final Map<String, ModuleInfo> modules;

	public WildermythDecompilerSetup(DecompilerBuilder builder, GameProject project, Map<String, ModuleInfo> modules) {
		this.builder = builder;
		this.project = project;
		this.modules = modules;
	}

	public void decompile(Path decompDir) throws IOException {
		// Pass modules and project root to the builder
		builder.setModules(modules, project.getRootDir());
		builder.setDecompDest(decompDir);

		// Optionally add any extra libraries (if needed) – but they are already handled via modules
		// Build and decompile
		WilderWorkspaceDecompiler decompiler = builder.build();
		decompiler.decompile();

		// Remap line numbers (unchanged from your previous version)
		Path linemapDir = decompDir.resolve("decomp").resolve("linemaps");
		if (Files.exists(linemapDir)) {
			Map<String, Path> sourceJars = new java.util.HashMap<>();
			for (ModuleInfo info : modules.values()) {
				if ("decompile".equals(info.sourceStrategy().type)) {
					Path jar = project.getRootDir().resolve(info.relativeJarPath()).normalize();
					sourceJars.put(jar.getFileName().toString(), jar);
				}
			}
			Files.list(linemapDir)
				.filter(p -> p.toString().endsWith(".linemap"))
				.forEach(linemap -> {
					String jarName = StringUtils.removeEnd(linemap.getFileName().toString(), ".linemap");
					Path sourceJar = sourceJars.get(jarName);
					if (sourceJar != null && Files.exists(sourceJar)) {
						project.info("Remapping " + sourceJar);
						try {
							remap(linemap, sourceJar, sourceJar); // overwrite original (as before)
						} catch (Throwable e) {
							throw new RuntimeException("Failed to remap " + jarName, e);
						}
					} else {
						project.warn("No source jar found for linemap: " + jarName);
					}
				});
		} else {
			project.info("No linemap directory found at " + linemapDir);
		}
	}

	private void remap(Path linemap, Path jarToRemap, Path remappedJarDest) throws Throwable {
		project.info("Remapping " + jarToRemap + " to " + remappedJarDest);
		ClassLineNumbers lineNumbers = ClassLineNumbers.readMappings(Files.newBufferedReader(linemap));
		LineNumberRemapper remapper = new LineNumberRemapper(lineNumbers);
		if (Files.notExists(remappedJarDest)) {
			Files.createDirectories(remappedJarDest.getParent());
		}
		remapper.process(jarToRemap, remappedJarDest);
	}
}