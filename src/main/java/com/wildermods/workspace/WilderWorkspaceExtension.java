package com.wildermods.workspace;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

import org.apache.commons.io.file.PathUtils;

public class WilderWorkspaceExtension {
	private String platform;
	private String patchline;
	private String gameDestDir;
	private String decompDir;
	
	public String getPlatform() {
		return platform;
	}
	
	public void setPlatform(String platform) {
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
		try {
			if(!Files.exists(configPath)) {
				PathUtils.createParentDirectories(configPath);
				PathUtils.writeString(configPath, "platform=steam", Charset.defaultCharset(), StandardOpenOption.CREATE_NEW);
			}
			Properties props = new Properties();
			try(FileInputStream i = new FileInputStream(configPath.toFile())) {
				props.load(i);
				if(props.containsKey("platform")) {
					setPlatform(props.getProperty(platform));
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
