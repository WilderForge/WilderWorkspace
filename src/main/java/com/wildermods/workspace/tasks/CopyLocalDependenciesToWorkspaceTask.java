package com.wildermods.workspace.tasks;

import java.io.FileNotFoundException;
import java.io.IOException;

import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.file.PathUtils;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import com.wildermods.masshash.exception.IntegrityException;
import com.wildermods.masshash.exception.IntegrityProblem;
import com.wildermods.thrixlvault.ChrysalisizedVault;
import com.wildermods.thrixlvault.MassDownloadWeaver;
import com.wildermods.thrixlvault.Vault;
import com.wildermods.thrixlvault.exception.DatabaseError;
import com.wildermods.thrixlvault.exception.DatabaseIntegrityError;
import com.wildermods.thrixlvault.exception.DatabaseMissingBlobError.DatabaseMissingBlobProblem;
import com.wildermods.thrixlvault.exception.MissingResourceException.MissingResourceProblem;
import com.wildermods.thrixlvault.exception.MissingVersionException;
import com.wildermods.thrixlvault.steam.IDownloadable;
import com.wildermods.thrixlvault.wildermyth.WildermythManifest;
import com.wildermods.workspace.WilderWorkspaceExtension;
import com.wildermods.workspace.WilderWorkspacePluginImpl;
import com.wildermods.workspace.dependency.VaultedDependencySpec;
import com.wildermods.workspace.util.FileHelper;
import com.wildermods.workspace.util.OS;
import com.wildermods.workspace.util.Platform;

/**
 * Task to copy local dependencies to the workspace.
 * <p>
 * This task copies the necessary files from the game installation directory to a specified workspace directory.
 * It supports the game platforms as defined in {@link Platform} and allows for custom directories.
 * </p>
 */
public class CopyLocalDependenciesToWorkspaceTask extends DefaultTask {
	
	private static final Logger LOGGER = Logging.getLogger(CopyLocalDependenciesToWorkspaceTask.class);
	private static final String UNSUPPLIED_USER = "NO_USERNAME_SUPPLIED";
	
	@Input
	private String platform = Platform.steam.name();
	
	@Input
	private String patchline = getProject().getName() + " " + getProject().getVersion();
	
	@Input
	private String destDir = getProject().file("bin").toString();
	
	@Input
	private String steamUser = UNSUPPLIED_USER;
	
	@Input
	private boolean overwrite = false;
	
	@TaskAction
	public void copyDependencies() throws IOException {
		final Path destDir = Path.of(this.destDir).toAbsolutePath().normalize();
		try {
			LOGGER.info(getProject().getExtensions().findByType(WilderWorkspaceExtension.class).getPlatform());
			Platform selectedPlatform = Platform.fromString(platform);
			LOGGER.info("Platform: " + platform);
			
			if(selectedPlatform == Platform.thrixlvault) {
				copyFromThrixlvault();
			}
			else {
				copyFromLocalInstallation(selectedPlatform);
			}
		}
		catch(GradleException e) {
			//log without stacktrace
			LOGGER.error("Failed to copy dependencies.");
			throw e;
		}
		catch(RuntimeException e) {
			LOGGER.error("Failed to copy dependencies.", e);
			throw e;
		}
		catch(Exception e) {
			RuntimeException e2 = new RuntimeException("Failed to copy dependencies.", e);
			LOGGER.error("Failed to copy dependencies.", e2);
			throw e2;
		}
	}
	
