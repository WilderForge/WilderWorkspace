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
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.file.PathUtils;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import com.wildermods.masshash.exception.IntegrityException;
import com.wildermods.thrixlvault.ChrysalisizedVault;
import com.wildermods.thrixlvault.SteamDownloader;
import com.wildermods.thrixlvault.Weaver;
import com.wildermods.thrixlvault.steam.CompletedDownload;
import com.wildermods.thrixlvault.steam.FailedDownload;
import com.wildermods.thrixlvault.steam.SkippedDownload;
import com.wildermods.thrixlvault.exception.DatabaseIntegrityError;
import com.wildermods.thrixlvault.exception.DatabaseMissingBlobError.DatabaseMissingBlobProblem;
import com.wildermods.thrixlvault.exception.MissingVersionException;
import com.wildermods.thrixlvault.exception.VersionParsingException;
import com.wildermods.thrixlvault.wildermyth.WildermythManifest;
import com.wildermods.workspace.WilderWorkspaceExtension;
import com.wildermods.workspace.WilderWorkspacePluginImpl;
import com.wildermods.workspace.dependency.VaultedDependencySpec;
import com.wildermods.workspace.runtime.GameVariants;
import com.wildermods.workspace.util.OS;
import com.wildermods.workspace.util.Platform;

/**
 * Task to copy local dependencies to the workspace.
 * <p>
 * This task copies the necessary files from the game installation directory to a specified workspace directory.
 * It supports the game platforms as defined in {@link Platform} and allows for custom directories.
 * </p>
 */
@DisableCachingByDefault(because = "This task is a one time workspace setup task and should not be cached")
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
	
	private void copyFromThrixlvault() throws IOException, InterruptedException, ExecutionException, IntegrityException, VersionParsingException {
		WilderWorkspaceExtension extension = getProject().getExtensions().getByType(WilderWorkspaceExtension.class);
		List<VaultedDependencySpec> deps = extension.getVaultDependencies();
		if (deps.isEmpty()) {
			throw new GradleException("Select a game version with wilderWorkspace.useDependency(version[, variant]).");
		}
		for (VaultedDependencySpec dep : deps) {
			WildermythManifest manifest = WildermythManifest.get(dep.version());
			// Only the built-in variant can be synthesized. A misspelled custom name must fail before downloading.
			if (!dep.variant().equals(VaultedDependencySpec.DEFAULT_VARIANT)
					&& !dep.variant().equals(VaultedDependencySpec.ORIGINAL_VARIANT)
					&& !dep.vault().hasChrysalis(manifest, dep.variant())) {
				throw new GradleException("Game variant '" + dep.variant() + "' is not vaulted for " + manifest);
			}
			ChrysalisizedVault selected;
			if (dep.vault().hasChrysalis(manifest, dep.variant())) {
				selected = dep.vault().chrysalisize(manifest, dep.variant());
			} else {
				try {
					dep.vault().chrysalisize(manifest).verifyBlobs();
				} catch (MissingVersionException e) {
					downloadOriginal(dep, manifest, extension.getSteamUser());
				} catch (DatabaseIntegrityError e) {
					if (!e.getProblems().allMatch(p -> p instanceof DatabaseMissingBlobProblem)) throw e;
					downloadOriginal(dep, manifest, extension.getSteamUser());
				}
				selected = GameVariants.select(dep.vault(), manifest, dep.variant());
			}
			LOGGER.lifecycle("Preparing Wildermyth " + dep.version() + " (" + dep.variant() + ")");
			GameVariants.materialize(selected, Path.of(extension.getGameDestDir()).toAbsolutePath().normalize());
		}
	}

	private void downloadOriginal(VaultedDependencySpec dep, WildermythManifest manifest, String user)
			throws IOException, InterruptedException, IntegrityException {
		if (user == null || user.isBlank()) user = UNSUPPLIED_USER;
		// Weave explicitly into the requested vault, rather than the downloader's default vault.
		for (var download : new SteamDownloader(user, java.util.Set.of(manifest)).run()) {
			if (download instanceof CompletedDownload) {
				new Weaver(dep.vault(), manifest, download.dest(), true).weave();
				return;
			}
			if (download instanceof FailedDownload failed) {
				throw new GradleException("Failed to download " + manifest, failed.failReason());
			}
			if (download instanceof SkippedDownload skipped) {
				throw new GradleException("Could not download " + manifest + ": " + skipped.skipReason());
			}
		}
		throw new GradleException("Steam returned no completed download for " + manifest);
	}

	private void copyFromLocalInstallation(Platform platform) throws Exception {
		final Path destDir = Path.of(this.destDir).toAbsolutePath().normalize();
		if(platform == Platform.thrixlvault) {
			throw new AssertionError();
		}
		String variant = getProject().getExtensions().getByType(WilderWorkspaceExtension.class).getGameVariant();
		if (!variant.equals(VaultedDependencySpec.DEFAULT_VARIANT)
				&& !variant.equals(VaultedDependencySpec.ORIGINAL_VARIANT)) {
			throw new GradleException("Named variant '" + variant + "' requires the thrixlvault platform.");
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
		
		if (destDir.startsWith(installDir.toAbsolutePath().normalize())) {
			throw new GradleException("The workspace destination must be outside the game installation.");
		}

		if(!Files.exists(destDir)) {
			Files.createDirectories(destDir);
		}
		
		Files.walkFileTree(installDir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path target = destDir.resolve(installDir.relativize(file));

				Path relative = installDir.relativize(file);
				if (GameVariants.isRootJar(relative)) {
					// Always refresh root JARs from the installation when switching between blobs/coremod-base.
					Files.createDirectories(target.getParent());
					Path temporary = Files.createTempFile(target.getParent(), ".wilderworkspace-", ".jar");
					try {
						if (!variant.equals(VaultedDependencySpec.DEFAULT_VARIANT)
								|| !GameVariants.stripManifest(file, temporary)) {
							Files.copy(file, temporary, StandardCopyOption.REPLACE_EXISTING);
						}
						Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
					} finally {
						Files.deleteIfExists(temporary);
					}
					return FileVisitResult.CONTINUE;
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
		PathUtils.writeString(patchFile, patchline + " - [WilderWorkspace " + WilderWorkspacePluginImpl.VERSION + "]", Charset.defaultCharset(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		

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
