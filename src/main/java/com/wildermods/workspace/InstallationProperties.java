package com.wildermods.workspace;

import java.io.File;
import java.nio.file.Path;

public interface InstallationProperties<G extends GameInfo> {
	
	/**
	 * @return true if the installation settings are valid, false otherwise
	 */
	public boolean isValid();
	
	public G getGameInfo();
	
	/**
	 * @return An installation which will install the modded version the game.
	 */
	public Installation<? extends InstallationProperties<G>, G> getInstallation();
	
	/**
	 * @return true if this installation should overwrite existing files in the destination directory if they exist, false otherwise.
	 */
	public boolean overwriteGame();
	
	/**
	 * @return true if this installation should create a gradle development workspace, false otherwise.
	 */
	public boolean createGradle();
	
	
	/**
	 * @return A directory pointing to an unmodded installation of the game.
	 */
	public File getSourceDir();
	
	public default Path getSourcePath() {
		return getSourceDir().toPath();
	}
	
	/**
	 * @return A directory to install the modded version of your game.
	 */
	public File getDestDir();
	
	public default Path getDestPath() {
		return getDestDir().toPath();
	}
	
	/**
	 * @return The location of the game. If creating a gradle workspace,
	 * it should be in 'getDestDir() + "/bin"'
	 * 
	 * Otherwise, it should just be the destination directory.
	 */
	public default File getBinDir() {
		if(createGradle()) {
			return new File(getDestDir() + "/bin");
		}
		return getDestDir();
	}
	
	public default Path getBinPath() {
		return getBinDir().toPath();
	}
	
}
