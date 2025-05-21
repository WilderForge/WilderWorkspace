package com.wildermods.workspace;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.file.PathUtils;
import org.gradle.api.Project;

import com.wildermods.thrixlvault.Vault;
import com.wildermods.workspace.dependency.VaultedDependencySpec;
import com.wildermods.workspace.util.Platform;

public class WilderWorkspaceExtension {
	private final Project project;
	private String platform = Platform.steam.name();
	private String patchline;
	private String gameDestDir;
	private String decompDir;
	
	private String steamCMDUser;
	private final List<VaultedDependencySpec> vaultedDependencies = new ArrayList<>();
	
	public WilderWorkspaceExtension(Project project) {
		this.project = project;
		this.patchline = project.getName() + " " + project.getVersion();
		this.gameDestDir = project.file("bin").toString();
		this.decompDir = Path.of(gameDestDir).toString();
	}
	
	public Project getProject() {
		return project;
	}
	
	public String getPlatform() {
		return platform;
	}
	
	public void setPlatformm(String platform) {
		project.getLogger().info("Setting platform to '" + platform + "'");
		this.platform = platform;
	}
	
	private void setSteamUser(String user) {
		project.getLogger().info("Setting SteamCMD user to '" + user + "'");
		steamCMDUser = user;
	}
	
	public String getSteamUser() {
		return steamCMDUser;
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
	
	public void useDependency(VaultedDependencySpec dependency) {
		vaultedDependencies.add(dependency);
	}
	
	public void useDependency(Path vault, String version) throws IOException {
		useDependency(new VaultedDependencySpec(new Vault(vault), version));
	}
	
	public void useDependency(Vault vault, String version) {
		useDependency(new VaultedDependencySpec(vault, version));
	}
	
	public void useDependency(String version) {
		useDependency(new VaultedDependencySpec(version));
	}
	
	public List<VaultedDependencySpec> getVaultDependencies() {
		return vaultedDependencies;
	}
	
	private final String defaultConfig = 
		"""
		platform=steam
		steamcmd_user=NO_USERNAME_PROVIDED
		""";
	
	public void loadUserConfig() {
		Path configPath = Paths.get(System.getProperty("user.home"), ".wilderWorkspace", "config.properties");
		project.getLogger().lifecycle("Looking for configuration file at: " + configPath);
		try {
			if(!Files.exists(configPath)) {
				PathUtils.createParentDirectories(configPath);
				PathUtils.writeString(configPath, defaultConfig, Charset.defaultCharset(), StandardOpenOption.CREATE_NEW);
			}
			Properties props = new Properties();
			try(FileInputStream i = new FileInputStream(configPath.toFile())) {
				props.load(i);
				props.forEach((k, v) -> {
					project.getLogger().lifecycle(k + ":" + v);
				});
				for(ConfigProperties p : ConfigProperties.values()) {
					project.getLogger().lifecycle(p.toString() + ":" + props.getProperty(p.toString()));
				}
				if(props.containsKey(ConfigProperties.platform.toString())) {
					setPlatformm(props.getProperty(ConfigProperties.platform.toString()));
				}
				if(props.containsKey(ConfigProperties.steamcmd_user.toString())) {
					setSteamUser(props.getProperty(ConfigProperties.steamcmd_user.toString()));
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		project.getLogger().lifecycle("SteamCMD user: " + getSteamUser());
	}
	
	private static enum ConfigProperties {
		platform,
		steamcmd_user
	}
}
