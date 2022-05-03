package com.wildermods.workspace.wilder;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.commons.io.FileUtils;

import com.wildermods.workspace.Installation;
import com.wildermods.workspace.LocalResource;
import com.wildermods.workspace.ConsumerLogger;
import com.wildermods.workspace.Dependency;
import com.wildermods.workspace.ShouldOverwriteWriteRule;
import com.wildermods.workspace.WriteRule;
import com.wildermods.workspace.decompile.DecompileWriteRule;
import com.wildermods.workspace.decompile.WilderWorkspaceFernFlowerDecompiler;

import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.util.gradle.ThreadedSimpleProgressLogger;

public class WilderInstallation extends Installation<WilderInstallationProperties, WildermythGameInfo> {

	public static final String LOGS = ".*/Wildermyth/logs.*";
	public static final String OUT = ".*/Wildermyth/out.*";
	public static final String JRE = ".*/Wildermyth/jre.*";
	public static final String PLAYERS = ".*/Wildermyth/players.*";
	public static final String SCREENSHOTS = ".*/Wildermyth/screenshots.*";
	public static final String FEEDBACK = ".*/Wildermyth/feedback.*";
	public static final String BACKUPS = ".*/Wildermyth/backup.*";
	public static final String MODS = ".*/Wildermyth/mods.*";
	public static final String SCRATCH_MODS = ".*/Wildermyth/mods/user.*";
	public static final String CHANGELOG = ".*/Wildermyth/change log.*";
	public static final String README = ".*/Wildermyth/readme\\.txt";
	public static final String PATCHLINE = ".*/Wildermyth/patchline\\.txt";
	
	WilderWorkspaceFernFlowerDecompiler decompiler = new WilderWorkspaceFernFlowerDecompiler();
	WilderInstallationProperties properties = this.installationProperties;
	Path workspaceDir;
	Path binDir;
	Path libDir = properties.getSourcePath().resolve("lib");
	DecompilationMetadata metaData;
	{
		Collection<Path> libs = new HashSet<Path>();
		try {
			Files.newDirectoryStream(libDir).forEach((e) -> {
				if(e.toFile().isFile() && e.endsWith(".jar")) {
					libs.add(e);
				}
			});
		} catch (IOException e) {
			throw new IOError(e);
		}
		metaData = new DecompilationMetadata(Runtime.getRuntime().availableProcessors(), null, libs, new ThreadedSimpleProgressLogger(new ConsumerLogger()), Collections.EMPTY_MAP);
	}
	
	public WilderInstallation(WilderInstallationProperties properties) {
		super(properties);
		workspaceDir = properties.getDestPath();
		binDir = workspaceDir;
		if(properties.createGradle()) {
			binDir = workspaceDir.resolve("bin");
		}
	}

	@Override
	public void declareExclusions() {
		EXCLUSIONS.put("logs", ".*/Wildermyth/logs.*");
		EXCLUSIONS.put("out", ".*/Wildermyth/out.*");
		EXCLUSIONS.put("jre", ".*/Wildermyth/jre.*");
		EXCLUSIONS.put("screenshots", ".*/Wildermyth/screenshots.*");
		EXCLUSIONS.put("feedback", ".*/Wildermyth/feedback.*");
		EXCLUSIONS.put("scratchmods", ".*/Wildermyth/mods/user.*");
		EXCLUSIONS.put("changelog", ".*/Wildermyth/change log.*");
		EXCLUSIONS.put("readme", ".*/Wildermyth/readme\\.txt");
		
		if(!properties.copySaves()) {
			EXCLUSIONS.put("players", ".*/Wildermyth/players.*");
			EXCLUSIONS.put("backup", ".*/Wildermyth/backup.*");
		}
		
		if(!properties.overwriteMods()) {
			EXCLUSIONS.put("mods", ".*/Wildermyth/mods.*");
		}
	}

	@Override
	public void declareForcedInclusions() {
		FORCE_INCLUSIONS.put("builtInMods", ".*/Wildermyth/mods/builtIn.*");
	}

