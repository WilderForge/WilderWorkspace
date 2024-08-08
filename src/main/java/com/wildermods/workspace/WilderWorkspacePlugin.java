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

import com.wildermods.workspace.tasks.ClearLocalRuntimeTask;
import com.wildermods.workspace.tasks.CopyLocalDependenciesToWorkspaceTask;
import com.wildermods.workspace.tasks.DecompileJarsTask;
import com.wildermods.workspace.util.ExceptionUtil;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Set;

import org.gradle.api.Plugin;

/**
 * A Gradle plugin for setting up and managing the WilderWorkspace development environment.
 * <p>
 * This plugin configures various tasks and settings required for working with the WilderWorkspace project,
 * including setting up dependencies, configuring tasks, and integrating with IDEs like Eclipse and IntelliJ IDEA.
 * </p>
 * <p>
 * It supports:
 * <ul>
 * <li>Adding plugin dependencies</li>
 * <li>Setting up configurations and tasks</li>
 * <li>Configuring IDE plugins (Eclipse and IntelliJ IDEA)</li>
 * <li>Handling post-evaluation of the project for dependency management</li>
 * </ul>
 * </p>
 */

public class WilderWorkspacePlugin implements Plugin<Object> {
	
	/** The version of the WilderWorkspace plugin. */
	public static final String VERSION = "@workspaceVersion@";
	
    /**
     * Applies the plugin to either a {@link Project} or {@link Settings}.
     *
     * @param object the object to apply the plugin to
     * @throws Error if the object is neither a {@link Project} nor a {@link Settings}
     */
	public void apply(Object object) {
		if(object instanceof Project) {
			apply((Project)object);
		}
		else if (object instanceof Settings) {
			apply((Settings)object);
		}
		else {
			throw new Error("WilderWorkspacePlugin can only be applied to projects and settings");
		}
	}
	
    /**
     * Applies the plugin to the project.
     * <p>
     * This method initializes the plugin, sets up dependencies, creates the WilderWorkspace extension,
     * configures tasks, and handles post-evaluation tasks.
     * </p>
     *
     * @param project the Gradle project to apply the plugin to
     */
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
		
		project.getLogger().log(LogLevel.INFO, "Initialized WilderWorkspace plugin version " + VERSION);
	}
	
    /**
     * Applies the plugin to the given settings.
     * <p>
     * This method sets up source control repositories for project dependencies as defined in {@link WWProjectDependency}.
     * </p>
     *
     * @param settings the Gradle settings to apply the plugin to
     */
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
	
    /**
     * Adds plugin dependencies to the project's build script classpath.
     * <p>
     * This method configures repositories and adds dependencies required for the plugin.
     * </p>
     *
     * @param project the Gradle project
     */
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
	
    /**
     * Sets up configurations required by the plugin.
     * <p>
     * This method creates configurations for Fabric dependencies and implementations.
     * </p>
     *
     * @param context the project context
     */
	private static void setupConfigurations(WWProjectContext context) {
		Project project = context.getProject();
		Configuration fabricDep = project.getConfigurations().create(ProjectDependencyType.fabricDep.name());
		Configuration fabricImpl = project.getConfigurations().create(ProjectDependencyType.fabricImpl.name());
		
		project.getConfigurations().create(ProjectDependencyType.resolvableImplementation.name(), configuration -> {
		    configuration.extendsFrom(project.getConfigurations().getByName("implementation"));
		    configuration.setCanBeResolved(true);
		    configuration.setCanBeConsumed(false);
		});
		
		project.getConfigurations().create(ProjectDependencyType.excludedFabricDeps.name(), configuration -> {
		    configuration.extendsFrom(project.getConfigurations().getByName(ProjectDependencyType.fabricDep.name()));
		    configuration.extendsFrom(project.getConfigurations().getByName(ProjectDependencyType.fabricImpl.name()));
		    configuration.setCanBeResolved(true);
		    configuration.setCanBeConsumed(false);
		});
		
		DependencyHandler dependencies = project.getDependencies();
		for(WWProjectDependency dependency : WWProjectDependency.values()) {
			dependencies.add("implementation", dependency.toString());
		}
	}
	
    /**
     * Sets up tasks for the plugin.
     * <p>
     * This method registers tasks for copying local dependencies, decompiling JARs, and configuring the workspace.
     * </p>
     *
     * @param context the project context
     */
	private static void setupTasks(WWProjectContext context) {
		Project project = context.getProject();
		WilderWorkspaceExtension extension = context.getWWExtension();
		
		project.getTasks().register("copyLocalDependenciesToWorkspace", CopyLocalDependenciesToWorkspaceTask.class, task -> {
			task.setPlatform(extension.getPlatform());
			task.setPatchline(extension.getPatchline());
			task.setDestDir(extension.getGameDestDir());
			task.finalizedBy(project.getTasks().getByName("copyFabricDependencies"));
			task.finalizedBy(project.getTasks().getByName("copyFabricImplementors"));
			task.finalizedBy(project.getTasks().getByName("copyProjectDependencies"));
		});
		
		project.getTasks().register("decompileJars", DecompileJarsTask.class, task -> {
			task.setCompiledDir(extension.getGameDestDir());
			task.setDecompDir(extension.getDecompDir());
		});
		
		project.getTasks().register("clearLocalRuntime", ClearLocalRuntimeTask.class, task -> {
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
		
		project.getTasks().register("copyProjectDependencies", Copy.class, task -> {
		    Configuration resolvableImplementation = project.getConfigurations().getByName(ProjectDependencyType.resolvableImplementation.name());
		    Configuration exclusionConfiguration = project.getConfigurations().getByName(ProjectDependencyType.excludedFabricDeps.name());

		    // Get the files from the exclusion configuration
		    Set<File> exclusionFiles = exclusionConfiguration.getResolvedConfiguration().getFiles();

		    task.from(resolvableImplementation, copySpec -> {
		        // Exclude files that are in the exclusion configuration
		        copySpec.exclude(fileTreeElement -> exclusionFiles.contains(fileTreeElement.getFile()));
		    });
		    
		    task.into(Path.of(extension.getGameDestDir()).resolve("fabric"));
		});
	}
	
    /**
     * Sets up post-evaluation tasks for the plugin.
     * <p>
     * This method adds workspace dependencies and configures IDE plugins.
     * </p>
     *
     * @param context the project context
     */
	private static void setupPostEvaluations(WWProjectContext context) {
		addWorkspaceDependencies(context);
        setupEclipsePlugin(context);
    }
	
    /**
     * Adds workspace dependencies to the project after evaluation.
     * <p>
     * This method ensures that project dependencies are added to the correct configurations.
     * </p>
     *
     * @param context the project context
     */
	private static void addWorkspaceDependencies(WWProjectContext context) {
		Project project = context.getProject();
		project.afterEvaluate((proj -> {
			DependencyHandler dependencyHandler = proj.getDependencies();
			
			
			
			for(WWProjectDependency dependency : WWProjectDependency.values()) {
				dependencyHandler.add(dependency.getType().toString(), dependency.toString());
			}
		}));
	}
    
    /**
     * Configures the Eclipse plugin after project evaluation.
     * <p>
     * This method integrates with the Eclipse plugin to set source paths for game JARs.
     * </p>
     *
     * @param context the project context
     */
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
