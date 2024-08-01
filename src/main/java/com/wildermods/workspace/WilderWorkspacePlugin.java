package com.wildermods.workspace;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.Copy;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.FileReference;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.vcs.SourceControl;

import com.wildermods.workspace.tasks.ClearLocalDependenciesTask;
import com.wildermods.workspace.tasks.CopyLocalDependenciesToWorkspaceTask;
import com.wildermods.workspace.tasks.DecompileJarsTask;
import com.wildermods.workspace.util.ExceptionUtil;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Set;

import org.gradle.api.Plugin;

public class WilderWorkspacePlugin implements Plugin<Object> {
	
	public static final String VERSION = "@workspaceVersion@";
	
	public void apply(Object object) {
		if(object instanceof Project) {
			apply((Project)object);
		}
		else if (object instanceof Settings) {
			apply((Settings)object);
		}
		else {
			throw new Error();
		}
	}
	
	public void apply(Project project) {
		
		project.getLogger().log(LogLevel.INFO, "Initializing WilderWorkspace PROJECT plugin version " + VERSION);
		try {

			addPluginDependencies(project);
			
			WilderWorkspaceExtension extension = project.getExtensions().create("wilderWorkspace", WilderWorkspaceExtension.class);
			extension.loadUserConfig();
			
			WWProjectContext context = new WWProjectContext(project, extension) {};
			
			setupConfigurations(context);
			
			setupTasks(context);
			
			setupPostEvaluations(context);
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
	
	public void apply(Settings settings) {
		SourceControl sourceControl = settings.getSourceControl();
		for(WWProjectDependency dependency : WWProjectDependency.values()) {
			if(dependency.getRepo() != null) {
				sourceControl.gitRepository(dependency.getRepo(), repo -> {
					repo.producesModule(dependency.getModule());
				});
			}
		}
	}
	
	private static void addPluginDependencies(Project project) {
		ScriptHandler buildscript = project.getBuildscript();
		{
			
			MavenArtifactRepository fabricRepository = buildscript.getRepositories().maven((c) -> {
				c.setUrl("https://maven.fabricmc.net");
			});
			MavenArtifactRepository mavenCentral = buildscript.getRepositories().mavenCentral();
			MavenArtifactRepository mavenLocal = buildscript.getRepositories().mavenLocal();
			
			DependencyHandler dependencies = buildscript.getDependencies();
			for(PluginDependency dependency : PluginDependency.values()) {
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
	
	private static void setupConfigurations(WWProjectContext context) {
		Project project = context.getProject();
		Configuration fabricDep = project.getConfigurations().create(ProjectDependencyType.fabricDep.name());
		Configuration fabricImpl = project.getConfigurations().create(ProjectDependencyType.fabricImpl.name());
	}
	
	private static void setupTasks(WWProjectContext context) {
		Project project = context.getProject();
		WilderWorkspaceExtension extension = context.getWWExtension();
		
		project.getTasks().register("copyLocalDependenciesToWorkspace", CopyLocalDependenciesToWorkspaceTask.class, task -> {
			task.setPlatform(extension.getPlatform());
			task.setPatchline(extension.getPatchline());
			task.setDestDir(extension.getGameDestDir());
			task.finalizedBy(project.getTasks().getByName("copyFabricDependencies"));
			task.finalizedBy(project.getTasks().getByName("copyFabricImplementors"));
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
			task.finalizedBy(project.getTasks().getByName("copyFabricDependencies"));
			task.finalizedBy(project.getTasks().getByName("copyFabricImplementors"));
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
		
		project.getTasks().register("copyFabricDependencies", Copy.class, task -> {
			Configuration fabricDep = context.getProject().getConfigurations().getByName(ProjectDependencyType.fabricDep.name());
			task.from(fabricDep);
			task.into(Path.of(extension.getGameDestDir()).resolve("fabric"));
		});
		
		project.getTasks().register("copyFabricImplementors", Copy.class, task -> {
			Configuration fabricImpl = context.getProject().getConfigurations().getByName(ProjectDependencyType.fabricImpl.name());
			task.from(fabricImpl);
			task.into(extension.getGameDestDir());
		});
	}
	
    
	private static void setupPostEvaluations(WWProjectContext context) {
		addWorkspaceDependencies(context);
        setupEclipsePlugin(context);
    }
	
	private static void addWorkspaceDependencies(WWProjectContext context) {
		Project project = context.getProject();
		project.afterEvaluate((proj -> {
			DependencyHandler dependencyHandler = proj.getDependencies();
			
			
			
			for(WWProjectDependency dependency : WWProjectDependency.values()) {
				dependencyHandler.add(dependency.getType().toString(), dependency.toString());
			}
		}));
	}
    
	@SuppressWarnings({ "unchecked"})
    private static void setupEclipsePlugin(WWProjectContext context) {
        Project project = context.getProject();
        WilderWorkspaceExtension extension = context.getWWExtension();
        project.afterEvaluate(proj -> {
            if (project.getPlugins().hasPlugin("eclipse")) {
                EclipseModel eclipseModel = proj.getExtensions().getByType(EclipseModel.class);
                EclipseClasspath classpath = eclipseModel.getClasspath();

                classpath.file(xmlFileContent -> {
                	xmlFileContent.getWhenMerged().add((classPathMerged) -> {
                		Classpath c = (Classpath) classPathMerged;
                        for (ClasspathEntry entry : c.getEntries()) {
                        	project.getLogger().warn(entry.getClass().getCanonicalName());
                        	if(entry instanceof Library) {
                        		Library lib = (Library) entry;
                        		GameJars gameJar = GameJars.fromPathString(lib.getPath());
                        		if(gameJar != null) {
                        			project.getLogger().info("Found a game jar to add sources to: " + lib.getPath());
                        			FileReference source = c.fileReference(Path.of(extension.getDecompDir()).resolve("decomp").resolve(gameJar.getJarName()).normalize().toAbsolutePath().toFile());
                        			lib.setSourcePath(source);
                        			project.getLogger().info("Setting sources of " + gameJar + " to " + source.getPath());
                        		}
                        	}
                        }
                	});
                });

            } else {
                project.getLogger().warn("Eclipse plugin is not applied. The eclipse source attachment will not be configured.");
            }
        });
    }
    
}
