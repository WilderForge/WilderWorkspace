package com.wildermods.workspace.tasks.eclipse;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.plugins.ide.eclipse.model.Container;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.plugins.ide.eclipse.model.Output;
import org.gradle.plugins.ide.eclipse.model.SourceFolder;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.wildermods.workspace.tasks.CalculateRuntimeTask;

class EclipseLaunchTest {
	@TempDir Path temporary;

	private Project project() {
		var project = ProjectBuilder.builder().withName("LauncherMod").withProjectDir(temporary.toFile()).build();
		project.getPlugins().apply("java");
		project.getPlugins().apply("eclipse");
		return project;
	}

	private Library library(Project project, String path) {
		var file = new org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory().fromFile(project.file(path));
		return new Library(file);
	}

	@Test void runtimeUsesEclipseOutputsAndFiltersContainersSourcesTestsAndProviders() throws Exception {
		var project = project();
		var main = new SourceFolder("src/main/java", "bin/main");
		var resources = new SourceFolder("src/main/resources", "bin/main");
		var java8 = new SourceFolder("src/main/java8", "bin/java8");
		var test = new SourceFolder("src/test/java", "bin/test");
		test.getEntryAttributes().put("test", "true");
		var entries = List.<org.gradle.plugins.ide.eclipse.model.ClasspathEntry>of(main, resources, java8, test,
			new Output("/LauncherMod/bin/default"), new Container("org.eclipse.buildship.core.gradleclasspathcontainer"),
			new Container("org.eclipse.jdt.launching.JRE_CONTAINER"), library(project, "libs/provider.jar"), library(project, "libs/game.jar"));
		var runtime = EclipseRuntimeClasspath.calculate(project, entries, project.file("bin/default"), List.of(project.file("libs/provider.jar")));
		assertEquals(Set.of(temporary.resolve("bin/main").toString(), temporary.resolve("bin/java8").toString(),
			temporary.resolve("libs/game.jar").toString()), Set.of(runtime.knotClasspath().split(Pattern.quote(File.pathSeparator))));
		assertEquals(temporary.resolve("libs/provider.jar").toString(), runtime.providerClasspath());
	}

	@Test void projectModOutputsReachTheSingleDiscoveryArgumentBeforeCompilation() throws Exception {
		var project = project();
		var eclipse = project.getExtensions().getByType(EclipseModel.class);
		var test = new SourceFolder("src/test/java", "custom/test");
		test.getEntryAttributes().put("test", "true");
		var runtime = EclipseRuntimeClasspath.calculate(project,
			List.of(new SourceFolder("src/main/java", "custom/main & resources"),
				new SourceFolder("src/main/resources", "custom/main & resources"),
				new SourceFolder("src/main/java8", "custom/java8"), test),
			eclipse.getClasspath().getDefaultOutputDir(), List.of(temporary.resolve("provider.jar").toFile()));
		assertEquals(Set.of(temporary.resolve("custom/main & resources").toString(), temporary.resolve("custom/java8").toString()),
			Set.of(runtime.projectOutputClasspath().split(Pattern.quote(File.pathSeparator))));
		assertEquals(runtime.projectOutputClasspath(), runtime.knotClasspath());
		var extra = project.getExtensions().getExtraProperties();
		extra.set("knotClasspath", runtime.knotClasspath());
		extra.set("providerClasspath", runtime.providerClasspath());
		extra.set("projectOutputClasspath", runtime.projectOutputClasspath());
		var calculate = project.getTasks().create("runtime", CalculateRuntimeTask.class);
		calculate.generate();
		var task = project.getTasks().create("launch", GenerateRunConfigurationTask.class);
		task.generateLaunchConfig();
		String args = attribute(parse(Files.readString(task.getLaunchFile())), "org.eclipse.jdt.launching.VM_ARGUMENTS");
		assertEquals(1, args.split("-Dfabric.addMods=", -1).length - 1);
		String discovery = args.substring(args.indexOf("-Dfabric.addMods=") + "-Dfabric.addMods=".length(), args.lastIndexOf('"'));
		// Eclipse resolves workspace variables before the VM receives the property.
		String[] roots = discovery.replace("${workspace_loc:LauncherMod}", temporary.toString())
			.split(Pattern.quote(File.pathSeparator));
		assertEquals(4, roots.length);
		assertEquals(temporary.resolve("custom/main & resources").toString(), roots[0]);
		assertEquals(temporary.resolve("custom/java8").toString(), roots[1]);
		assertTrue(roots[2].endsWith("/build/nested-jars"));
		assertTrue(roots[3].endsWith("/bin/.wilderworkspace/processedMods"));
		assertFalse(discovery.contains("src/main"));
		assertFalse(discovery.contains("custom/test"));
		// The launch can be generated before Eclipse writes the compiled mod metadata.
		assertFalse(Files.exists(Path.of(roots[0])));
		Files.createDirectories(Path.of(roots[0]));
		Files.writeString(Path.of(roots[0]).resolve("fabric.mod.json"), "{\"schemaVersion\":1,\"id\":\"launcher\",\"version\":\"1.0\"}");
		assertTrue(Files.isRegularFile(Path.of(roots[0]).resolve("fabric.mod.json")));
	}

	@Test void fallbackOutputIsResolvedFromWorkspacePath() {
		var project = project();
		var runtime = EclipseRuntimeClasspath.calculate(project, List.of(new SourceFolder("src/main/java", null),
			new Output("/LauncherMod/custom/classes")), project.file("bin/default"), List.of());
		assertEquals(temporary.resolve("custom/classes").toString(), runtime.knotClasspath());
	}

