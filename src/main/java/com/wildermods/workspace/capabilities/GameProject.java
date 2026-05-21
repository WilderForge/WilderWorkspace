package com.wildermods.workspace.capabilities;

import java.nio.file.Path;

public interface GameProject {
	public Path getRootDir();
	public void trace(String message);
	public void debug(String message);
	public void info(String message);
	public void warn(String message);
	public void error(String message);
	public void fatal(String message);
	public void trace(Throwable t, String message);
	public void debug(Throwable t, String message);
	public void info(Throwable t, String message);
	public void warn(Throwable t, String message);
	public void error(Throwable t, String message);
	public void fatal(Throwable t, String message);
}
