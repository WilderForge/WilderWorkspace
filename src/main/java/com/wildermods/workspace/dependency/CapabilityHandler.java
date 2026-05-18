package com.wildermods.workspace.dependency;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wildermods.thrixlvault.exception.VersionParsingException;
import com.wildermods.thrixlvault.utils.version.Version;
import com.wildermods.thrixlvault.utils.version.VersionPredicate;

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

	/**
	 * Source strategy for a version rule.
	 */
	public static class SourceStrategy implements Serializable {
		private static final long serialVersionUID = 7503141011986913752L;
		public final String type;				 // "decompile", "file", "skip"
		public final List<String> exclude;		// version exclusions (simple string matching)
		public final String path;				 // for "file" type, path to source jar (relative to project root)

		public SourceStrategy(String type, List<String> exclude, String path) {
			this.type = type;
			this.exclude = exclude == null ? List.of() : List.copyOf(exclude);
			this.path = path;
		}

		@Override
		public String toString() {
			return "SourceStrategy{" + type + ", exclude=" + exclude + ", path=" + path + "}";
		}
	}

	/**
	 * Container for a version result with its associated source strategy.
	 */
	public static class VersionResult {
		public final Version version;
		public final SourceStrategy sourceStrategy;

		public VersionResult(Version version, SourceStrategy sourceStrategy) {
			this.version = version;
			this.sourceStrategy = sourceStrategy;
		}
	}

	/**
	 * A version rule: knows how to extract the version string and also holds candidate source strategies.
	 */
	public static class VersionRule {
		private final VersionExtractor extractor;
		private final List<SourceStrategy> sourceStrategies; // in order of appearance in JSON

		public VersionRule(VersionExtractor extractor, List<SourceStrategy> sourceStrategies) {
			this.extractor = extractor;
			this.sourceStrategies = sourceStrategies;
		}

		public Optional<VersionResult> extract(Path file, Project project) {
			Optional<String> versionStr = extractor.extract(file);
			if (versionStr.isEmpty() || versionStr.get().isBlank()) {
				return Optional.empty();
			}
			Version version;
			try {
				version = Version.parse(versionStr.get());
			} catch (VersionParsingException e) {
				project.getLogger().warn("Failed to parse version '{}' for {}", versionStr.get(), file);
				throw new AssertionError(e); //fail fast so we don't deploy broken stuff
			}
			// Select the first source strategy that does NOT exclude this version
			SourceStrategy selected = null;
			for (SourceStrategy s : sourceStrategies) {
				boolean excluded = false;
				for (String excl : s.exclude) {
					VersionPredicate exclude;
					try {
						exclude = VersionPredicate.parse(excl);
					} catch (VersionParsingException e) {
						throw new AssertionError(e);
					}
					if (exclude.test(version)) {
						excluded = true;
						break;
					}
				}
				if (!excluded) {
					selected = s;
					break;
				}
			}
			if (selected == null) {
				project.getLogger().warn("No source strategy applicable for version {} of {}", version, file);
				selected = new SourceStrategy("skip", List.of(), null);
			}
			return Optional.of(new VersionResult(version, selected));
		}
	}

	@FunctionalInterface
	public interface VersionExtractor {
		Optional<String> extract(Path file);
	}

	public static class FileAlias {
		public final List<Path> directories;
		public final List<Pattern> namePatterns;
		public final List<VersionRule> versionRules;

		private FileAlias(List<Path> dirs, List<Pattern> patterns, List<VersionRule> rules) {
			this.directories = dirs;
			this.namePatterns = patterns;
			this.versionRules = rules;
		}

		public static FileAlias fromJson(CanonicalModule module, JsonObject json, Project project) {
			Path baseDir = project.getRootDir().toPath();

			List<Path> dirs = json.getAsJsonArray("location").asList().stream()
					.<Path>map(e -> baseDir.resolve(e.getAsString()).normalize())
					.collect(Collectors.toList());

			List<Pattern> patterns = json.getAsJsonArray("name").asList().stream()
					.map(e -> Pattern.compile(e.getAsString()))
					.collect(Collectors.toList());

			List<VersionRule> rules = json.getAsJsonArray("version").asList().stream()
					.map(e -> createVersionRule(module, e.getAsJsonObject(), project))
					.collect(Collectors.toList());

			return new FileAlias(dirs, patterns, rules);
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

		public Optional<VersionResult> extractVersion(Path file, Project project) {
			for (VersionRule rule : versionRules) {
				Optional<VersionResult> result = rule.extract(file, project);
				if (result.isPresent()) {
					return result;
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
	private static VersionRule createVersionRule(CanonicalModule module, JsonObject ruleObj, Project project) {
		String type = ruleObj.get("type").getAsString();
		String value = ruleObj.get("value").getAsString();

		project.getLogger().info(module + ": Creating version rule: " + type + " - " + value);

		// Parse source array
		List<SourceStrategy> sourceStrategies = parseSourceArray(ruleObj, project);

		// Build the appropriate extractor
		VersionExtractor extractor;
		switch (type) {
			case "derived":
				Pattern pattern = Pattern.compile(value);
				extractor = file -> {
					project.getLogger().info("Executing " + module + ": derived - " + value);
					return pattern.matcher(file.getFileName().toString()).results()
							.findFirst().map(m -> m.group(1));
				};
				break;
			case "literal":
				extractor = file -> {
					project.getLogger().info("Executing " + module + ": literal - " + value);
					return Optional.of(value);
				};
				break;
			case "projectVar":
				String varName = value;
				extractor = file -> {
					project.getLogger().info("Executing " + module + ": projectVar - " + varName);
					return Optional.ofNullable(project.findProperty(varName))
							.map(Object::toString);
				};
				break;
			case "method":
				project.getLogger().info("Creating " + module + ": method - " + value);
				extractor = file -> {
					project.getLogger().info("Executing " + module + ": method - " + value);
					try {
						String clazzName = ruleObj.get("class").getAsString();
						Class<?> clazz = Class.forName(clazzName);
						Method m = clazz.getMethod(value);
						m.setAccessible(true);
						return Optional.of((String) m.invoke(null));
					} catch (Exception | LinkageError e) {
						e.printStackTrace();
						return Optional.empty();
					}
				};
				break;
			case "assert":
				project.getLogger().info("Creating " + module + ": assert - " + value);
				extractor = file -> {
					project.getLogger().info("Executing " + module + ": assert - " + value);
					throw new AssertionError(value);
				};
				break;
			default:
				throw new IllegalArgumentException("Unknown version rule type: " + type);
		}
		return new VersionRule(extractor, sourceStrategies);
	}

	private static List<SourceStrategy> parseSourceArray(JsonObject ruleObj, Project project) {
		if (!ruleObj.has("source")) {
			// default: decompile with no exclusions
			return List.of(new SourceStrategy("decompile", List.of(), null));
		}
		JsonArray sourceArr = ruleObj.getAsJsonArray("source");
		List<SourceStrategy> strategies = new ArrayList<>();
		for (JsonElement elem : sourceArr) {
			JsonObject src = elem.getAsJsonObject();
			String sType = src.get("type").getAsString();
			List<String> exclude = new ArrayList<>();
			if (src.has("exclude")) {
				JsonArray exclArr = src.getAsJsonArray("exclude");
				for (JsonElement excl : exclArr) {
					exclude.add(excl.getAsString());
				}
			}
			String path = null;
			if (src.has("path")) {
				path = src.get("path").getAsString();
			}
			strategies.add(new SourceStrategy(sType, exclude, path));
		}
		if (strategies.isEmpty()) {
			// fallback
			strategies.add(new SourceStrategy("decompile", List.of(), null));
		}
		return strategies;
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
					public MethodVisitor visitMethod(int access, String name, String descriptor,
													  String signature, String[] exceptions) {
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