package com.wildermods.workspace.wilder;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.wildermods.workspace.Installation;
import com.wildermods.workspace.LocalResource;
import com.wildermods.workspace.Main;
import com.wildermods.workspace.RemoteResource;
import com.wildermods.workspace.ShouldOverwriteWriteRule;
import com.wildermods.workspace.WriteRule;
import com.wildermods.workspace.decompile.DecompileWriteRule;

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
	
	WilderInstallationProperties properties = this.installationProperties;
	File workspaceDir;
	File binDir;
	
	public WilderInstallation(WilderInstallationProperties properties) {
		super(properties);
		workspaceDir = properties.getDestDir();
		binDir = workspaceDir;
		if(properties.createGradle()) {
			binDir = new File(workspaceDir.getAbsolutePath() + "/bin");
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
			@Override
			public Throwable write(Installation installation, File source, File dest) {
				System.out.println("adding custom " + dest);
				try {
					FileUtils.writeByteArrayToFile(dest, IOUtils.toByteArray(Main.class.getResourceAsStream("/patchline.txt")), false);
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
			WRITE_RULES.put("wildermyth", new DecompileWriteRule(".*/Wildermyth/wildermyth\\.jar"));
			WRITE_RULES.put("scratchpad", new DecompileWriteRule(".*/Wildermyth/scratchpad\\.jar"));
			WRITE_RULES.put("server", new DecompileWriteRule(".*/Wildermyth/lib/server-.*\\.jar"));
			WRITE_RULES.put("gameEngine", new DecompileWriteRule(".*/Wildermyth/lib/gameEngine-.*\\.jar"));
			WRITE_RULES.put("fmod", new DecompileWriteRule(".*/Wildermyth/lib/fmod-jni\\.jar"));
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
		
		HashMap<String, RemoteResource> dependencies = declareDependencies();
		
		for(RemoteResource resource : dependencies.values()) {
			NEW_RESOURCES.put(resource.name, resource);
		}
		
	}
	

	@Override
	public HashMap<String, RemoteResource> declareDependencies() {
		JsonArray dependenciesJsonArray = new JsonArray();
		final HashMap<String, RemoteResource> dependencies = new HashMap<String, RemoteResource>();
		
		try {
			dependenciesJsonArray = JsonParser.parseString(new String(IOUtils.resourceToByteArray("/dependencies.json"))).getAsJsonObject().get("dependencies").getAsJsonArray();
		} catch (Throwable t) {
			throw new Error(t);
		}
		
		for(JsonElement dependencyElement : dependenciesJsonArray) {
			RemoteResource resource;
			try {
				resource = new RemoteResource(dependencyElement.getAsJsonObject());
			} catch (IOException e) {
				throw new Error(e);
			}
			dependencies.put(resource.name, resource);
		}
		return dependencies;
	}

	@Override
	public void modifyWriteRules() {
		if(properties.createGradle()) {
			binDir = new File(workspaceDir.getAbsolutePath() + "/bin");
		}
		if(properties.decompile()) {
			((DecompileWriteRule)WRITE_RULES.get("wildermyth")).setOriginCopyDest(new File(binDir.getPath() + "/wildermyth.jar"));
			((DecompileWriteRule)WRITE_RULES.get("scratchpad")).setOriginCopyDest(new File(binDir.getPath() + "/scratchpad.jar"));
			((DecompileWriteRule)WRITE_RULES.get("server")).setOriginCopyDest(new File(binDir.getPath() + "/lib/server-1.0.jar"));
			((DecompileWriteRule)WRITE_RULES.get("gameEngine")).setOriginCopyDest(new File(binDir.getPath() + "/lib/gameEngine-1.0.jar"));
			((DecompileWriteRule)WRITE_RULES.get("fmod")).setOriginCopyDest(new File(binDir.getPath() + "/lib/fmod-jni.jar"));
			//((DecompileWriteRule)WRITE_RULES.get("devvotes-client")).setOriginCopyDest(new File(binDir.getPath() + "/lib/devvotes-client.jar"));
		}
	}

}
