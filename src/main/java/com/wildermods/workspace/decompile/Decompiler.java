package com.wildermods.workspace.decompile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import com.strobel.assembler.metadata.JarTypeLoader;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;

public class Decompiler {
	
	private HashSet<JarFile> jarFiles = new HashSet<JarFile>();
	
	private final DecompilerSettings settings;
	
	public Decompiler() {
		this(new DecompilerSettings());
	}
	
	public Decompiler(DecompilerSettings settings) {
		this.settings = settings;
	}
	
	public void addJarFile(JarFile jarfile) {
		jarFiles.add(jarfile);
	}
	
	public void Decompile(File jarFile, File outputDir) throws IOException {
		outputDir = getNewOutDir(outputDir);
		JarFile sourceJar = new JarFile(jarFile);
		settings.setTypeLoader(new JarTypeLoader(sourceJar));
		FileOutputStream fileOutputStream = new FileOutputStream(outputDir);
		JarOutputStream out = new JarOutputStream(fileOutputStream);
		Enumeration<JarEntry> entries = sourceJar.entries();
		while(entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();
			try {
				if(entry.getName().endsWith(".class")) {
					StringWriter outputStringWriter = new StringWriter();
					PlainTextOutput output = new PlainTextOutput(outputStringWriter);
					System.out.println("Decompiling " + entry);
					com.strobel.decompiler.Decompiler.decompile(entry.getName(), output, settings);
					JarEntry outEntry = new JarEntry(entry.getName());
					out.putNextEntry(outEntry);
					String outString = outputStringWriter.toString();
					if(outString.startsWith("!!!")) {
						throw new ClassFormatError(outEntry.toString());
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
				throw t;
			}
		}
		out.close();
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
