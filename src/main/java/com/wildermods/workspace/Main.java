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

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

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
	private static final ConcurrentHashMap<String, WriteRule> WRITE_RULES = new ConcurrentHashMap<String, WriteRule>();
	private static final ConcurrentHashMap<String, Resource> NEW_RESOURCES = new ConcurrentHashMap<String, Resource>();
	public static File binDir;
	
	static {
		EXCLUSIONS.put("logs", ".*/Wildermyth/logs.*");
		EXCLUSIONS.put("out", ".*/Wildermyth/out.*");
		EXCLUSIONS.put("jre", ".*/Wildermyth/jre.*");
		EXCLUSIONS.put("players", ".*/Wildermyth/players.*");
		EXCLUSIONS.put("screenshots", ".*/Wildermyth/screenshots.*");
		EXCLUSIONS.put("feedback", ".*/Wildermyth/feedback.*");
		EXCLUSIONS.put("backup", ".*/Wildermyth/backup.*");
		EXCLUSIONS.put("scratchmods", ".*/Wildermyth/mods/user.*");
		EXCLUSIONS.put("changelog", ".*/Wildermyth/change log.*");
		EXCLUSIONS.put("readme", ".*/Wildermyth/readme\\.txt");
		
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
		WRITE_RULES.put("wildermyth", new DecompileWriteRule(".*/Wildermyth/wildermyth\\.jar"));
		WRITE_RULES.put("scratchpad", new DecompileWriteRule(".*/Wildermyth/scratchpad\\.jar"));
		WRITE_RULES.put("server", new DecompileWriteRule(".*/Wildermyth/lib/server-.*\\.jar"));
		WRITE_RULES.put("gameEngine", new DecompileWriteRule(".*/Wildermyth/lib/gameEngine-.*\\.jar"));
		//WRITE_RULES.put("devvotes-client", new DecompileWriteRule(".*/Wildermyth/lib/devvotes-client\\.jar")); //Crashes Enigma with StackOverflowError
		
		NEW_RESOURCES.put(".gitignore", new Resource("gitignore", ".gitignore"));
		NEW_RESOURCES.put(".gitattributes", new Resource("gitattributes", ".gitattributes"));
		NEW_RESOURCES.put("build.gradle", new Resource("build.gradle"));
		NEW_RESOURCES.put("gradlew", new Resource("gradlew"));
		NEW_RESOURCES.put("gradlew.bat", new Resource("gradlew.bat"));
		NEW_RESOURCES.put("gradleJar", new Resource("gradle/wrapper/gradle-wrapper", "gradle/wrapper/gradle-wrapper.jar"));
		NEW_RESOURCES.put("gradleProperties", new Resource("gradle/wrapper/gradle-wrapper.properties"));
	}
	
	private static final HashSet<File> FILES = new HashSet<File>();
	private static final HashSet<Path> JARS = new HashSet<Path>();
	
	public static void main(String[] args) throws Throwable {
		
		checkJavaVersion();
		
		System.out.println(Runtime.version());
		if(!confirm("Select the root directory of your Wildermyth installation.\nFiles will be copied and extracted from here to create a gradle workspace.", "WilderWorkspace: Select Game Location")) {
			cancel();
		}
		
		GameInfo gameInfo = selectRootInstallation();
		if(gameInfo.getVersion() == Version.NO_VERSION) {
			if(!confirm("No wildermyth version detected in " + gameInfo.getGameDir() + "\n\n Continue anyway?", "Warning")) {
				cancel();
			}
		}
		
		if(!confirm("Select the root directory for your gradle workspace.\nFiles will be copied here.\nExisting files will be overwritten if they have the same name.\n\n" + "Wildermyth version: " + gameInfo.getVersion(), "WilderWorkspace: Select Workspace Location")) {
			cancel();
		}
		
		File workspaceDir = selectWorkspaceDirectory();
		
		prepareWorkspace(gameInfo, workspaceDir);
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
	
	private static GameInfo selectRootInstallation() {
		JFileChooser gameDirChooser = new JFileChooser();
		JFrame frame = new JFrame();
		gameDirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		gameDirChooser.setAcceptAllFileFilterUsed(true);
		gameDirChooser.setVisible(true);
		gameDirChooser.setFileHidingEnabled(false);
		File defaultLocation = new File(System.getProperty("user.home") + "/.local/share/Steam/steamapps/common/Wildermyth");
		if(defaultLocation.exists() && defaultLocation.isDirectory()) {
			gameDirChooser.setCurrentDirectory(defaultLocation);
		}
		if(gameDirChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
			GameInfo gameInfo = new GameInfo(gameDirChooser.getSelectedFile());
			frame.dispose();
			return gameInfo;
		}
		else {
			cancel(frame);
		}
		throw new AssertionError("This code should be unreachable.");
	}
	
	private static File selectWorkspaceDirectory() {
		File workspaceDir = null;
		JFileChooser workspaceDirChooser = new JFileChooser();
		JFrame frame = new JFrame();
		workspaceDirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		workspaceDirChooser.setAcceptAllFileFilterUsed(true);
		workspaceDirChooser.setVisible(true);
		workspaceDirChooser.setFileHidingEnabled(false);
		if(workspaceDirChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
			workspaceDir = workspaceDirChooser.getSelectedFile();
			frame.dispose();
			return workspaceDir;
		}
		else {
			cancel(frame);
		}
		throw new AssertionError("This code should be unreachable.");
	}
	
	private static void prepareWorkspace(GameInfo gameInfo, File workspaceDir) throws InterruptedException {
		Iterator<File> files = FileUtils.iterateFilesAndDirs(gameInfo.getGameDir(), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
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
			for(String e : EXCLUSIONS.values()) {
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
		System.out.println("Found: " + (found) + " total files.\n\nWith" + excluded + " files excluded, and\n" + jars + " jar files to add to this runtime classpath");
		Thread.sleep(3000);
		binDir = new File(workspaceDir.getAbsolutePath() + "/bin");
		
		((DecompileWriteRule)WRITE_RULES.get("wildermyth")).setOriginCopyDest(new File(binDir.getPath() + "/wildermyth.jar"));
		((DecompileWriteRule)WRITE_RULES.get("scratchpad")).setOriginCopyDest(new File(binDir.getPath() + "/scratchpad.jar"));
		((DecompileWriteRule)WRITE_RULES.get("server")).setOriginCopyDest(new File(binDir.getPath() + "/lib/server-1.0.jar"));
		((DecompileWriteRule)WRITE_RULES.get("gameEngine")).setOriginCopyDest(new File(binDir.getPath() + "/lib/gameEngine-1.0.jar"));
		//((DecompileWriteRule)WRITE_RULES.get("devvotes-client")).setOriginCopyDest(new File(binDir.getPath() + "/lib/devvotes-client.jar"));
		
		fileLoop:
		for(File f : FILES) {
			boolean isModified = false;
			try {
				if(!f.isDirectory()) {
					File dest = new File(binDir.getAbsolutePath() + getLocalPath(gameInfo.getGameDir(), f));
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
						modified++;
					}
					else {
					//System.out.println("copying " + f);
						FileUtils.copyFile(f, dest);
					}
				}
				copied++;
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
		System.out.println("returned");
		return;
	}
	
	private static boolean confirm(String message, String title, String... options) {
		if(options.length != 2) {
			options = new String[] {"Cancel", "Okay"};
		}
		return 1 == JOptionPane.showOptionDialog(null, message, title, JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
	}
	
	private static void cancel(JFrame... jframes) {
		for(JFrame frame : jframes) {
			frame.dispose();
		}
		System.out.println("User cancelled workspace creation.");
		System.exit(0);
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
