package com.wildermods.workspace;

import java.awt.Window;
import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringUtils;

import com.strobel.assembler.InputTypeLoader;
import com.strobel.assembler.metadata.ClasspathTypeLoader;
import com.strobel.assembler.metadata.CompositeTypeLoader;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.JarTypeLoader;
import com.wildermods.workspace.decompile.DecompileWriteRule;
import com.wildermods.workspace.decompile.Decompiler;

import static com.wildermods.workspace.Version.NO_VERSION;

@SuppressWarnings("deprecation")
public class Main {

	private static final ConcurrentHashMap<String, String> EXCLUSIONS = new ConcurrentHashMap<String, String>();
	private static final ConcurrentHashMap<String, WriteRule> WRITE_RULES = new ConcurrentHashMap<String, WriteRule>();
	private static final ConcurrentHashMap<String, Resource> NEW_RESOURCES = new ConcurrentHashMap<String, Resource>();
	private static final Decompiler DECOMPILER = new Decompiler();
	
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
		WRITE_RULES.put("wildermyth", new DecompileWriteRule(".*/Wildermyth/wildermyth\\.jar", DECOMPILER));
		WRITE_RULES.put("scratchpad", new DecompileWriteRule(".*/Wildermyth/scratchpad\\.jar", DECOMPILER));
		WRITE_RULES.put("server", new DecompileWriteRule(".*/Wildermyth/lib/server-.*\\.jar", DECOMPILER));
		
		NEW_RESOURCES.put(".gitignore", new Resource("gitignore", ".gitignore"));
		NEW_RESOURCES.put(".gitattributes", new Resource("gitattributes", ".gitattributes"));
		NEW_RESOURCES.put("build.gradle", new Resource("build.gradle"));
		NEW_RESOURCES.put("gradlew", new Resource("gradlew"));
		NEW_RESOURCES.put("gradlew.bat", new Resource("gradlew.bat"));
		NEW_RESOURCES.put("gradleJar", new Resource("gradle/wrapper/gradle-wrapper", "gradle/wrapper/gradle-wrapper.jar"));
		NEW_RESOURCES.put("gradleProperties", new Resource("gradle/wrapper/gradle-wrapper.properties"));
	}
	
	private static final HashSet<File> FILES = new HashSet<File>();
	private static final HashSet<URL> JARS = new HashSet<URL>();
	
	public static void main(String[] args) throws InterruptedException, IOException, InvocationTargetException {
		Thread appThread = new Thread() {
			public void run() {
				try {
					SwingUtilities.invokeLater(() -> {
						System.out.println(Runtime.version());
						String[] options = new String[] {"Cancel", "Okay"};
						if(1 == JOptionPane.showOptionDialog(null, "Select the root directory of your Wildermyth installation.\nFiles will be copied and extracted from here to create a gradle workspace.", "WilderWorkspace: Select Game Location", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0])) {
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e1) {
								return;
							}
							JFileChooser gameDirChooser = new JFileChooser();
							gameDirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
							gameDirChooser.setAcceptAllFileFilterUsed(true);
							gameDirChooser.setVisible(true);
							gameDirChooser.setFileHidingEnabled(false);
							File defaultLocation = new File(System.getProperty("user.home") + "/.local/share/Steam/steamapps/common/Wildermyth");
							if(defaultLocation.exists() && defaultLocation.isDirectory()) {
								gameDirChooser.setCurrentDirectory(defaultLocation);
							}
							if(gameDirChooser.showOpenDialog(new JFrame()) == JFileChooser.APPROVE_OPTION) {
								File gameDir = gameDirChooser.getSelectedFile();
								Version wildermythVersion = NO_VERSION;
								try {
									wildermythVersion = getWildermythVersion(gameDir);
								}
								catch(IOException e) {
									System.err.println("Could not find wildermyth version due to an exception.");
									e.printStackTrace(System.err);
								}
								System.out.println("Wildermyth version: " + wildermythVersion);
								
								options = new String[] {"Cancel", "Proceed"};
								if(!wildermythVersion.equals(NO_VERSION) || (wildermythVersion.equals(NO_VERSION) && 1 == JOptionPane.showOptionDialog(null, "No Wildermyth version was detected in:\n\n" + gameDir + "\n\nContinue anyway?", "Warning", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0]))) {
									if(1 == JOptionPane.showOptionDialog(null, "Select the root directory for your gradle workspace.\nFiles will be copied here.\nExisting files will be overwritten if they have the same name.\n\n" + "Wildermyth version: " + wildermythVersion, "WilderWorkspace: Select Workspace Location", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0])){
										JFileChooser workspaceDirChooser = new JFileChooser();
										workspaceDirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
										workspaceDirChooser.setAcceptAllFileFilterUsed(true);
										workspaceDirChooser.setVisible(true);
										workspaceDirChooser.setFileHidingEnabled(false);
										if(workspaceDirChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
											Iterator<File> files = FileUtils.iterateFilesAndDirs(gameDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
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
													try {
														jars++;
														JARS.add(f.toURI().toURL());
													} catch (IOException e1) {
														throw new IOError(e1);
													}
												}
												FILES.add(f);
												System.out.println(f.getAbsolutePath());
											}
											System.out.println("Found: " + (found) + " total files.\n\nWith" + excluded + " files excluded, and\n" + jars + " jar files to add to this runtime classpath");
											
											try {
												addJarsToDecompilationClasspath();
											} catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | IOException e1) {
												throw new LinkageError(e1.getMessage(), e1);
											}
											
											File workspaceDir = workspaceDirChooser.getSelectedFile();
											File binDir = new File(workspaceDir.getAbsolutePath() + "/bin");
											fileLoop:
											for(File f : FILES) {
												boolean isModified = false;
												try {
													if(!f.isDirectory()) {
														File dest = new File(binDir.getAbsolutePath() + getLocalPath(gameDir, f));
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
														if(!isModified) {
															modified++;
															System.out.println("copying " + f);
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
											exit();
											return;
										}
									}
								}
							}
						}
						System.out.println("User cancelled workspace creation.");
						exit();
					});
				} catch (Throwable t) {
					throw new Error(t);
				}
			}
		};
		appThread.start();

	}
	
	@SuppressWarnings("deprecation")
	private static Version getWildermythVersion(File gameDir) throws IOException {
		File versionFile = new File(gameDir.getAbsolutePath() + "/version.txt");
		if(versionFile.exists()) {
			return new Version(FileUtils.readFileToString(versionFile).split(" ")[0]);
		}
		else {
			System.err.println("Could not find wilermyth version file.");
			return NO_VERSION;
		}
	}
	
	private static String getLocalPath(File gameDir, File file) {
		return StringUtils.replaceOnce(file.getAbsolutePath(), gameDir.getAbsolutePath(), "");
	}
	
	private static void addJarsToDecompilationClasspath() throws ClassNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, IOException {
		HashSet<ITypeLoader> typeLoaders = new HashSet<ITypeLoader>();
		typeLoaders.add(new ClasspathTypeLoader());
		for(URL jarFile : JARS) {
			System.out.println(jarFile + " added to classpath");
			typeLoaders.add(new JarTypeLoader(new JarFile(jarFile.getFile())));
		}
		CompositeTypeLoader compositeLoader = new CompositeTypeLoader(typeLoaders.toArray(new ITypeLoader[]{}));
		DECOMPILER.TYPE_LOADER = new InputTypeLoader(compositeLoader);
	}
	
	private static void exit() {
		for(Window window : Window.getWindows()) {
			window.dispose();
		}
	}
	
}
