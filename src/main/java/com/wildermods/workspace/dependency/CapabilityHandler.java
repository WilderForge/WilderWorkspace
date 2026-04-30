package com.wildermods.workspace.dependency;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.gradle.api.Project;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wildermods.thrixlvault.exception.VersionParsingException;
import com.wildermods.thrixlvault.utils.version.Version;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CapabilityHandler {

	public final Map<String, CanonicalModule> modules = new LinkedHashMap<>();

	public CapabilityHandler(Project project) throws IOException {
		this(project, readJsonFromResource("/capabilities.json"));
	}

	public CapabilityHandler(Project project, JsonObject json) {
		for (var entry : json.entrySet()) {
			String canonicalKey = entry.getKey();
			CanonicalModule module = new CanonicalModule(canonicalKey);
			for (JsonElement aliasElem : entry.getValue().getAsJsonArray()) {
				JsonObject aliasObj = aliasElem.getAsJsonObject();
				String type = aliasObj.get("type").getAsString();
				try {
					if ("file".equals(type)) {
						module.fileAliases.add(FileAlias.fromJson(module, aliasObj, project));
					} else if ("maven".equals(type)) {
						module.mavenAliases.addAll(MavenAlias.fromJson(aliasObj));
					} else {
						throw new IllegalArgumentException("Unknown alias type: " + type);
					}
				}
				catch(Throwable t) {
					throw new Error("Could not parse module " + canonicalKey, t);
				}
			}
			modules.put(canonicalKey, module);
		}
	}

	private static JsonObject readJsonFromResource(String path) throws IOException {
		try (var reader = new InputStreamReader(
				CapabilityHandler.class.getResourceAsStream(path))) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}

	// ---------- Public API ----------
	public Optional<CanonicalModule> findModuleForFile(Path file) {
		return modules.values().stream()
				.filter(m -> m.fileAliases.stream().anyMatch(a -> a.matches(file)))
				.findFirst();
	}

	public Optional<CanonicalModule> findModuleForMaven(String coord) {
		return modules.values().stream()
				.filter(m -> m.mavenAliases.contains(coord))
				.findFirst();
	}

	// ---------- Data classes ----------
	public static class CanonicalModule {
		public final String key;
		public final List<FileAlias> fileAliases = new ArrayList<>();
		public final List<String> mavenAliases = new ArrayList<>();

		public CanonicalModule(String key) { this.key = key; }
		public String getGroup() { return key.split(":", 2)[0]; }
		public String getName() { return key.split(":", 2)[1]; }
		public String toString() { return key;}
	}

	public static class FileAlias {
		public final List<Path> directories;
		public final List<Pattern> namePatterns;
		public final List<VersionExtractor> versionExtractors;

		private FileAlias(List<Path> dirs, List<Pattern> patterns, List<VersionExtractor> extractors) {
			this.directories = dirs;
			this.namePatterns = patterns;
			this.versionExtractors = extractors;
		}

		public static FileAlias fromJson(CanonicalModule module, JsonObject json, Project project) {
			Path baseDir = project.getRootDir().toPath();
	
			List<Path> dirs = json.getAsJsonArray("location").asList().stream()
			.<Path>map(e -> baseDir.resolve(e.getAsString()).normalize())
			.collect(Collectors.toList());
	
			List<Pattern> patterns = json.getAsJsonArray("name").asList().stream()
			.map(e -> Pattern.compile(e.getAsString()))
			.collect(Collectors.toList());
	
			List<VersionExtractor> extractors = json.getAsJsonArray("version").asList().stream()
			.map(e -> createVersionExtractor(module, e.getAsJsonObject(), project))
			.collect(Collectors.toList());
	
			return new FileAlias(dirs, patterns, extractors);
		}

		public boolean matches(Path file) {
			if (!Files.isRegularFile(file)) {
				return false;
			}
			String fileName = file.getFileName().toString();
			boolean nameMatches = namePatterns.stream().anyMatch(p -> p.matcher(fileName).matches());
			if (!nameMatches) {
				return false;
			}
			boolean inDirectory = directories.stream().anyMatch(dir -> file.startsWith(dir));
			return inDirectory;
		}

		public Optional<Version> extractVersion(Path file) {
			for (var extractor : versionExtractors) {
				Optional<Version> version;
				if(extractor.extract(file).isPresent()) {
					try {
						version = Optional.of(Version.parse(extractor.extract(file).get()));
					} 
					catch (VersionParsingException e) {
						version = Optional.empty();
					}
					if (version.isPresent()) {
						return version;
					}
				}
			}
			return Optional.empty();
		}
	}

	public static class MavenAlias {
		public static List<String> fromJson(JsonObject json) {
			return json.getAsJsonArray("location").asList().stream()
					.map(JsonElement::getAsString)
					.collect(Collectors.toList());
		}
	}

	// ---------- Version extraction ----------
	@FunctionalInterface
	public interface VersionExtractor {
		Optional<String> extract(Path file);
	}

	private static VersionExtractor createVersionExtractor(CanonicalModule module, JsonObject rule, Project project) {
		String type = rule.get("type").getAsString();
		String value = rule.get("value").getAsString();

		project.getLogger().info(module + ": Version extractor for " + module);
		
		switch (type) {
			case "derived":
				project.getLogger().info("Creating " + module + ": derived - " + value);
				Pattern pattern = Pattern.compile(value);
				return file -> {
					project.getLogger().info("Executing " + module + ": derived - " + value);
					return pattern.matcher(file.getFileName().toString()).results()
						.findFirst().map(m -> m.group(1));
				};
			case "literal":
				project.getLogger().info("Creating " + module + ": literal - " + value);
				return file -> {
					project.getLogger().info("Executing " + module + ": literal - " + value);
					return Optional.of(value);
				};
			case "projectVar":
				project.getLogger().info("Creating " + module + ": projectVar - " + value);
				project.getExtensions().getExtraProperties().getProperties().forEach((key, val) -> {
					project.getLogger().info("Creating [debug] " + module + ": EXTRA-PROPERTIES - KEY:" + key + " - VALUE: " + val);
				});
				return file -> {
					project.getLogger().info("Executing " + module + ": projectVar - " + value);
					return Optional.ofNullable(project.getExtensions().getExtraProperties().get("gameVersion"))
						.map(Object::toString);
				};
			case "method":
				project.getLogger().info("Creating " + module + ": method - " + value);
				return file -> {
					project.getLogger().info("Executing " + module + ": method - " + value);
					try {
						String clazzName = rule.get("class").getAsString();
						
						Class<?> clazz = Class.forName(clazzName);
						Method m = clazz.getMethod(value);
						m.setAccessible(true);
						m.invoke(null);
						return Optional.of((String)m.invoke(null));
					}
					catch(Exception | LinkageError e) {
						e.printStackTrace();
					}
					return Optional.empty();
				};
			case "assert":
				project.getLogger().info("Creating " + module + ": assert - " + value);
				return file -> {
					project.getLogger().info("Executing " + module + ": assert - " + value);
					throw new AssertionError(value);
				};
			default:
				throw new IllegalArgumentException("Unknown version rule: " + type);
		}
	}
	
	private String getTweenVersion(Path jarPath) throws Exception {
		AtomicReference<String> version = new AtomicReference<>();

		try (JarFile jar = new JarFile(jarPath.toFile())) {
			JarEntry entry = jar.getJarEntry("aurelienribon/tweenengine/Tween.class");

			if (entry == null) {
				throw new IOException("Tween.class not found in jar: " + jarPath);
			}

			try (InputStream in = jar.getInputStream(entry)) {
				ClassReader reader = new ClassReader(in);

				reader.accept(new ClassVisitor(Opcodes.ASM9) {
					@Override
					public MethodVisitor visitMethod(
						int access,
						String name,
						String descriptor,
						String signature,
						String[] exceptions
					) {
						if (name.equals("getVersion") && descriptor.equals("()Ljava/lang/String;")) {
							return new MethodVisitor(Opcodes.ASM9) {
								@Override
								public void visitLdcInsn(Object value) {
									if (value instanceof String) {
										version.set((String) value);
									}
								}
							};
						}
						return null;
					}
				}, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
			}
		}

		return version.get();
	}
}