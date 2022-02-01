package com.wildermods.workspace.wilder;

import static com.wildermods.workspace.Version.NO_VERSION;

import java.io.File;

import org.apache.commons.io.FileUtils;

import com.wildermods.workspace.GameInfo;
import com.wildermods.workspace.InstallationProperties;
import com.wildermods.workspace.Version;

public class WildermythGameInfo implements GameInfo {

	public final File gameDir;
	public final Version version;
	
	WildermythGameInfo(File gameDir) {
		this.gameDir = gameDir;
		this.version = getWildermythVersion();
	}
	
	public String getName() {
		return "Wildermyth";
	}
	
	public File getGameDir() {
		return gameDir;
	}
	
	public Version getVersion() {
		return version;
	}
	

	@Override
	public Class<? extends InstallationProperties<?>> getInstallationProperties() {
		return WilderInstallationProperties.class;
	}
	
	@SuppressWarnings("deprecation")
	private Version getWildermythVersion() {
		try {
			File versionFile = new File(gameDir.getAbsolutePath() + "/version.txt");
			if(versionFile.exists()) {
				return new Version(FileUtils.readFileToString(versionFile).split(" ")[0]);
			}
			else {
				System.err.println("Could not find wildermyth version file.");
				return NO_VERSION;
			}
		}
		catch(Throwable t) {
			System.err.println("Could not read wildermyth version file");
			t.printStackTrace();
			return NO_VERSION;
		}
	}
	
}
