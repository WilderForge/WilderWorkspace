package com.wildermods.workspace.decomp;

import org.gradle.api.Task;

import com.wildermods.workspace.util.GradlePrintStreamLogger;

public class GradleDecompilerBuilder extends DecompilerBuilder {

	public GradleDecompilerBuilder(Task task) {
		setLogger(new GradlePrintStreamLogger(task));
	}
	
}
