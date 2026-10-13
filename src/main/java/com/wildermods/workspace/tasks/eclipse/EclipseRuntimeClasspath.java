package com.wildermods.workspace.tasks.eclipse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.plugins.ide.eclipse.model.Output;
import org.gradle.plugins.ide.eclipse.model.ProjectDependency;
import org.gradle.plugins.ide.eclipse.model.SourceFolder;

/** Converts Eclipse entries into filesystem paths without changing the IDE's compile classpath. */
public record EclipseRuntimeClasspath(String knotClasspath, String providerClasspath, String projectOutputClasspath) {
	public static EclipseRuntimeClasspath calculate(Project project, Collection<ClasspathEntry> entries,
			File defaultOutput, Collection<File> providerFiles) {
		Set<Path> provider = providerFiles.stream().map(file -> normalized(file.toPath()))
			.collect(Collectors.toCollection(LinkedHashSet::new));
		Set<Path> knot = new LinkedHashSet<>();
		addEntries(project, entries, defaultOutput.toPath(), knot, new LinkedHashSet<>());
		knot.removeIf(path -> provider.contains(normalized(path)));
		Set<Path> outputs = projectOutputs(project, entries, defaultOutput.toPath());
		outputs.removeIf(path -> provider.contains(normalized(path)));
		return new EclipseRuntimeClasspath(join(knot), join(provider), join(outputs));
	}

	private static Set<Path> projectOutputs(Project project, Collection<ClasspathEntry> entries, Path defaultOutput) {
		Path fallback = entries.stream().filter(Output.class::isInstance).map(Output.class::cast)
			.map(output -> outputPath(project, output.getPath())).findFirst().orElse(defaultOutput.toAbsolutePath());
		Set<Path> outputs = new LinkedHashSet<>();
		for (ClasspathEntry entry : entries) {
			if (entry instanceof SourceFolder source
					&& !"true".equals(String.valueOf(source.getEntryAttributes().get("test")))) {
				outputs.add(source.getOutput() == null ? fallback : outputPath(project, source.getOutput()));
			}
		}
		return outputs;
	}

	private static void addEntries(Project project, Collection<ClasspathEntry> entries, Path defaultOutput,
			Set<Path> knot, Set<Project> visited) {
		if (!visited.add(project)) return;
		Path fallback = entries.stream().filter(Output.class::isInstance).map(Output.class::cast)
			.map(output -> outputPath(project, output.getPath())).findFirst().orElse(defaultOutput.toAbsolutePath());
		for (ClasspathEntry entry : entries) {
			if (entry instanceof AbstractClasspathEntry abstractEntry
					&& "true".equals(String.valueOf(abstractEntry.getEntryAttributes().get("test")))) continue;
			if (entry instanceof Library library) {
				knot.add(project.file(library.getPath()).toPath().toAbsolutePath().normalize());
			} else if (entry instanceof SourceFolder source) {
				knot.add(source.getOutput() == null ? fallback : outputPath(project, source.getOutput()));
			} else if (entry instanceof ProjectDependency dependency) {
				String name = dependency.getPath().replaceFirst("^/", "");
				Project referenced = project.getRootProject().getAllprojects().stream().filter(candidate -> {
					EclipseModel eclipse = candidate.getExtensions().findByType(EclipseModel.class);
					return name.equals(eclipse == null ? candidate.getName() : eclipse.getProject().getName());
				}).findFirst().orElseThrow(() -> new GradleException("Cannot resolve Eclipse project output: " + dependency.getPath()));
				EclipseModel eclipse = referenced.getExtensions().findByType(EclipseModel.class);
				if (eclipse == null) throw new GradleException("Referenced project needs the Eclipse plugin: " + name);
				addEntries(referenced, eclipse.getClasspath().resolveDependencies(),
					eclipse.getClasspath().getDefaultOutputDir().toPath(), knot, visited);
			}
			// Containers, variables and source paths are not filesystem runtime entries.
		}
	}

	private static Path outputPath(Project project, String path) {
		EclipseModel eclipse = project.getExtensions().findByType(EclipseModel.class);
		String name = eclipse == null ? project.getName() : eclipse.getProject().getName();
		String workspacePrefix = "/" + name + "/";
		if (path.startsWith(workspacePrefix)) path = path.substring(workspacePrefix.length());
		return project.file(path).toPath().toAbsolutePath().normalize();
	}

	private static Path normalized(Path path) {
		try { return path.toRealPath(); }
		catch (IOException e) { return path.toAbsolutePath().normalize(); }
	}

	private static String join(Set<Path> paths) {
		return paths.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
	}
}