	private void copyFromThrixlvault() throws IOException, InterruptedException, ExecutionException, IntegrityException {
		WilderWorkspaceExtension extension = getProject().getExtensions().getByType(WilderWorkspaceExtension.class);
		List<VaultedDependencySpec> deps = extension.getVaultDependencies();
		
		HashMap<IDownloadable, ChrysalisizedVault> vaultedDeps = new HashMap<>();
		HashMap<IDownloadable, ChrysalisizedVault> neededDeps = new HashMap<>();
		HashMap<IDownloadable, VaultedDependencySpec> toDownloadDeps = new HashMap();
		HashMap<IDownloadable, ChrysalisizedVault> chrysalisizedDownloads = new HashMap();
		HashMap<IDownloadable, ChrysalisizedVault> toExportDeps = new HashMap<>();
		
		//make sure database is correct
		//add all vaulted deps we need
		for(VaultedDependencySpec dep : deps) {
			Vault vault = dep.vault();
			try {
				WildermythManifest manifest = WildermythManifest.get(dep.version());
				ChrysalisizedVault cVault;
				try {
					 cVault = vault.chrysalisize(manifest);
				}
				catch(MissingVersionException e) {
					LOGGER.warn("Version " + manifest.version() + " for " + manifest.os() + " not vaulted. Marking for download.");
					toDownloadDeps.put(manifest, dep);
					continue;
				}
				vaultedDeps.put(manifest, cVault);
				try {
					cVault.verifyBlobs();
				}
				catch(DatabaseIntegrityError e) { 
					boolean allMissingBlobs = e.getProblems().toList().stream().parallel().allMatch(p -> p instanceof DatabaseMissingBlobProblem);
					if(allMissingBlobs) {
						LOGGER.warn("Version " + manifest.version() + " for " + manifest.os() + " not fully vaulted. Marking for download.");
						toDownloadDeps.put(manifest, dep);
					}
					else {
						throw e;
					}
				}

			}
			catch(Throwable t) {
				throw new DatabaseError(t);
			}
		}
		
		neededDeps.putAll(vaultedDeps);
		
		if(toDownloadDeps.size() > 0) { //download any deps we need
			String user = extension.getSteamUser();
			if(user == null || user.isBlank()) {
				user = UNSUPPLIED_USER;
			}
			MassDownloadWeaver downloader = new MassDownloadWeaver(user, toDownloadDeps.keySet());
			downloader.run();
			toDownloadDeps.forEach((manifest, dep) -> {
				try {
					chrysalisizedDownloads.put(manifest, dep.vault().chrysalisize(manifest));
				} catch (MissingVersionException | IOException e) {
					throw new DatabaseError(e);
				}
			});
		}
		else {
			LOGGER.info("No dependencies to download from steam.");
		}
		
		toExportDeps.putAll(chrysalisizedDownloads); //all downloaded deps are not in the vault yet, so we know we have to export them.
		
		for(Entry<IDownloadable, ChrysalisizedVault> entry : vaultedDeps.entrySet()) { //If we have a dep already downloaded, and our project is only missing blobs from the vault, export the blobs to the project
			if(toExportDeps.containsKey(entry.getKey())) {
				continue;
			}
			try {
				entry.getValue().verifyDirectory(Path.of(extension.getGameDestDir()), false);
			} catch (IntegrityException e) {
				List<IntegrityProblem> problems = e.getProblems().toList();
				boolean onlyMissing = problems.stream().parallel().allMatch(p -> p instanceof MissingResourceProblem); //if we only have missing resources and no other problems, we can export them from the vault
				if (!onlyMissing) {
					throw e;
				}
				toExportDeps.put(entry.getKey(), entry.getValue());
			}
		}
		
		for(Entry<IDownloadable, ChrysalisizedVault> entry : toExportDeps.entrySet()) {
			try {
				entry.getValue().export(Path.of(extension.getGameDestDir()), true);
			}  catch (Throwable t) {
				throw new DatabaseError("Failed to export " + entry.getKey(), t);
			}
		}
	}
	
	private void copyFromLocalInstallation(Platform platform) throws Exception {
		final Path destDir = Path.of(this.destDir).toAbsolutePath().normalize();
		if(platform == Platform.thrixlvault) {
			throw new AssertionError();
		}
		Path installDir;
		if(platform != Platform.filesystem) {
			installDir = platform.getDefaultInstallDirectory();
			LOGGER.info("Using default " + platform + " install for " + OS.getOS() + ", located at " + installDir);
		}
		else {
			installDir = Path.of(this.platform);
			LOGGER.info("Using custom Wildermyth install located at " + installDir);
		}
		
		if(!Files.exists(installDir)) {
			throw new FileNotFoundException(installDir.toAbsolutePath().normalize().toString());
		}
		if(!Files.isDirectory(installDir)) {
			throw new NotDirectoryException(installDir.toAbsolutePath().normalize().toString());
		}
		
		if(!Files.exists(destDir)) {
			Files.createDirectories(destDir);
		}
		
		Files.walkFileTree(installDir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path target;
				if(FileHelper.shouldBeRemapped(file)) {
					target = destDir.resolve("unmapped").resolve(installDir.relativize(file));
				}
				else {
					target = destDir.resolve(installDir.relativize(file));
				}
				if(!overwrite && Files.exists(target)) {
					LOGGER.info("Not copying " + target + " - File at target location already exists.");
					return FileVisitResult.CONTINUE;
				}
				Files.createDirectories(target.getParent());
				Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
			
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Path target = destDir.resolve(installDir.relativize(dir));
				if(Files.isSymbolicLink(dir) || attrs.isSymbolicLink() || dir.getFileName().endsWith("backup") || dir.getFileName().endsWith("feedback") || dir.getFileName().endsWith("logs") || dir.getFileName().endsWith("out") || dir.getFileName().endsWith("players") || dir.getFileName().endsWith("screenshots")) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				Files.createDirectories(target);
				return FileVisitResult.CONTINUE;
			}
		});
		
		Path patchFile = destDir.resolve("patchline.txt");
		PathUtils.writeString(patchFile, patchline + " - [WilderWorkspace " + WilderWorkspacePluginImpl.VERSION + "]", Charset.defaultCharset(), StandardOpenOption.TRUNCATE_EXISTING);
		

	}
	
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
	
	public String getDestDir() {
		return destDir;
	}
	
	public void setDestDir(String path) {
		this.destDir = path;
	}
	
	public boolean getOverwrite() {
		return overwrite;
	}
	
	public void setOverwrite(boolean overwrite) {
		this.overwrite = overwrite;
	}
	
	public String getSteamUser() {
		return steamUser;
	}
	
	public void setSteamUser(String steamUser) {
		this.steamUser = steamUser;
	}
	
}
