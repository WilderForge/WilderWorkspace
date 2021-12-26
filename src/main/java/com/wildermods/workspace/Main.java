package com.wildermods.workspace;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Path;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringUtils;

import com.wildermods.workspace.decompile.DecompileWriteRule;

import cuchaz.enigma.classprovider.ClassProvider;
import cuchaz.enigma.classprovider.ClasspathClassProvider;
import cuchaz.enigma.classprovider.CombiningClassProvider;
import cuchaz.enigma.classprovider.JarClassProvider;

@SuppressWarnings("deprecation")
public class Main {

	private static final ConcurrentHashMap<String, String> EXCLUSIONS = new ConcurrentHashMap<String, String>();
	private static final ConcurrentHashMap<String, String> FORCE_INCLUSIONS = new ConcurrentHashMap<String, String>();
	private static final ConcurrentHashMap<String, WriteRule> WRITE_RULES = new ConcurrentHashMap<String, WriteRule>();
	private static final ConcurrentHashMap<String, Resource> NEW_RESOURCES = new ConcurrentHashMap<String, Resource>();
	public static File binDir;
	
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
	
	static {
		EXCLUSIONS.put("logs", ".*/Wildermyth/logs.*");
		EXCLUSIONS.put("out", ".*/Wildermyth/out.*");
		EXCLUSIONS.put("jre", ".*/Wildermyth/jre.*");
		EXCLUSIONS.put("screenshots", ".*/Wildermyth/screenshots.*");
		EXCLUSIONS.put("feedback", ".*/Wildermyth/feedback.*");
		EXCLUSIONS.put("scratchmods", ".*/Wildermyth/mods/user.*");
		EXCLUSIONS.put("changelog", ".*/Wildermyth/change log.*");
		EXCLUSIONS.put("readme", ".*/Wildermyth/readme\\.txt");
		
		FORCE_INCLUSIONS.put("builtInMods", ".*/Wildermyth/mods/builtIn.*");
		
		WRITE_RULES.put("patchline", new WriteRule(".*/Wildermyth/patchline\\.txt") {
			@Override
			public Throwable write(File source, File dest) {
				System.out.println("adding custom " + dest);
				try {
					FileUtils.writeByteArrayToFile(dest, IOUtils.toByteArray(Main.class.getResourceAsStream("/patchline.txt")), false);
				} catch (IOException e) {
					return e;
				} return null;
			}}
		);
	}
	
	private static final HashSet<File> FILES = new HashSet<File>();
	private static final HashSet<Path> JARS = new HashSet<Path>();
	
	private static final HashMap<String, Object> PROPERTIES = new HashMap<String, Object>();
	
	private static boolean overwriteByDefault = false;
	
	public static void main(String[] args) throws InterruptedException {
		if(args.length == 0) {
			System.err.println("Warning: Calling Main.main(String[]) with no arguments is discouraged. Call UI.main(String[]) instead!");
			UI.main(args);
		}
		else {
			main(InstallationProperties.fromArgs(args));
		}
	}
	
	public static void main(InstallationProperties properties) throws InterruptedException {
		checkJavaVersion();
		if(properties.isValid()) {
			File sourceDir = properties.getSourceDir();
			File workspaceDir = properties.getDestDir();
			GameInfo gameVersion = properties.getGameInfo();
			
			setupWriteRulesAndResources(properties);
			
			System.out.println("Wildermyth version: " + gameVersion);
			
			prepareWorkspace(properties);
			
		}
		else {
			throw new IllegalStateException("Installation properties were invalid!");
		}
	}
	
	private static void checkJavaVersion() {
	    String version = System.getProperty("java.version");
	    if(version.startsWith("1.")) {
	        version = version.substring(2, 3);
	    } else {
	        int dot = version.indexOf(".");
	        if(dot != -1) { version = version.substring(0, dot); }
	    } 
	    int versionNo = Integer.parseInt(version);
	    if(versionNo < 16) {
	    	System.err.println("WilderWorkspace can only run on Java 16 or later, re-run this jar in a Java 16 environment. Don't worry though, the project that wilderworkspace creates will work with Java 8 or later!");
	    	System.exit(-1);
	    }
	}
	
