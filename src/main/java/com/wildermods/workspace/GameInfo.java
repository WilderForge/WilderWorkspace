package com.wildermods.workspace;

import static com.wildermods.workspace.Version.NO_VERSION;

import java.io.File;
import org.apache.commons.io.FileUtils;

public class GameInfo {
	
	private final File gameDir;
	private final Version version;

	GameInfo(File gameDir) {
		this.gameDir = gameDir;
		this.version = getWildermythVersion();
	}
	
	public File getGameDir() {
		return gameDir;
	}
	
	public Version getVersion() {
		return version;
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
