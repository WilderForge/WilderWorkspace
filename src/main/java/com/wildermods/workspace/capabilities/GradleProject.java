package com.wildermods.workspace.capabilities;

import java.nio.file.Path;

import org.gradle.api.Project;

public class GradleProject implements GameProject {

	private final Project project;
	
	public GradleProject(Project project) {
		this.project = project;
	}
	
	public Project getProject() {
		return project;
	}

	@Override
	public Path getRootDir() {
		return project.getRootDir().toPath();
	}

	@Override
	public void trace(String message) {
		project.getLogger().trace(message);
	}

	@Override
	public void debug(String message) {
		project.getLogger().debug(message);
	}

	@Override
	public void info(String message) {
		project.getLogger().info(message);
	}

	@Override
	public void warn(String message) {
		project.getLogger().warn(message);
	}

	@Override
	public void error(String message) {
		project.getLogger().error(message);
	}
	
	@Override
	public void fatal(String message) {
		error(message);
	}

	@Override
	public void trace(Throwable t, String message) {
		project.getLogger().trace(message, t);
	}

	@Override
	public void debug(Throwable t, String message) {
		project.getLogger().debug(message, t);
	}

	@Override
	public void info(Throwable t, String message) {
		project.getLogger().info(message, t);
	}

	@Override
	public void warn(Throwable t, String message) {
		project.getLogger().warn(message, t);
	}

	@Override
	public void error(Throwable t, String message) {
		project.getLogger().error(message, t);
	}
	
	@Override
	public void fatal(Throwable t, String message) {
		error(t, message);
	}
	
}