	private static void setupWriteRulesAndResources(InstallationProperties properties) {
		
		overwriteByDefault = properties.overwriteGame();
		
		if(!properties.copySaves()) {
			EXCLUSIONS.put("players", ".*/Wildermyth/players.*");
			EXCLUSIONS.put("backup", ".*/Wildermyth/backup.*");
		}
		else {
			WRITE_RULES.put("overwritePlayers", new ShouldOverwriteWriteRule(properties.overwriteSaves(), PLAYERS));
			WRITE_RULES.put("overwriteBackups", new ShouldOverwriteWriteRule(properties.overwriteSaves(), BACKUPS));
		}
		
		if(!properties.overwriteMods()) {
			EXCLUSIONS.put("mods", ".*/Wildermyth/mods.*");
		}
		else {
			WRITE_RULES.put("overwriteMods", new ShouldOverwriteWriteRule(properties.overwriteMods(), MODS));
		}
		
		if(properties.createGradle()) {
			NEW_RESOURCES.put(".gitignore", new Resource("gitignore", ".gitignore"));
			NEW_RESOURCES.put(".gitattributes", new Resource("gitattributes", ".gitattributes"));
			NEW_RESOURCES.put("build.gradle", new Resource("build.gradle"));
			NEW_RESOURCES.put("gradlew", new Resource("gradlew"));
			NEW_RESOURCES.put("gradlew.bat", new Resource("gradlew.bat"));
			NEW_RESOURCES.put("gradleJar", new Resource("gradle/wrapper/gradle-wrapper", "gradle/wrapper/gradle-wrapper.jar"));
			NEW_RESOURCES.put("gradleProperties", new Resource("gradle/wrapper/gradle-wrapper.properties"));
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
	
	private static void prepareWorkspace(InstallationProperties properties) throws InterruptedException {
		File workspaceDir = properties.getDestDir();
		Iterator<File> files = FileUtils.iterateFilesAndDirs(properties.getSourceDir(), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
		int found = 0;
		int jars = 0;
		int excluded = 0;
		int copied = 0;
		int modified = 0;
		HashMap<File, Throwable> errors = new HashMap<File, Throwable>();
		fileloop:
		while(files.hasNext()) {
			found++;
			File f = files.next();
			exclusions:
			for(String e : EXCLUSIONS.values()) {
				for(String i : FORCE_INCLUSIONS.values()) {
					if(f.getAbsolutePath().matches(i)) {
						System.out.println("Force including " + f + " regardless of exclusion rules.");
						break exclusions;
					}
				}
				if(f.getAbsolutePath().matches(e)) {
					excluded++;
					continue fileloop;
				}
			}
			if(f.getName().endsWith(".jar")) {
				jars++;
				JARS.add(f.toPath());
			}
			FILES.add(f);
			System.out.println(f.getAbsolutePath());
		}
		System.out.println("Found: " + (found) + " total files.\n\nWith" + excluded + " files excluded, and\n" + jars + " jar files to possibly add to this runtime classpath");
		Thread.sleep(3000);
		binDir = workspaceDir;
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
		
		fileLoop:
		for(File f : FILES) {
			boolean isModified = false;
			try {
				File dest = new File(binDir.getAbsolutePath() + getLocalPath(properties.getSourceDir(), f));
				if(!f.isDirectory()) {
					for(WriteRule writeRule : WRITE_RULES.values()) {
						if(writeRule.matches(f)) {
							isModified = true;
							Throwable t = writeRule.write(f, dest);
							if(t != null) {
								errors.put(f, t);
								continue fileLoop;
							}
						}
					}
					if(isModified) {
						copied++;
						modified++;
					}
					else {
						if(dest.exists() && overwriteByDefault) {
							System.out.println("Overwriting " + dest);
							copied++;
							FileUtils.copyFile(f, dest);
						}
						else if(dest.exists()){
							System.out.println("Skipping " + f + " by default because it already exists.");
						}
						else {
							//System.out.println("copying " + f);
							copied++;
							FileUtils.copyFile(f, dest);
						}
						
					}
				}

			} catch (IOException e) {
				throw new IOError(e);
			}
		}
		System.out.println("Copied " + copied + " files to workspace (" + modified + " of which were modified with custom writerules)");
		if(errors.size()!= 0) {
			System.err.println(errors.size() + " files failed to write correctly:");
			for(Entry<File, Throwable> entry : errors.entrySet()) {
				System.err.println("Could not write file " + entry.getKey() + ":");
				entry.getValue().printStackTrace(System.err);
			}
		}
		for(Resource r : NEW_RESOURCES.values()) {
			try {
				r.write(workspaceDir);
			} catch (IOException e) {
				throw new IOError(e);
			}
		}
		HashSet<WriteRule> unmatched = new HashSet<WriteRule>();
		for(WriteRule writeRule : WRITE_RULES.values()) {
			if(!writeRule.matchFound()) {
				unmatched.add(writeRule);
			}
		}
		if(unmatched.size() > 0) {
			System.err.println("WARNING: " + unmatched.size() + " the following WriteRules not match any files:");
		}
		return;
	}
	
	private static String getLocalPath(File gameDir, File file) {
		return StringUtils.replaceOnce(file.getAbsolutePath(), gameDir.getAbsolutePath(), "");
	}
	
	public static ClassProvider getDecompilationClasspath() throws IOException {
		HashSet<ClassProvider> classProviders = new HashSet<ClassProvider>();
		classProviders.add(new ClasspathClassProvider());
		for(Path jarFile : JARS) {
			System.out.println(jarFile + " added to classpath");
			classProviders.add(new JarClassProvider(jarFile));
		}
		return new CombiningClassProvider(classProviders.toArray(new ClassProvider[]{}));
	}
	
}
