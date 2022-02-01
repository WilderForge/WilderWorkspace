package com.wildermods.workspace;

import java.io.File;

public interface GameInfo {

	public File getGameDir();
	
	public String getName();
	
	public Version getVersion();
	
	public Class<? extends InstallationProperties<?>> getInstallationProperties();
	
}
