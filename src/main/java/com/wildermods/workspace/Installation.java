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
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringUtils;

import com.google.gson.JsonArray;

import cuchaz.enigma.classprovider.ClassProvider;
import cuchaz.enigma.classprovider.ClasspathClassProvider;
import cuchaz.enigma.classprovider.CombiningClassProvider;
import cuchaz.enigma.classprovider.JarClassProvider;

public abstract class Installation<I extends InstallationProperties<G>, G extends GameInfo> {
	
	protected final ConcurrentHashMap<String, String> EXCLUSIONS = new ConcurrentHashMap<String, String>();
	protected final ConcurrentHashMap<String, String> FORCE_INCLUSIONS = new ConcurrentHashMap<String, String>();
	protected final ConcurrentHashMap<String, WriteRule> WRITE_RULES = new ConcurrentHashMap<String, WriteRule>();
	protected final ConcurrentHashMap<String, Resource> NEW_RESOURCES = new ConcurrentHashMap<String, Resource>();
	protected JsonArray DEPENDENCIES;
	
	protected final HashSet<File> FILES = new HashSet<File>();
	protected final HashSet<Path> JARS = new HashSet<Path>();
	
	protected final I installationProperties;
	
	public Installation(I properties) {
		this.installationProperties = properties;
	}
	
	protected final void install() throws InterruptedException {
		preCheck();
		if(installationProperties.isValid()) {
			setupWriteRulesAndResources();
			System.out.println(installationProperties.getGameInfo().getName() + " version "  + installationProperties.getGameInfo().getVersion());
			installImpl();
		}
		throw new IllegalStateException("Installation properties were invalid!");
	}

	public void preCheck() {
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
	
	public void setupWriteRulesAndResources() {
		declareExclusions();
		declareWriteRules();
		declareResources();
	}
	
	/**
	 * File that are declared as excluded are not copied
	 */
	public abstract void declareExclusions();
	
	/**
	 * Forced inclusions are copied, even if they are located in a folder that is excluded.
	 */
	public abstract void declareForcedInclusions();
	
	/**
	 * A write rule determines how a file is copied.
	 * 
	 * If a file matches a writerule, then the writerule will execute before the file is copied. This can be used to
	 * modify the file, or perform other actions when a file is copied.
	 * 
	 * @see {@link WriteRule}
	 * @see {@link DecompileWriteRule}
	 * @see {@link ShouldOverwriteWriteRule}
	 */
	public abstract void declareWriteRules();
	
	/**
	 * Declare resources to add that normally don't exist in the vanilla game.
	 */
	public abstract void declareResources();
	
	/**
	 * Create your modded installation here.
	 * 
	 * @throws InterruptedException 
	 */
	public void installImpl() throws InterruptedException {
		File workspaceDir = installationProperties.getDestDir();
		Iterator<File> files = FileUtils.iterateFilesAndDirs(installationProperties.getSourceDir(), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
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
		File binDir = installationProperties.getBinDir();
		
		fileLoop:
		for(File f : FILES) {
			boolean isModified = false;
			try {
				File dest = new File(binDir.getAbsolutePath() + getLocalPath(installationProperties.getSourceDir(), f));
				if(!f.isDirectory()) {
					for(WriteRule writeRule : WRITE_RULES.values()) {
						if(writeRule.matches(f)) {
							isModified = true;
							Throwable t = writeRule.write(this, f, dest);
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
						if(dest.exists() && installationProperties.overwriteGame()) {
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
				r.write(workspaceDir, installationProperties.createGradle());
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
	
	/**
	 * Used to change the directory of declared writerules inside of installImpl.
	 * Useful if you cannot determine some data for a write rule until copy time.
	 */
	public abstract void modifyWriteRules();
	
	public ClassProvider getDecompilationClasspath() throws IOException {
		HashSet<ClassProvider> classProviders = new HashSet<ClassProvider>();
		classProviders.add(new ClasspathClassProvider());
		for(Path jarFile : JARS) {
			System.out.println(jarFile + " added to classpath");
			classProviders.add(new JarClassProvider(jarFile));
		}
		return new CombiningClassProvider(classProviders.toArray(new ClassProvider[]{}));
	}
	
	public final InstallationProperties getInstallationProperties() {
		return installationProperties;
	}
	
	/**
	 * Internal
	 */
	protected static final String getLocalPath(File gameDir, File file) {
		return StringUtils.replaceOnce(file.getAbsolutePath(), gameDir.getAbsolutePath(), "");
	}
	
}