	private void arguments(String providers, String knot) throws Exception {
		var props = new Properties();
		props.setProperty("providerClasspath", providers);
		props.setProperty("knotClasspath", knot);
		props.setProperty("vmArgs", "-Dmixin.debug=true -Dfabric.development=true " + CalculateRuntimeTask.quoteVmArgument("-Dknot.class.path=" + knot));
		Files.createDirectories(temporary.resolve("build"));
		try (var out = Files.newOutputStream(temporary.resolve("build/runArgs.properties"))) { props.store(out, "fixture"); }
	}

	private Document parse(String xml) throws Exception {
		return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
	}

	private String attribute(Document document, String key) {
		var children = document.getDocumentElement().getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i) instanceof Element element && key.equals(element.getAttribute("key"))) return element.getAttribute("value");
		}
		throw new AssertionError("Missing attribute: " + key);
	}

	@Test void generatedLaunchUsesOnlyProvidersAndEscapesPathsAndMementos() throws Exception {
		var project = project();
		String provider = temporary.resolve("provider & bootstrap.jar").toString();
		String knot = temporary.resolve("game & mods/classes").toString();
		arguments(provider + File.pathSeparator + provider, knot);
		var task = project.getTasks().create("launch", GenerateRunConfigurationTask.class);
		task.generateLaunchConfig();
		Document launch = parse(Files.readString(task.getLaunchFile()));
		assertEquals("false", attribute(launch, "org.eclipse.jdt.launching.DEFAULT_CLASSPATH"));
		var lists = launch.getElementsByTagName("listAttribute");
		Element classpath = null;
		for (int i = 0; i < lists.getLength(); i++) {
			var element = (Element) lists.item(i);
			if (element.getAttribute("key").equals("org.eclipse.jdt.launching.CLASSPATH")) classpath = element;
		}
		assertNotNull(classpath);
		var entries = classpath.getElementsByTagName("listEntry");
		assertEquals(2, entries.getLength());
		var jre = parse(((Element)entries.item(0)).getAttribute("value")).getDocumentElement();
		assertEquals("4", jre.getAttribute("type"));
		assertEquals("1", jre.getAttribute("path"));
		var archive = parse(((Element)entries.item(1)).getAttribute("value")).getDocumentElement();
		assertEquals(provider, archive.getAttribute("externalArchive"));
		assertEquals("2", archive.getAttribute("type"));
		assertEquals("3", archive.getAttribute("path"));
		String vmArgs = attribute(launch, "org.eclipse.jdt.launching.VM_ARGUMENTS");
		assertTrue(vmArgs.contains("\"-Dknot.class.path=" + knot + "\""));
		assertEquals(1, vmArgs.split("-Dmixin.debug=true", -1).length - 1);
		task.overwrite = true;
		arguments(temporary.resolve("replacement.jar").toString(), knot);
		task.generateLaunchConfig();
		assertFalse(Files.readString(task.getLaunchFile()).contains("provider &amp;"));
	}

	@Test void emptyProviderClasspathFailsInsteadOfRestoringEclipseDefault() throws Exception {
		var project = project();
		arguments("", temporary.resolve("bin/main").toString());
		var task = project.getTasks().create("launch", GenerateRunConfigurationTask.class);
		assertThrows(org.gradle.api.GradleException.class, task::generateLaunchConfig);
		assertFalse(Files.exists(task.getLaunchFile()));
	}

	@Test void removingGameClassesFromAppClasspathRestoresPackageAccess() throws Exception {
		Path bootstrap = Files.createDirectories(temporary.resolve("bootstrap"));
		Path game = Files.createDirectories(temporary.resolve("game"));
		Path source = Files.createDirectories(temporary.resolve("sources"));
		Files.writeString(source.resolve("Configuration.java"), "package fixture; public class Configuration { static String title = \"success\"; }");
		Files.writeString(source.resolve("Nice.java"), "package fixture; public class Nice { public static String run() { return Configuration.title; } }");
		Files.writeString(source.resolve("Bootstrap.java"), """
			import java.net.*; import java.nio.file.*;
			public class Bootstrap {
			  public static void main(String[] args) throws Exception {
			    try (var loader = new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()}) {
			      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			        if (name.equals("fixture.Nice")) { var c = findClass(name); if (resolve) resolveClass(c); return c; }
			        return super.loadClass(name, resolve);
			      }
			    }) { System.out.println(loader.loadClass("fixture.Nice").getMethod("run").invoke(null)); }
			  }
			}
			""");
		var compiler = ToolProvider.getSystemJavaCompiler();
		assertEquals(0, compiler.run(null, null, null, "-d", game.toString(), source.resolve("Configuration.java").toString(), source.resolve("Nice.java").toString()));
		assertEquals(0, compiler.run(null, null, null, "-d", bootstrap.toString(), source.resolve("Bootstrap.java").toString()));
		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		var broken = new ProcessBuilder(java, "-cp", bootstrap + File.pathSeparator + game, "Bootstrap", game.toString()).redirectErrorStream(true).start();
		String error = new String(broken.getInputStream().readAllBytes());
		assertNotEquals(0, broken.waitFor());
		assertTrue(error.contains("IllegalAccessError"), error);
		var fixed = new ProcessBuilder(java, "-cp", bootstrap.toString(), "Bootstrap", game.toString()).redirectErrorStream(true).start();
		String result = new String(fixed.getInputStream().readAllBytes());
		assertEquals(0, fixed.waitFor(), result);
		assertEquals("success", result.trim());
	}
}
