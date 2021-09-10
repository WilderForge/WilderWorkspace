package com.wildermods.workspace.decompile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;

public class Decompiler {
	
	private final DecompilerSettings settings;
	public ITypeLoader TYPE_LOADER = null;
	
	public Decompiler() {
		this(DecompilerSettings.javaDefaults());
	}
	
	public Decompiler(DecompilerSettings settings) {
		this.settings = settings;
		this.settings.setForceExplicitImports(true);
		this.settings.setExcludeNestedTypes(true);
	}
	
	public Throwable Decompile(File jarFile, File outputDir) {
		JarFile sourceJar = null;
		JarOutputStream out = null;
		try {
			outputDir = getNewOutDir(outputDir);
			sourceJar = new JarFile(jarFile);
			settings.setTypeLoader(TYPE_LOADER);
			FileOutputStream fileOutputStream = new FileOutputStream(outputDir);
			out = new JarOutputStream(fileOutputStream);
			Enumeration<JarEntry> entries = sourceJar.entries();
			while(entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				try {
					if(entry.getName().endsWith(".class")) {
						StringWriter outputStringWriter = new StringWriter();
						PlainTextOutput output = new PlainTextOutput(outputStringWriter);
						System.out.println("Decompiling " + entry);
						com.strobel.decompiler.Decompiler.decompile(entry.getName(), output, settings);
						JarEntry outEntry = new JarEntry(entry.getName().replace(".class", ".java"));
						out.putNextEntry(outEntry);
						String outString = outputStringWriter.toString();
						if(outString.startsWith("!!!")) {
							throw new DecompilationError(outEntry.toString());
						}
						out.write(outString.getBytes(), 0, outString.length());
						out.closeEntry();
					}
					else { //not a .class file, cannot decompile. Write file directly to decomp jar
						System.out.println("Warning: " + entry.getName() + " in " + jarFile.getAbsolutePath() + " is not a class file, skipping decompilation for this entry!");
						out.putNextEntry(new JarEntry(entry.getName()));
						byte[] bytes = new byte[(int) entry.getCompressedSize()];
						InputStream is = sourceJar.getInputStream(entry);
						is.read(bytes, 0, bytes.length);
						out.write(bytes);
						out.closeEntry();
						is.close();
					}
				}
				catch(Throwable t) {
					t.printStackTrace();
					System.err.println("FATAL: Could not decompile: " + entry);
					try {
						out.close();
						sourceJar.close();
					} catch(IOException e) {e.printStackTrace();}
					return t;
				}
			}
			out.close();
			sourceJar.close();
		}
		catch(Throwable t) {
			try {
				out.close();
				sourceJar.close();
			} catch(IOException e) {e.printStackTrace();}
			return t;
		}
		return null;
	}
	
	private static File getNewOutDir(File oldOutDir) throws IOException {
		if(oldOutDir.exists()) {
			oldOutDir.delete();
		}
		File outputDir = new File(oldOutDir.getAbsolutePath().substring(0, oldOutDir.getAbsolutePath().length() - 4) + "_DECOMP.jar");
		if(outputDir.exists()) {
			outputDir.delete();
		}
		outputDir.createNewFile();
		return outputDir;
	}
	
}
