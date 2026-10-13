package com.wildermods.workspace.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wildermods.thrixlvault.Vault;
import com.wildermods.workspace.WilderWorkspaceExtension;
import com.wildermods.workspace.tasks.CopyLocalDependenciesToWorkspaceTask;

class GameVariantConfigurationTest {
	@TempDir Path temporary;

	@Test void extensionSupportsDefaultAndExplicitVariantsWithCustomVault() throws Exception {
		var project = ProjectBuilder.builder().withProjectDir(temporary.toFile()).build();
		var extension = new WilderWorkspaceExtension(project);
		Vault vault = new Vault(temporary.resolve("vault"));
		extension.useDependency(vault, "1.0");
		extension.useDependency(vault, "1.0", "blobs");
		extension.setGameVariant("custom");
		extension.useDependency(vault, "1.1");
		assertEquals("coremod-base", extension.getVaultDependencies().get(0).variant());
		assertEquals("blobs", extension.getVaultDependencies().get(1).variant());
		assertEquals("custom", extension.getVaultDependencies().get(2).variant());
		assertThrows(IllegalArgumentException.class, () -> extension.setGameVariant("../bad"));
	}

	@Test void localPreparationTransformsOnlyWorkspaceRootJarsAndCanRestoreOriginal() throws Exception {
		Path installation = Files.createDirectory(temporary.resolve("installation"));
		GameVariantsTest.jar(installation.resolve("game.jar"), true);
		GameVariantsTest.jar(installation.resolve("lib/library.jar"), true);
		var project = ProjectBuilder.builder().withProjectDir(temporary.toFile()).build();
		var extension = project.getExtensions().create("wilderWorkspace", WilderWorkspaceExtension.class);
		var task = project.getTasks().create("copyGame", CopyLocalDependenciesToWorkspaceTask.class);
		task.setPlatform(installation.toString());
		Path workspace = temporary.resolve("workspace");
		task.setDestDir(workspace.toString());
		task.copyDependencies();
		try (var input = new ZipFile(workspace.resolve("game.jar").toFile())) { assertNull(input.getEntry("META-INF/MANIFEST.MF")); }
		try (var input = new ZipFile(installation.resolve("game.jar").toFile())) { assertNotNull(input.getEntry("META-INF/MANIFEST.MF")); }
		assertArrayEquals(Files.readAllBytes(installation.resolve("lib/library.jar")), Files.readAllBytes(workspace.resolve("lib/library.jar")));
		extension.setGameVariant("blobs");
		task.copyDependencies();
		assertArrayEquals(Files.readAllBytes(installation.resolve("game.jar")), Files.readAllBytes(workspace.resolve("game.jar")));
		extension.setGameVariant("custom");
		assertThrows(org.gradle.api.GradleException.class, task::copyDependencies);
	}
}
