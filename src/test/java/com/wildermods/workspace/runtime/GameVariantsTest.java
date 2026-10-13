package com.wildermods.workspace.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wildermods.thrixlvault.ChrysalisModification;
import com.wildermods.thrixlvault.Vault;
import com.wildermods.thrixlvault.Weaver;
import com.wildermods.thrixlvault.exception.MissingVersionException;
import com.wildermods.thrixlvault.steam.IVaultable;
import com.wildermods.workspace.dependency.VaultedDependencySpec;

class GameVariantsTest {
	@TempDir Path temporary;
	private static final IVaultable ARTIFACT = new IVaultable() {
		public String name() { return "test-game"; }
		public Path artifactPath() { return Path.of("test-game", "1.0"); }
	};

	static void jar(Path path, boolean manifest) throws Exception {
		Files.createDirectories(path.getParent());
		try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
			output.setComment("archive comment");
			if (manifest) {
				output.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
				output.write("Manifest-Version: 1.0\r\nClass-Path: lib/old.jar\r\n\r\n".getBytes());
				output.closeEntry();
			}
			ZipEntry entry = new ZipEntry("payload.txt");
			entry.setTime(1700000000000L);
			entry.setComment("entry comment");
			output.putNextEntry(entry);
			output.write("game contents".getBytes());
			output.closeEntry();
			byte[] stored = "stored contents".getBytes();
			CRC32 crc = new CRC32(); crc.update(stored);
			entry = new ZipEntry("stored.txt");
			entry.setMethod(ZipEntry.STORED); entry.setSize(stored.length); entry.setCrc(crc.getValue());
			output.putNextEntry(entry); output.write(stored); output.closeEntry();
		}
	}

	@Test void removesOnlyRootManifestsAndPersistsReplacementContext() throws Exception {
		Path original = Files.createDirectory(temporary.resolve("game"));
		jar(original.resolve("game.jar"), true);
		Files.copy(original.resolve("game.jar"), original.resolve("other.jar"));
		Files.createDirectories(original.resolve("lib"));
		Files.copy(original.resolve("game.jar"), original.resolve("lib/library.jar"));
		jar(original.resolve("plain.jar"), false);
		Files.writeString(original.resolve("data.txt"), "data");
		Vault vault = new Vault(temporary.resolve("vault"));
		var base = new Weaver(vault, ARTIFACT, original).weave();
		byte[] declaration = Files.readAllBytes(base.getChrysalisFile());
		var selected = GameVariants.select(vault, ARTIFACT, "coremod-base");
		assertEquals("blobs", selected.getParent().orElseThrow().getContextName());
		assertArrayEquals(declaration, Files.readAllBytes(base.getChrysalisFile()));
		base.verifyDirectory(original);
		var json = Weaver.GSON.fromJson(Files.readString(selected.getChrysalisFile()), com.google.gson.JsonObject.class);
		assertEquals("blobs", json.get("parent").getAsString());
		assertEquals(1, json.getAsJsonObject("replacements").size());
		Path workspace = temporary.resolve("workspace");
		GameVariants.materialize(selected, workspace);
		try (var input = new ZipFile(workspace.resolve("game.jar").toFile())) {
			assertNull(input.getEntry("META-INF/MANIFEST.MF"));
			assertEquals("archive comment", input.getComment());
			assertEquals("entry comment", input.getEntry("payload.txt").getComment());
			assertEquals(1700000000000L, input.getEntry("payload.txt").getTime());
			assertEquals("game contents", new String(input.getInputStream(input.getEntry("payload.txt")).readAllBytes()));
			assertEquals(ZipEntry.STORED, input.getEntry("stored.txt").getMethod());
		}
		assertArrayEquals(Files.readAllBytes(original.resolve("lib/library.jar")), Files.readAllBytes(workspace.resolve("lib/library.jar")));
		assertArrayEquals(Files.readAllBytes(original.resolve("plain.jar")), Files.readAllBytes(workspace.resolve("plain.jar")));
		byte[] saved = Files.readAllBytes(selected.getChrysalisFile());
		GameVariants.select(vault, ARTIFACT, "coremod-base");
		assertArrayEquals(saved, Files.readAllBytes(selected.getChrysalisFile()));
	}

	@Test void switchesExistingWorkspaceBetweenOriginalAndCoremodBase() throws Exception {
		Path original = Files.createDirectory(temporary.resolve("game"));
		jar(original.resolve("game.jar"), true);
		Vault vault = new Vault(temporary.resolve("vault"));
		var base = new Weaver(vault, ARTIFACT, original).weave();
		Path workspace = temporary.resolve("workspace");
		GameVariants.materialize(base, workspace);
		Path output = workspace.resolve("mod-output/classes.bin");
		Files.createDirectories(output.getParent()); Files.writeString(output, "mod output");
		var selected = GameVariants.select(vault, ARTIFACT, "coremod-base");
		GameVariants.materialize(selected, workspace);
		try (var input = new ZipFile(workspace.resolve("game.jar").toFile())) { assertNull(input.getEntry("META-INF/MANIFEST.MF")); }
		var modifiedTime = Files.getLastModifiedTime(workspace.resolve("game.jar"));
		GameVariants.materialize(selected, workspace);
		assertEquals(modifiedTime, Files.getLastModifiedTime(workspace.resolve("game.jar")));
		GameVariants.materialize(GameVariants.select(vault, ARTIFACT, "blobs"), workspace);
		assertArrayEquals(Files.readAllBytes(original.resolve("game.jar")), Files.readAllBytes(workspace.resolve("game.jar")));
		assertEquals("mod output", Files.readString(output));
	}

	@Test void selectsExistingCustomVariantAndRejectsMissingNames() throws Exception {
		Path original = Files.createDirectory(temporary.resolve("game"));
		jar(original.resolve("game.jar"), true);
		Vault vault = new Vault(temporary.resolve("vault"));
		var base = new Weaver(vault, ARTIFACT, original).weave();
		vault.saveModification(ARTIFACT, "custom", "blobs", ChrysalisModification.builder(base.getChrysalis().getBlobFactory().algorithm()).remove(Path.of("game.jar")).build());
		assertTrue(GameVariants.select(vault, ARTIFACT, "custom").getChrysalis().blobs().isEmpty());
		Path workspace = temporary.resolve("workspace");
		GameVariants.materialize(base, workspace);
		GameVariants.materialize(GameVariants.select(vault, ARTIFACT, "custom"), workspace);
		assertFalse(Files.exists(workspace.resolve("game.jar")));
		assertThrows(MissingVersionException.class, () -> GameVariants.select(vault, ARTIFACT, "missing"));
		assertThrows(IllegalArgumentException.class, () -> new VaultedDependencySpec(vault, "1.0", "../escape"));
		assertEquals("coremod-base", new VaultedDependencySpec(vault, "1.0").variant());
	}
	@Test void switchingAwayFromCustomVariantRemovesItsAddedFiles() throws Exception {
		Path original = Files.createDirectory(temporary.resolve("game"));
		jar(original.resolve("game.jar"), true);
		Vault vault = new Vault(temporary.resolve("vault"));
		var base = new Weaver(vault, ARTIFACT, original).weave();
		Path extra = temporary.resolve("extra.jar");
		jar(extra, false);
		var hash = base.getChrysalis().getBlobFactory().blob(extra);
		Files.copy(extra, base.getBlobFile(hash));
		var custom = vault.saveModification(ARTIFACT, "custom", "blobs",
			ChrysalisModification.builder(hash.algorithm()).add(Path.of("extra.jar"), hash).build());
		Path workspace = temporary.resolve("workspace");
		GameVariants.materialize(custom, workspace);
		assertTrue(Files.exists(workspace.resolve("extra.jar")));
		GameVariants.materialize(base, workspace);
		assertFalse(Files.exists(workspace.resolve("extra.jar")));
	}

}
