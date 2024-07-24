package com.wildermods.workspace;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

import org.apache.commons.io.file.PathUtils;
import org.gradle.api.Project;

import com.wildermods.workspace.util.Platform;

public class WilderWorkspaceExtension {
	private final Project project;
	private String platform = Platform.steam.name();
	private String patchline;
	private String gameDestDir;
	private String decompDir;
	
	public WilderWorkspaceExtension(Project project) {
		this.project = project;
		this.patchline = project.getName() + " " + project.getVersion();
		this.gameDestDir = project.file("bin").toString();
		this.decompDir = Path.of(gameDestDir).toString();
	}
	
	public String getPlatform() {
		return platform;
	}
	
	public void setPlatformm(String platform) {
		project.getLogger().info("Setting platform to " + platform);
		this.platform = platform;
	}
	
	public String getPatchline() {
		return patchline;
	}
	
	public void setPatchline(String patchline) {
		this.patchline = patchline;
	}
	
	public String getGameDestDir() {
		return gameDestDir;
	}
	
	public void setGameDestDir(String dir) {
		this.gameDestDir = dir;
	}
	
	public String getDecompDir() {
		return decompDir;
	}
	
	public void setDecompDir(String dir) {
		this.decompDir = dir;
	}
	
	public void loadUserConfig() {
		Path configPath = Paths.get(System.getProperty("user.home"), ".wilderWorkspace", "config.properties");
		project.getLogger().lifecycle("Looking for configuration file at: " + configPath);
		try {
			if(!Files.exists(configPath)) {
				PathUtils.createParentDirectories(configPath);
				PathUtils.writeString(configPath, "platform=steam", Charset.defaultCharset(), StandardOpenOption.CREATE_NEW);
			}
			Properties props = new Properties();
			try(FileInputStream i = new FileInputStream(configPath.toFile())) {
				props.load(i);
				if(props.containsKey("platform")) {
					setPlatformm(props.getProperty("platform"));
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
