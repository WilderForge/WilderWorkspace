package com.wildermods.workspace.decompile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;

import com.wildermods.workspace.Main;

public class Decompiler {
	
	private static final ExecutorCompletionService<Throwable> executor = new ExecutorCompletionService<Throwable>(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 3));
	
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
	
	public Throwable Decompile(File jarFile, File outputFile) {
		try {
			outputFile = getNewOutFile(outputFile);
		} catch (IOException e) {
			return e;
		}
		if(outputFile.exists()) {
			outputFile.delete();
		}
		try (JarFile originalJar = new JarFile(jarFile); JarOutputStream outJarStream = new JarOutputStream(new FileOutputStream(outputFile))){
			outputFile.createNewFile();
			settings.setTypeLoader(TYPE_LOADER);
			List<Future<Throwable>> futures = Collections.synchronizedList(new ArrayList<Future<Throwable>>());
			ConcurrentLinkedDeque<ZipEntryContext> deque = new ConcurrentLinkedDeque<ZipEntryContext>();
			originalJar.stream().forEach(e -> submitDecompTask(executor, futures, deque, getZipContext(originalJar, e)));
			for(Future<Throwable> future : futures) { //wait until all tasks are finished
				Throwable t = future.get();
				if(t != null) {
					return t;
				}
			} 
			System.out.println("Finished decompiling " + jarFile.getName() + ". Zipping contents to " + outputFile.getAbsolutePath());
			while(!deque.isEmpty()) {
				ZipEntryContext zipContext = deque.poll();
				JarEntry entry = new JarEntry(zipContext.entry.getName().replace('\\', '/').replace(".class", ".java"));
				InputStream i = new FileInputStream(zipContext.tempFile);
				outJarStream.putNextEntry(entry);
				i.transferTo(outJarStream);
				outJarStream.closeEntry();
				zipContext.file.close();
			}
			return null;
		}
		catch(Throwable t) {
			return t;
		}
	}
	
	@SuppressWarnings("resource")
	private void submitDecompTask(ExecutorCompletionService<Throwable> executor, List<Future<Throwable>> futures, Deque<ZipEntryContext> queue, ZipEntryContext context) {
		futures.add(executor.submit(() -> {
			try {
				if(context.entry.getName().endsWith(".class")) {
						File file = context.tempFile;
						StringWriter outputStringWriter = new StringWriter();
						PlainTextOutput output = new PlainTextOutput(outputStringWriter);
						System.out.println("Decompiling " + context.entry);
						com.strobel.decompiler.Decompiler.decompile(context.entry.getName(), output, settings);
						String outString = outputStringWriter.toString();
						if(outString.startsWith("!!!")) {
							throw new DecompilationError(context.entry.getName());
						}
						FileOutputStream fos = new FileOutputStream(file);
						fos.write(outString.getBytes(), 0, outString.length());
						queue.add(context);
						//fos.close();
						return null;
				}
				else { //not a .class file, cannot decompile. Write file directly.
					System.out.println("Warning: " + context.entry.getName() + " in " + context.file.getName() + " is not a class file, skipping decompilation for this entry!");
					byte[] bytes = new byte[(int) context.entry.getCompressedSize()];
					InputStream is = context.file.getInputStream(context.entry);
					is.read(bytes, 0, bytes.length);
					FileOutputStream fos = new FileOutputStream(context.tempFile);
					fos.write(bytes);
					queue.add(context);
					//fos.close();
					return null;
				}
			}
			catch(Throwable t) {
				System.out.println("Failed to decompile " + context.entry.getName());
				t.printStackTrace();
				try(context.file){} catch(IOException swallow) {swallow.printStackTrace();};
				return new IOError(t);
			}
		}));
	}
	
	private static final class ZipEntryContext {
		
		private final ZipFile file;
		private final ZipEntry entry;
		private final File tempFile;
		
		private ZipEntryContext(ZipFile file, ZipEntry entry) throws IOException {
			this.file = file;
			this.entry = entry;
			this.tempFile = File.createTempFile(file.getName().substring(0, file.getName().length() - 3), entry.getName().replace(File.separatorChar, '.'));
			tempFile.deleteOnExit();
		}
		
	}
	
	private static ZipEntryContext getZipContext(ZipFile file, ZipEntry entry) {
		try {
			return new ZipEntryContext(file, entry);
		}
		catch(IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private static File getNewOutFile(File oldOutDir) throws IOException {
		if(oldOutDir.exists()) {
			oldOutDir.delete();
		}
		File outputDir = new File(Main.binDir.getAbsolutePath() + File.separator + "decomp" + File.separator + oldOutDir.getName().replace(".jar", "") + "_DECOMP.jar");
		if(outputDir.exists()) {
			outputDir.delete();
		}
		outputDir.getParentFile().mkdirs();
		outputDir.createNewFile();
		return outputDir;
	}
	
}
