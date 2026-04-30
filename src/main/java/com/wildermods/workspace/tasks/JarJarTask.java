package com.wildermods.workspace.tasks;

import java.io.File;
import java.util.Set;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import net.fabricmc.loom.build.nesting.JarNester;

@DisableCachingByDefault(because = "This task is a one time workspace setup task and should not be cached")
public abstract class JarJarTask extends DefaultTask {

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getMainJar();
	
	@InputFiles
	@PathSensitive(PathSensitivity.NONE)
	public abstract ConfigurableFileCollection getNestedJars();
	
	@TaskAction
	public void run() {
		File main = getMainJar().get().getAsFile();

		Set<File> nestedJars = getNestedJars().getFiles();
		
		if(nestedJars.isEmpty()) {
			getLogger().info("No jars to nest");
		}
		
		for(File file : nestedJars) {
			getLogger().info("Will attempt to nest " + file + " into " + getMainJar().get().getAsFile());
		}
		
		JarNester.nestJars(nestedJars, main, getLogger());
		
		getLogger().lifecycle("Nested " + nestedJars.size());
	}
	
}
