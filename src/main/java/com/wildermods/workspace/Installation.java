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

public abstract class Installation<I extends InstallationProperties<G>, G extends GameInfo> {
	
	protected final ConcurrentHashMap<String, String> EXCLUSIONS = new ConcurrentHashMap<String, String>();
	protected final ConcurrentHashMap<String, String> FORCE_INCLUSIONS = new ConcurrentHashMap<String, String>();
	protected final ConcurrentHashMap<String, WriteRule> WRITE_RULES = new ConcurrentHashMap<String, WriteRule>();
	protected final ConcurrentHashMap<String, Resource> NEW_RESOURCES = new ConcurrentHashMap<String, Resource>();
	protected Dependency[] dependencies;
	
	protected final HashSet<Path> FILES = new HashSet<Path>();
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
		else {
			throw new IllegalStateException("Installation properties were invalid!\n\n");
		}
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
	    if(versionNo < 17) {
	    	System.err.println("WilderWorkspace can only run on Java 17 or later, re-run this jar in a Java 17 environment. Don't worry though, the project that wilderworkspace creates will work with Java 8 or later!");
	    	System.exit(-1);
	    }
	}
	
	public void setupWriteRulesAndResources() {
		declareExclusions();
		declareForcedInclusions();
		declareWriteRules();
		declareResources();
		declareDependencies();
		modifyWriteRules();
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
	 * Declare dependencies that are required for your game, but which cannot be obtained until copy time.
	 * 
	 * @return a HashMap of {@link Dependency}s to download. The generic argument String is the name of the remote resource.
	 */
	public abstract HashMap<String, Dependency> declareDependencies();
	
	/**
	 * Used to change the directory of declared writerules inside of installImpl().
	 * Useful if you cannot determine some data for a write rule until copy time.
	 */
	public abstract void modifyWriteRules();
	
	/**
	 * Create your modded installation here.
	 * 
	 * This default implementation should be enough for most games, but if it isn't, you'll have to create your own implementation.
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
		HashMap<Path, Throwable> errors = new HashMap<Path, Throwable>();
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
			FILES.add(f.toPath());
			System.out.println(f.getAbsolutePath());
		}
		System.out.println("Found: " + (found) + " total files.\n\nWith" + excluded + " files excluded, and\n" + jars + " jar files to possibly add to this runtime classpath");
		Thread.sleep(3000);
		
		fileLoop:
		for(Path p : FILES) {
			File f = p.toFile();
			boolean isModified = false;
			try {

				Path dest = installationProperties.getBinPath().resolve(getLocalPath(installationProperties.getSourcePath(), p));
				
				if(!f.isDirectory()) {
					for(WriteRule writeRule : WRITE_RULES.values()) {
						if(writeRule.matches(p)) {
							isModified = true;
							Throwable t = writeRule.write(this, p, dest);
							if(t != null) {
								errors.put(p, t);
								continue fileLoop;
							}
						}
					}
					if(isModified) {
						copied++;
						modified++;
					}
					else {
						if(dest.toFile().exists() && installationProperties.overwriteGame()) {
							System.out.println("Overwriting " + dest);
							copied++;
							FileUtils.copyFile(f, dest.toFile());
						}
						else if(dest.toFile().exists()){
							System.out.println("Skipping " + dest + " by default because it already exists.");
						}
						else {
							//System.out.println("copying " + f);
							copied++;
							FileUtils.copyFile(f, dest.toFile());
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
			for(Entry<Path, Throwable> entry : errors.entrySet()) {
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
		HashSet<Entry<String, WriteRule>> unmatched = new HashSet<Entry<String, WriteRule>>();
		for(Entry<String, WriteRule> writeRule : WRITE_RULES.entrySet()) {
			if(!writeRule.getValue().matchFound()) {
				unmatched.add(writeRule);
			}
		}
		if(unmatched.size() > 0) {
			System.err.println("WARNING: " + unmatched.size() + " the following WriteRules not match any files:");
			for(Entry<String, WriteRule> wr : unmatched) {
				System.err.println(wr.getKey());
			}
		}
		return;
	}
	
	public final I getInstallationProperties() {
		return installationProperties;
	}
	
	protected static final String getLocalPath(Path gameDir, Path file) {
		return "." + StringUtils.replaceOnce(file.toFile().getAbsolutePath(), gameDir.toFile().getAbsolutePath(), "");
	}
	
}
