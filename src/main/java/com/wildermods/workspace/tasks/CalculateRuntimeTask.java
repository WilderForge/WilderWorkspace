package com.wildermods.workspace.tasks;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

@DisableCachingByDefault(because = "Projects depend on dynamic classpath")
public class CalculateRuntimeTask extends DefaultTask {

	@OutputFile
	public Path getArgsFile() {
		return getProject().getBuildDir().toPath().resolve("runArgs.properties");
	}

	@TaskAction
	public void generate() throws IOException {
		Properties props = new Properties();

		String knotClasspath = (String) getProject().getExtensions().getExtraProperties().get("knotClasspath");
		String providerClasspath = (String) getProject().getExtensions().getExtraProperties().get("providerClasspath");

		props.setProperty("knotClasspath", knotClasspath);
		props.setProperty("providerClasspath", providerClasspath);

		List<String> vmArgs = new ArrayList<>();
		vmArgs.add("-Dmixin.debug=true");
		vmArgs.add("-Dfabric.development=true");

		if (!knotClasspath.isEmpty()) {
			vmArgs.add("-Dknot.class.path=" + knotClasspath);
		}

		// Scan JARs and the project's fabric.mod.json for forceOpens/forceExports
		Set<Path> pathsToScan = new LinkedHashSet<>();
		if (!knotClasspath.isEmpty()) {
			for (String entry : knotClasspath.split(File.pathSeparator)) {
				Path p = Paths.get(entry);
				if (Files.exists(p) && !Files.isDirectory(p) && p.toString().endsWith(".jar")) {
					pathsToScan.add(p);
				}
			}
		}
		if (!providerClasspath.isEmpty()) {
			for (String entry : providerClasspath.split(File.pathSeparator)) {
				Path p = Paths.get(entry);
				if (Files.exists(p) && !Files.isDirectory(p) && p.toString().endsWith(".jar")) {
					pathsToScan.add(p);
				}
			}
		}

		// Also the project's own fabric.mod.json
		Path projectJson = getProject().getProjectDir().toPath().resolve("src/main/resources/fabric.mod.json");
		if (Files.exists(projectJson)) {
			pathsToScan.add(projectJson);
		}

		Map<String, List<String>> opens = new LinkedHashMap<>();
		Map<String, List<String>> exports = new LinkedHashMap<>();
		for (Path p : pathsToScan) {
			if (p.toString().endsWith("fabric.mod.json") && Files.isRegularFile(p)) {
				try (Reader reader = Files.newBufferedReader(p)) {
					JsonObject json = new Gson().fromJson(reader, JsonObject.class);
					extractForceEntries(json, opens, exports);
				} catch (Exception e) {
					getLogger().warn("Failed to parse " + p, e);
				}
				continue;
			}
			if (!Files.isRegularFile(p))
				continue;
			try (JarFile jar = new JarFile(p.toFile())) {
				ZipEntry entry = jar.getEntry("fabric.mod.json");
				if (entry != null) {
					try (InputStream is = jar.getInputStream(entry); Reader reader = new InputStreamReader(is)) {
						JsonObject json = new Gson().fromJson(reader, JsonObject.class);
						extractForceEntries(json, opens, exports);
					}
				}
			} catch (IOException e) {
				getLogger().warn("Failed to open JAR: " + p, e);
			}
		}

		for (Map.Entry<String, List<String>> e : opens.entrySet()) {
			String target = e.getKey();
			for (String pkg : e.getValue()) {
				vmArgs.add("--add-opens " + pkg + "=" + target);
			}
		}
		for (Map.Entry<String, List<String>> e : exports.entrySet()) {
			String target = e.getKey();
			for (String pkg : e.getValue()) {
				vmArgs.add("--add-exports " + pkg + "=" + target);
			}
		}

		props.setProperty("vmArgs", String.join(" ", vmArgs));

		// Write to file
		Path argsFile = getArgsFile();
		Files.createDirectories(argsFile.getParent());
		try (OutputStream out = Files.newOutputStream(argsFile)) {
			props.store(out, "Run arguments for game launch");
		}
		getLogger().info("Run arguments written to " + argsFile);
	}

	private void extractForceEntries(JsonObject json, Map<String, List<String>> opens, Map<String, List<String>> exports) {
		JsonObject custom = json.getAsJsonObject("custom");
		if (custom == null)
			return;
		JsonObject forceOpens = custom.getAsJsonObject("forceOpens");
		if (forceOpens != null) {
			for (String key : forceOpens.keySet()) {
				var packages = forceOpens.getAsJsonArray(key);
				if (packages != null) {
					List<String> list = opens.computeIfAbsent(key, k -> new ArrayList<>());
					for (var elem : packages) {
						list.add(elem.getAsString());
					}
				}
			}
		}
		JsonObject forceExports = custom.getAsJsonObject("forceExports");
		if (forceExports != null) {
			for (String key : forceExports.keySet()) {
				var packages = forceExports.getAsJsonArray(key);
				if (packages != null) {
					List<String> list = exports.computeIfAbsent(key, k -> new ArrayList<>());
					for (var elem : packages) {
						list.add(elem.getAsString());
					}
				}
			}
		}
	}

}