	@Override
	public void declareWriteRules() {
		WRITE_RULES.put("patchline", new WriteRule(".*/Wildermyth/patchline\\.txt") {
			@SuppressWarnings("deprecation")
			@Override
			public Throwable write(Installation installation, Path source, Path dest) {
				System.out.println("adding custom " + dest);
				try {
					FileUtils.write(dest.toFile(), "WilderWorkspace " + WilderForgeDependency.WILDER_WORKSPACE.getVersion(), false);
				} catch (IOException e) {
					return e;
				} return null;
			}}
		);
		
		if(properties.copySaves()){
			WRITE_RULES.put("overwritePlayers", new ShouldOverwriteWriteRule(properties.overwriteSaves(), PLAYERS));
			WRITE_RULES.put("overwriteBackups", new ShouldOverwriteWriteRule(properties.overwriteSaves(), BACKUPS));
		}
		
		if(properties.copyMods()){
			WRITE_RULES.put("overwriteMods", new ShouldOverwriteWriteRule(properties.overwriteMods(), MODS));
		}
		
		if(properties.decompile()) {
			WRITE_RULES.put("wildermyth", new DecompileWriteRule(decompiler, ".*/Wildermyth/wildermyth\\.jar"));
			WRITE_RULES.put("scratchpad", new DecompileWriteRule(decompiler, ".*/Wildermyth/scratchpad\\.jar"));
			WRITE_RULES.put("server", new DecompileWriteRule(decompiler, ".*/Wildermyth/lib/server-.*\\.jar"));
			WRITE_RULES.put("gameEngine", new DecompileWriteRule(decompiler, ".*/Wildermyth/lib/gameEngine-.*\\.jar"));
			WRITE_RULES.put("fmod", new DecompileWriteRule(decompiler, ".*/Wildermyth/lib/fmod-jni\\.jar"));
			//WRITE_RULES.put("devvotes-client", new DecompileWriteRule(".*/Wildermyth/lib/devvotes-client\\.jar")); //Crashes Enigma with StackOverflowError
		}
	}

	@Override
	public void declareResources() {
		
		if(properties.createGradle()) {
			NEW_RESOURCES.put(".gitignore", new LocalResource("gitignore", ".gitignore", false));
			NEW_RESOURCES.put(".gitattributes", new LocalResource("gitattributes", ".gitattributes", false));
			NEW_RESOURCES.put("build.gradle", new LocalResource("build.gradle", false));
			NEW_RESOURCES.put("gradlew", new LocalResource("gradlew", false));
			NEW_RESOURCES.put("gradlew.bat", new LocalResource("gradlew.bat", false));
			NEW_RESOURCES.put("gradleJar", new LocalResource("gradle/wrapper/gradle-wrapper", "gradle/wrapper/gradle-wrapper.jar", false));
			NEW_RESOURCES.put("gradleProperties", new LocalResource("gradle/wrapper/gradle-wrapper.properties", false));
		}
		
		HashMap<String, Dependency> dependencies = declareDependencies();
		
		for(Dependency resource : dependencies.values()) {
			NEW_RESOURCES.put(resource.getName(), resource);
		}
		
	}
	

	@Override
	public HashMap<String, Dependency> declareDependencies() {
		final HashMap<String, Dependency> dependencies = new HashMap<String, Dependency>();
		for(WilderForgeDependency dep : WilderForgeDependency.values()) {
			if(!dep.ignored) {
				dependencies.put(dep.getName(), dep);
			}
		}

		return dependencies;
	}

	@Override
	public void modifyWriteRules() {
		if(properties.decompile()) {
			Path decomp = binDir.resolve("decomp");
			Path linemap = decomp.resolve("linemaps");
			linemap.toFile().mkdirs();
			
			((DecompileWriteRule)WRITE_RULES.get("wildermyth")).setSource(binDir.resolve("wildermyth.jar")).setDecompFolder(decomp).setName("wildermyth").setMetaData(metaData);
			((DecompileWriteRule)WRITE_RULES.get("scratchpad")).setSource(binDir.resolve("/scratchpad.jar")).setDecompFolder(decomp).setName("scratchpad").setMetaData(metaData);
			((DecompileWriteRule)WRITE_RULES.get("server")).setSource(binDir.resolve("/lib/server-1.0.jar")).setDecompFolder(decomp).setName("server-1.0").setMetaData(metaData);
			((DecompileWriteRule)WRITE_RULES.get("gameEngine")).setSource(binDir.resolve("/lib/gameEngine-1.0.jar")).setDecompFolder(decomp).setName("gameEngine-1.0").setMetaData(metaData);
			((DecompileWriteRule)WRITE_RULES.get("fmod")).setSource(binDir.resolve("/lib/fmod-jni.jar")).setDecompFolder(decomp).setName("fmod-jni").setMetaData(metaData);
			//((DecompileWriteRule)WRITE_RULES.get("devvotes-client")).setSource(binDir.resolve("/lib/devvotes-client.jar"));
		}
	}

}
