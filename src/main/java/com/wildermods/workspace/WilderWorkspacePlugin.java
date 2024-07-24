package com.wildermods.workspace;

import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.idea.IdeaPlugin;

import com.wildermods.workspace.tasks.ClearLocalDependenciesTask;
import com.wildermods.workspace.tasks.CopyLocalDependenciesToWorkspaceTask;
import com.wildermods.workspace.tasks.DecompileJarsTask;
import com.wildermods.workspace.util.ExceptionUtil;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Set;

import org.gradle.api.Plugin;

public class WilderWorkspacePlugin implements Plugin<Project> {
	public static final String VERSION = "@workspaceVersion@";
	
	public void apply(Project project) {
		project.getLogger().log(LogLevel.INFO, "Initializing WilderWorkspace plugin version " + VERSION);
		try {
			
			addDependencies(project);
			
			WilderWorkspaceExtension extension = project.getExtensions().create("wilderWorkspace", WilderWorkspaceExtension.class);
			extension.loadUserConfig();
			
			project.getTasks().register("copyLocalDependenciesToWorkspace", CopyLocalDependenciesToWorkspaceTask.class, task -> {
				task.setPlatform(extension.getPlatform());
				task.setPatchline(extension.getPatchline());
				task.setDestDir(extension.getGameDestDir());
			});
			
			project.getTasks().register("decompileJars", DecompileJarsTask.class, task -> {
				task.setCompiledDir(extension.getGameDestDir());
				task.setDecompDir(extension.getDecompDir());
			});
			
			project.getTasks().register("clearLocalDependencies", ClearLocalDependenciesTask.class, task -> {
				task.setDecompDir(extension.getDecompDir());
				task.setDestDir(extension.getGameDestDir());
			});
			
			project.getTasks().register("setupDecompWorkspace", CopyLocalDependenciesToWorkspaceTask.class, task -> {
				task.setOverwrite(false);
				project.getPlugins().withType(EclipsePlugin.class, eclipsePlugin -> {
					task.dependsOn(project.getTasks().named("eclipse"));
				});
				project.getPlugins().withType(IdeaPlugin.class, ideaPlugin -> {
					task.dependsOn(project.getTasks().named("idea"));
				});
				task.finalizedBy(project.provider(() -> {
					DecompileJarsTask decompileTask = (DecompileJarsTask)project.getTasks().named("decompileJars").get();
					return decompileTask;
				}));
			});
			
			project.getTasks().register("updateDecompWorkspace", CopyLocalDependenciesToWorkspaceTask.class, task -> {
				task.setPlatform(extension.getPlatform());
				task.setPatchline(extension.getPatchline());
				task.setDestDir(extension.getGameDestDir());
				task.setOverwrite(true);
				task.dependsOn(project.getTasks().named("clearLocalDependencies"));
				task.finalizedBy(project.provider(() -> {
					CopyLocalDependenciesToWorkspaceTask copyTask = (CopyLocalDependenciesToWorkspaceTask) project.getTasks().named("copyLocalDependenciesToWorkspace").get();
					copyTask.setOverwrite(true);
					copyTask.finalizedBy(project.provider(() -> {
						DecompileJarsTask decompileTask = (DecompileJarsTask)project.getTasks().named("decompileJars").get();
						return decompileTask;
					}));
					return copyTask;
				}));
			});
		}
		catch(Throwable t) {
			Throwable cause = ExceptionUtil.getInitialCause(t);
			if(cause instanceof NoClassDefFoundError) {
				throw new LinkageError("Required class not in classpath.", t);
			}
			throw new LinkageError("Failed to initialize WilderWorkspace " + VERSION, t);
		}
		
		project.getLogger().log(LogLevel.INFO, "Initialized WilderWorkspace plugin version {$workspaceVersion}");
	}
	
	private static void addDependencies(Project project) {
		ScriptHandler buildscript = project.getBuildscript();
		{
			
			MavenArtifactRepository fabricRepository = buildscript.getRepositories().maven((c) -> {
				c.setUrl("https://maven.fabricmc.net");
			});
			MavenArtifactRepository mavenCentral = buildscript.getRepositories().mavenCentral();
			MavenArtifactRepository mavenLocal = buildscript.getRepositories().mavenLocal();
			
			DependencyHandler dependencies = buildscript.getDependencies();
			for(Dependencies dependency : Dependencies.values()) {
				dependencies.add(ScriptHandler.CLASSPATH_CONFIGURATION, dependency.toString());
			}
		}
		
		buildscript.getConfigurations().forEach((config -> {
			Set<File> dependencies = config.resolve();
			VisitableURLClassLoader classLoader = (VisitableURLClassLoader) WilderWorkspacePlugin.class.getClassLoader();
			for(File dependency : dependencies) {
				try {
					classLoader.addURL(dependency.toURI().toURL());
				} catch (MalformedURLException e) {
					throw new LinkageError("Could not resolve dependency", e);
				}
			}
		}));
	}
	
}
