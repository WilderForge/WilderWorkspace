package com.wildermods.workspace.tasks;

import org.apache.commons.io.file.PathUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import com.google.gson.JsonObject;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.Checksum;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public abstract class GenNestedMetadataJarsTask extends DefaultTask {

	@OutputDirectory
	public abstract DirectoryProperty getOutputDir();

	@TaskAction
	public void run() throws IOException {
		Path outputDir = getOutputDir().get().getAsFile().toPath();
		PathUtils.delete(outputDir);
		Files.createDirectories(outputDir);

		HashSet<Module> modules = new HashSet<>();

		collectNest("nest", false, modules);
		collectNest("nestTransitive", true, modules);

		for (Module module : modules) {
			String jarName = module.artifact() + "-" + module.version() + ".jar";
			Path jarPath = outputDir.resolve(jarName);

			try (OutputStream os = Files.newOutputStream(jarPath);
			     JarOutputStream jar = new JarOutputStream(os)) {

				JarEntry entry = new JarEntry("fabric.mod.json");
				jar.putNextEntry(entry);

				String json = generateFabricModJson(module);

				jar.write(json.getBytes(StandardCharsets.UTF_8));
				jar.closeEntry();
			}
		}
	}

	private void collectNest(String configName, boolean transitive, Set<Module> out) {
		Configuration config = getProject().getConfigurations().getByName(configName);
		ResolvedConfiguration resolved = config.getResolvedConfiguration();

		if (transitive) {
			for (ResolvedDependency dep : resolved.getFirstLevelModuleDependencies()) {
				collectRecursive(dep, resolved.getResolvedArtifacts(), out);
			}
		} else {
			for (ResolvedArtifact artifact : resolved.getResolvedArtifacts()) {
				if (hasFabricModJson(artifact)) { //skip nested fabric mods, they already have a fabric.mod.json
					continue;
				}
				out.add(toModule(artifact));
			}
		}
	}

	private void collectRecursive(ResolvedDependency dep, Set<ResolvedArtifact> artifacts, Set<Module> out) {
		ResolvedArtifact artifact = findArtifact(dep, artifacts);

		if (artifact != null && !hasFabricModJson(artifact)) {
			out.add(toModule(artifact));
		}

		// Always traverse children
		for (ResolvedDependency child : dep.getChildren()) {
			collectRecursive(child, artifacts, out);
		}
	}
	
	private static ResolvedArtifact findArtifact(ResolvedDependency dep, Set<ResolvedArtifact> artifacts) {
		for (ResolvedArtifact artifact : artifacts) {
			ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
			if (id.getGroup().equals(dep.getModuleGroup()) 
				&& id.getName().equals(dep.getModuleName())
				&& id.getVersion().equals(dep.getModuleVersion())
			){
				return artifact;
			}
		}
		return null;
	}
	
	private static boolean hasFabricModJson(ResolvedArtifact artifact) {
		if(artifact.getFile().getName().endsWith(".jar")) {
			try (JarFile jar = new JarFile(artifact.getFile())) {
				return jar.getEntry("fabric.mod.json") != null;
			} catch (IOException e) {
				throw new RuntimeException("Failed to inspect artifact " + artifact.getFile(), e);
			}
		}
		return false;
	}

	private static Module toModule(ResolvedArtifact artifact) {

		return new Module(
			artifact.getModuleVersion().getId().getGroup(),
			artifact.getName(),
			artifact.getClassifier(),
			artifact.getModuleVersion().getId().getVersion()
		);
	}

	private static String generateFabricModJson(Module module) {
		JsonObject ret = new JsonObject();
		JsonObject custom = new JsonObject();
		custom.addProperty("fabric-loom:generated", true);

		ret.addProperty("schemaVersion", 1);
		ret.addProperty("id", module.toString());
		ret.addProperty("version", module.version());
		ret.addProperty("name", module.toString());
		ret.add("custom", custom);

		return LoomGradlePlugin.GSON.toJson(ret);
	}

	private static final record Module(String group, String artifact, String classifier, String version) {
		@Override
		public String toString() {
			String ret = (group() + "_" + artifact() + classifier())
					.replaceAll("\\.", "_")
					.toLowerCase(Locale.ENGLISH);

			// Fabric Loader can't handle modIds longer than 64 characters
			if (ret.length() > 64) {
				String hash = Checksum.of(ret).sha256().hex();
				ret = ret.substring(0, 50) + hash.substring(0, 14);
			}

			return ret;
		}
		
		@Override
		public String classifier() {
			if(classifier == null) {
				return "";
			}
			return classifier;
		}
	}
}