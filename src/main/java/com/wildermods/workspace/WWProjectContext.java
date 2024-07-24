package com.wildermods.workspace;

import org.gradle.api.Project;

public class WWProjectContext {
	
	private final Project project;
	private final WilderWorkspaceExtension extension;
	
	public WWProjectContext(Project project, WilderWorkspaceExtension extension) {
		this.project = project;
		this.extension = extension;
	}

	public Project getProject() {
		return project;
	}
	
	public WilderWorkspaceExtension getWWExtension() {
		return extension;
	}
	
}
