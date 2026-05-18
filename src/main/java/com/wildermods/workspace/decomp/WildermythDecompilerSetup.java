package com.wildermods.workspace.decomp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;

import com.wildermods.workspace.WilderWorkspacePluginImpl.ModuleInfo;

import net.fabricmc.loom.decompilers.ClassLineNumbers;
import net.fabricmc.loom.decompilers.LineNumberRemapper;

public class WildermythDecompilerSetup {

	private final DecompilerBuilder builder;
	private final Project project;
	private final Map<String, ModuleInfo> modules;

	public WildermythDecompilerSetup(DecompilerBuilder builder, Project project, Map<String, ModuleInfo> modules) {
		this.builder = builder;
		this.project = project;
		this.modules = modules;
	}

	public void decompile(Path decompDir) throws IOException {
		// Pass modules and project root to the builder
		builder.setModules(modules, project.getRootDir().toPath());
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
					Path jar = project.getRootDir().toPath().resolve(info.relativeJarPath()).normalize();
					sourceJars.put(jar.getFileName().toString(), jar);
				}
			}
			Files.list(linemapDir)
				.filter(p -> p.toString().endsWith(".linemap"))
				.forEach(linemap -> {
					String jarName = StringUtils.removeEnd(linemap.getFileName().toString(), ".linemap");
					Path sourceJar = sourceJars.get(jarName);
					if (sourceJar != null && Files.exists(sourceJar)) {
						project.getLogger().info("Remapping " + sourceJar);
						try {
							remap(linemap, sourceJar, sourceJar); // overwrite original (as before)
						} catch (Throwable e) {
							throw new RuntimeException("Failed to remap " + jarName, e);
						}
					} else {
						project.getLogger().warn("No source jar found for linemap: " + jarName);
					}
				});
		} else {
			project.getLogger().info("No linemap directory found at " + linemapDir);
		}
	}

	private void remap(Path linemap, Path jarToRemap, Path remappedJarDest) throws Throwable {
		project.getLogger().info("Remapping " + jarToRemap + " to " + remappedJarDest);
		ClassLineNumbers lineNumbers = ClassLineNumbers.readMappings(Files.newBufferedReader(linemap));
		LineNumberRemapper remapper = new LineNumberRemapper(lineNumbers);
		if (Files.notExists(remappedJarDest)) {
			Files.createDirectories(remappedJarDest.getParent());
		}
		remapper.process(jarToRemap, remappedJarDest);
	}
}