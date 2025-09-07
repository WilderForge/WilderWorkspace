package com.wildermods.workspace.tasks;

import java.io.IOException;
import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import com.wildermods.workspace.decomp.DecompilerBuilder;
import com.wildermods.workspace.decomp.GradleDecompilerBuilder;
import com.wildermods.workspace.decomp.WildermythDecompilerSetup;

public class DecompileJarsTask extends DefaultTask {

	@Input
	private String compiledDir;
	
	@Input
	private String decompDir;
	
	@TaskAction
	public void decompile() throws IOException {
		DecompilerBuilder b = new GradleDecompilerBuilder(this);
		WildermythDecompilerSetup setup = new WildermythDecompilerSetup(b);
		Path compiledDir = Path.of(this.compiledDir);
		Path decompDir = Path.of(this.decompDir);
		setup.decompile(compiledDir, decompDir);
		
	}

    /**
     * Gets the directory containing the compiled JAR files.
     * 
     * @return the compiled directory path as a string
     */
	public String getCompiledDir() {
		return compiledDir;
	}

    /**
     * Sets the directory containing the compiled JAR files.
     * 
     * @param compiledDir the compiled directory path as a string
     */
	public void setCompiledDir(String compiledDir) {
		this.compiledDir = compiledDir;
	}

	 /**
     * Gets the directory where the decompiled sources will be stored.
     * 
     * @return the decompiled directory path as a string
     */
	public String getDecompDir() {
		return decompDir;
	}

    /**
     * Sets the directory where the decompiled sources will be stored.
     * 
     * @param decompDir the decompiled directory path as a string
     */
	public void setDecompDir(String decompDir) {
		this.decompDir = decompDir;
	}
	
}
