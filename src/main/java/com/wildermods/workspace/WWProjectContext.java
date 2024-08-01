package com.wildermods.workspace;

import org.gradle.api.Project;

/**
 * Represents the context of a WilderWorkspace project.
 * <p>
 * This class encapsulates the project and the WilderWorkspace extension
 * to provide a unified interface for interacting with project-specific
 * settings and configurations within the plugin.
 * </p>
 */
public class WWProjectContext {
	
	private final Project project;
	private final WilderWorkspaceExtension extension;
	
	/**
	 * Constructs a new {@code WWProjectContext} with the specified project
	 * and WilderWorkspace extension.
	 * 
	 * @param project the Gradle project associated with this context
	 * @param extension the WilderWorkspace extension associated with this context
	 */
	public WWProjectContext(Project project, WilderWorkspaceExtension extension) {
		this.project = project;
		this.extension = extension;
	}

	/**
	 * Returns the Gradle project associated with this context.
	 * 
	 * @return the Gradle project
	 */
	public Project getProject() {
		return project;
	}
	
	/**
	 * Returns the WilderWorkspace extension associated with this context.
	 * 
	 * @return the WilderWorkspace extension
	 */
	public WilderWorkspaceExtension getWWExtension() {
		return extension;
	}
	
}
