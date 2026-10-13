package com.wildermods.workspace.runtime;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.wildermods.masshash.Hash;
import com.wildermods.masshash.exception.IntegrityException;
import com.wildermods.thrixlvault.ChrysalisModification;
import com.wildermods.thrixlvault.ChrysalisizedVault;
import com.wildermods.thrixlvault.Vault;
import com.wildermods.thrixlvault.exception.MissingVersionException;
import com.wildermods.thrixlvault.steam.IVaultable;
import com.wildermods.workspace.dependency.VaultedDependencySpec;

/** Selects named game contexts without changing original game blobs. */
public final class GameVariants {
	private static final String MANIFEST = "META-INF/MANIFEST.MF";

	private GameVariants() {}

	/** Loads a context, generating the built-in coremod-base context only when absent. */
	public static ChrysalisizedVault select(Vault vault, IVaultable artifact, String variant)
			throws IOException, MissingVersionException, IntegrityException, InterruptedException, ExecutionException {
		VaultedDependencySpec.validateVariant(variant);
		if (vault.hasChrysalis(artifact, variant)) {
			return vault.chrysalisize(artifact, variant);
		}
		if (!variant.equals(VaultedDependencySpec.DEFAULT_VARIANT)) {
			throw new MissingVersionException("Game variant '" + variant + "' is not vaulted for " + artifact);
		}
		ChrysalisizedVault base = vault.chrysalisize(artifact);
		base.verifyBlobs();
		var factory = base.getChrysalis().getBlobFactory();
		var changes = ChrysalisModification.builder(factory.algorithm());
		for (var entry : base.getChrysalis().blobs().entries()) {
			Path relative = entry.getValue().normalize();
			if (!isRootJar(relative)) continue;
			Path temporary = Files.createTempFile(vault.getBlobDir(), "coremod-base-", ".jar");
			try {
				if (!stripManifest(base.getBlobFile(entry.getKey()), temporary)) continue;
				Hash replacement = factory.blob(temporary);
				Path blob = base.getBlobFile(replacement);
				try {
					Files.copy(temporary, blob);
				} catch (FileAlreadyExistsException e) {
					factory.blob(blob, replacement).verify();
				}
				changes.replace(relative, entry.getKey(), replacement);
			} finally {
				Files.deleteIfExists(temporary);
			}
		}
		try {
			return vault.saveModification(artifact, variant, VaultedDependencySpec.ORIGINAL_VARIANT, changes.build());
		} catch (FileAlreadyExistsException e) {
			// Another workspace may have created this variant while we prepared its blobs.
			return vault.chrysalisize(artifact, variant);
		}
	}

	public static boolean isRootJar(Path relative) {
		Path normalized = relative.normalize();
		return !normalized.isAbsolute() && normalized.getNameCount() == 1
				&& normalized.toString().toLowerCase(Locale.ROOT).endsWith(".jar");
	}

	/**
	 * Removes the manifest ZIP entry, preserving all other entry contents and metadata.
	 * Returns false without writing the destination when the source has no manifest.
	 * The source is never modified. Callers must use distinct source and destination paths.
	 */
	public static boolean stripManifest(Path source, Path destination) throws IOException {
		if (source.toAbsolutePath().normalize().equals(destination.toAbsolutePath().normalize())
				|| (Files.exists(destination) && Files.isSameFile(source, destination))) {
			throw new IllegalArgumentException("Source and destination must be different files");
		}
		try (ZipFile input = new ZipFile(source.toFile())) {
			if (input.getEntry(MANIFEST) == null) return false;
			try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(destination))) {
				if (input.getComment() != null) output.setComment(input.getComment());
				var entries = input.entries();
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					if (entry.getName().equals(MANIFEST)) continue;
					ZipEntry copy = new ZipEntry(entry);
					if (copy.getMethod() == ZipEntry.DEFLATED) copy.setCompressedSize(-1);
					output.putNextEntry(copy);
					if (!entry.isDirectory()) {
						try (var stream = input.getInputStream(entry)) {
							stream.transferTo(output);
						}
					}
					output.closeEntry();
				}
			}
		}
		return true;
	}

	/** Updates selected game files, retaining unrelated workspace output and matching files. */
	public static void materialize(ChrysalisizedVault selected, Path destination)
			throws IOException, IntegrityException, InterruptedException, ExecutionException {
		selected.verifyBlobs();
		Files.createDirectories(destination);
		// A child may remove inherited files. Do not retain them as accidental runtime dependencies.
		var desired = new HashSet<>(selected.getChrysalis().blobs().values());
		var previous = new HashSet<>(selected.getRoot().getChrysalis().blobs().values());
		Path marker = destination.resolve(".wilderworkspace/game-files.properties");
		if (Files.exists(marker)) {
			Properties managed = new Properties();
			try (var input = Files.newInputStream(marker)) { managed.load(input); }
			for (String value : managed.stringPropertyNames()) {
				Path relative = Path.of(value).normalize();
				if (relative.isAbsolute() || relative.startsWith("..") || relative.toString().isEmpty()) {
					throw new IOException("Invalid managed game path: " + value);
				}
				previous.add(relative);
			}
		}
		for (Path relative : previous) {
			if (!desired.contains(relative) && !selected.getChrysalis().isIgnored(relative)) {
				Files.deleteIfExists(destination.resolve(relative));
			}
		}
		selected.computeOverBlobs((hash, blobDir, chrysalis, handler) -> {
			for (Path relative : chrysalis.activeBlobs().get(hash)) {
				Path target = destination.resolve(relative);
				if (Files.isRegularFile(target)) {
					try {
						chrysalis.getBlobFactory().blob(target, hash).verify();
						continue;
					} catch (IntegrityException e) {
						// Original and other variant files must be replaced with the selected bytes.
					}
				}
				Files.createDirectories(target.getParent());
				Path temporary = Files.createTempFile(target.getParent(), ".wilderworkspace-", ".tmp");
				try {
					Files.copy(blobDir.resolve(hash.hash()), temporary, StandardCopyOption.REPLACE_EXISTING);
					Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
				} finally {
					Files.deleteIfExists(temporary);
				}
			}
		});
		selected.verifyDirectory(destination, false);
		Properties managed = new Properties();
		for (var entry : selected.getChrysalis().activeBlobs().entries()) {
			managed.setProperty(entry.getValue().toString(), entry.getKey().hash());
		}
		Files.createDirectories(marker.getParent());
		Path temporary = Files.createTempFile(marker.getParent(), ".game-files-", ".tmp");
		try {
			try (var output = Files.newOutputStream(temporary)) {
				managed.store(output, "Game files managed by WilderWorkspace (" + selected.getContextName() + ")");
			}
			Files.move(temporary, marker, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
