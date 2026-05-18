package com.wildermods.workspace.tasks;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import com.wildermods.workspace.WilderWorkspacePluginImpl;
import com.wildermods.workspace.WilderWorkspacePluginImpl.ModuleInfo;
import com.wildermods.workspace.decomp.DecompilerBuilder;
import com.wildermods.workspace.decomp.GradleDecompilerBuilder;
import com.wildermods.workspace.decomp.WildermythDecompilerSetup;

@DisableCachingByDefault(because = "This task is a one time workspace setup task and should not be cached")
public class DecompileJarsTask extends DefaultTask {
	
	@Input
	private String decompDir;
	
	@TaskAction
	public void decompile() throws IOException {
		// Retrieve the module map from the project's extra properties
		Object modulesObj = getProject().getExtensions().getExtraProperties().get(WilderWorkspacePluginImpl.DECOMP_MODULES);
		if (!(modulesObj instanceof Map)) {
			throw new IllegalStateException("No module map found for decompilation. Ensure setupCapabilities ran first.");
		}
		@SuppressWarnings("unchecked")
		Map<String, ModuleInfo> modules = (Map<String, ModuleInfo>) modulesObj;

		DecompilerBuilder b = new GradleDecompilerBuilder(this);
		WildermythDecompilerSetup setup = new WildermythDecompilerSetup(b, getProject(), modules);
		Path decompPath = Path.of(this.decompDir);
		setup.decompile(decompPath);
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
