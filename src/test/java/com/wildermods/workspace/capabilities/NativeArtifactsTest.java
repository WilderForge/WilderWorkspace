package com.wildermods.workspace.capabilities;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wildermods.workspace.WWProjectContext;
import com.wildermods.workspace.WilderWorkspaceExtension;
import com.wildermods.workspace.WilderWorkspacePluginImpl;

class NativeArtifactsTest {
	@TempDir Path temporary;

	private void jar(String name) throws Exception {
		Path file = temporary.resolve("bin/lib").resolve(name);
		Files.createDirectories(file.getParent());
		try (var out = new ZipOutputStream(Files.newOutputStream(file))) {
			out.putNextEntry(new ZipEntry("fixture.txt"));
			out.write(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			out.closeEntry();
		}
	}

	private Project project() {
		var project = ProjectBuilder.builder().withProjectDir(temporary.toFile()).build();
		project.getPlugins().apply("java");
		return project;
	}

	private Map<String, ModuleInfo> scan(Project project) throws Exception {
		var game = new GradleProject(project);
		return new CapabilityHandler(game).scanFlatDirModules(game, Set.of(temporary.resolve("bin/lib").toFile()));
	}

	private void invoke(String name, Class<?> argumentType, Object argument) throws Exception {
		var method = WilderWorkspacePluginImpl.class.getDeclaredMethod(name, argumentType);
		method.setAccessible(true);
		method.invoke(new WilderWorkspacePluginImpl(), argument);
	}

	private void configure(Project project) throws Exception {
		var extension = new WilderWorkspaceExtension(project);
		invoke("setupCapabilities", WWProjectContext.class, new WWProjectContext(project, extension));
	}

	@Test void nativeRulesCoverEveryLwjglModuleAndKeepThemOutOfDecompilation() throws Exception {
		for (String module : new String[] {"lwjgl", "lwjgl-glfw", "lwjgl-jemalloc", "lwjgl-openal", "lwjgl-opengl", "lwjgl-stb"}) {
			jar(module + "-3.3.1.jar");
			jar(module + "-3.3.1-natives-linux.jar");
			jar(module + "-3.3.1-natives-windows-x86.jar");
			jar(module + "-3.2.3-natives-linux.jar");
			jar(module + "-3.3.1-natives-macos-arm64.jar");
		}
		jar("lwjgl-nfd.jar");
		jar("lwjgl-nfd-natives-linux.jar");
		jar("lwjgl-nfd-natives-windows.jar");
		jar("lwjgl-nfd-natives-macos.jar");
		var modules = scan(project());
		assertEquals(7, modules.size());
		for (var module : modules.values()) {
			assertEquals(3, module.natives().size());
			assertFalse(module.relativeJarPath().toString().contains("natives-"));
			assertTrue(module.natives().stream().noneMatch(n -> n.relativeJarPath().toString().contains("3.2.3")));
		}
		assertEquals("0.0.0+unknown", modules.get("lwjgl-nfd").version().toString());
	}

	@Test void selectedLocalVersionSuppliesBaseAndMatchingNativeClassifiers() throws Exception {
		for (String version : new String[] {"3.2.3", "3.3.1"}) {
			jar("lwjgl-" + version + ".jar");
			jar("lwjgl-" + version + "-natives-linux.jar");
			jar("lwjgl-" + version + "-natives-windows.jar");
		}
		var project = project();
		configure(project);
		var artifacts = project.getConfigurations().getByName("compileClasspath").getResolvedConfiguration().getResolvedArtifacts();
		assertEquals(3, artifacts.size());
		assertTrue(artifacts.stream().allMatch(a -> a.getModuleVersion().getId().getVersion().equals("3.3.1")));
		assertEquals(Set.of("base", "natives-linux", "natives-windows"), artifacts.stream()
			.map(a -> a.getClassifier() == null ? "base" : a.getClassifier()).collect(Collectors.toSet()));
		project.getPlugins().apply("eclipse");
		var eclipse = project.getExtensions().getByType(org.gradle.plugins.ide.eclipse.model.EclipseModel.class);
		var entries = eclipse.getClasspath().resolveDependencies();
		assertEquals(2, entries.stream().filter(e -> e instanceof org.gradle.plugins.ide.eclipse.model.Library)
			.filter(e -> ((org.gradle.plugins.ide.eclipse.model.Library)e).getPath().contains("natives-")).count());
		String xml = Files.readString(temporary.resolve("build/ivy/org.lwjgl/lwjgl/ivy-3.3.1.xml"));
		assertTrue(xml.contains("m:classifier=\"natives-linux\""));
	}

	@Test void mavenOverrideUsesNativesOfTheResolvedVersion() throws Exception {
		jar("lwjgl-3.3.1.jar");
		jar("lwjgl-3.3.1-natives-linux.jar");
		Path module = temporary.resolve("maven/org/lwjgl/lwjgl/3.3.2");
		Files.createDirectories(module);
		Files.writeString(module.resolve("lwjgl-3.3.2.pom"), "<project><modelVersion>4.0.0</modelVersion><groupId>org.lwjgl</groupId><artifactId>lwjgl</artifactId><version>3.3.2</version></project>");
		Files.copy(temporary.resolve("bin/lib/lwjgl-3.3.1.jar"), module.resolve("lwjgl-3.3.2.jar"));
		Files.copy(temporary.resolve("bin/lib/lwjgl-3.3.1-natives-linux.jar"), module.resolve("lwjgl-3.3.2-natives-linux.jar"));
		var project = project();
		configure(project);
		// Use only local repositories, so this fixture cannot contact Maven Central.
		project.getRepositories().removeIf(repo -> !(repo instanceof org.gradle.api.artifacts.repositories.IvyArtifactRepository));
		project.getRepositories().maven(repo -> repo.setUrl(temporary.resolve("maven").toUri()));
		project.getDependencies().add("implementation", "org.lwjgl:lwjgl:3.3.2");
		var artifacts = project.getConfigurations().getByName("compileClasspath").getResolvedConfiguration().getResolvedArtifacts();
		assertEquals(2, artifacts.size());
		assertTrue(artifacts.stream().allMatch(a -> a.getModuleVersion().getId().getVersion().equals("3.3.2")));
		assertTrue(artifacts.stream().anyMatch(a -> "natives-linux".equals(a.getClassifier())));
	}

	@Test void modulesWithoutNativesKeepTheirExistingBehavior() throws Exception {
		jar("commons-io-2.21.0.jar");
		var project = project();
		assertTrue(scan(project).get("commons-io-2.21.0").natives().isEmpty());
		configure(project);
		assertEquals(1, project.getConfigurations().getByName("compileClasspath").getResolvedConfiguration().getResolvedArtifacts().size());
	}
}
