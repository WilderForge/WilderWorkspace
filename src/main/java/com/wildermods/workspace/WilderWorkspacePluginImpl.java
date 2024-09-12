package com.wildermods.workspace;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wildermods.workspace.tasks.ClearLocalRuntimeTask;
import com.wildermods.workspace.tasks.CopyLocalDependenciesToWorkspaceTask;
import com.wildermods.workspace.tasks.DecompileJarsTask;
import com.wildermods.workspace.util.ExceptionUtil;

import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

import org.gradle.api.GradleException;
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

public class WilderWorkspacePluginImpl implements Plugin<Object> {
	
	/** The version of the WilderWorkspace plugin. */
	public static final String VERSION = "@workspaceVersion@";
	
	private Configuration implementation;
	private Configuration fabricDep;
	private Configuration fabricImpl;
	private Configuration retrieveJson;
	private Configuration resolvableImplementation;
	private Configuration excludedFabricDeps;
	
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
			
			setupRepositories(context);
			
			project.getLogger().log(LogLevel.INFO, "SETTING UP CONFIGURATIONS ");
			setupConfigurations(context);
			
			setupTasks(context);
			
			setupPostEvaluations(context);
		}
		catch(Throwable t) {
			Throwable cause = ExceptionUtil.getInitialCause(t);
			if(cause instanceof NoClassDefFoundError) {
				throw new LinkageError("Required class not in classpath.", t);
			}
			throw new LinkageError("Failed to apply WilderWorkspace " + VERSION, t);
		}
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
	private void addPluginDependencies(Project project) {
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
	
	private void setupRepositories(WWProjectContext context) {
		Project project = context.getProject();
		RepositoryHandler repositories = project.getRepositories();
		repositories.add(repositories.mavenCentral());
		repositories.add(repositories.maven((repo) -> {
			repo.setUrl("https://maven.fabricmc.net/");
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
	private void setupConfigurations(WWProjectContext context) {
		Project project = context.getProject();
		implementation = project.getConfigurations().getByName(ProjectDependencyType.implementation.name());
		fabricDep = project.getConfigurations().create(ProjectDependencyType.fabricDep.name());
		fabricImpl = project.getConfigurations().create(ProjectDependencyType.fabricImpl.name());
		retrieveJson = project.getConfigurations().create(ProjectDependencyType.retrieveJson.name(), configuration -> {
			configuration.setCanBeResolved(true);
			configuration.setCanBeConsumed(false);
		});
		
		resolvableImplementation = project.getConfigurations().create(ProjectDependencyType.resolvableImplementation.name(), configuration -> {
			configuration.extendsFrom(implementation);
			configuration.setCanBeResolved(true);
			configuration.setCanBeConsumed(false);
		});
		
		excludedFabricDeps = project.getConfigurations().create(ProjectDependencyType.excludedFabricDeps.name(), configuration -> {
			configuration.extendsFrom(project.getConfigurations().getByName(ProjectDependencyType.fabricDep.name()));
			configuration.extendsFrom(project.getConfigurations().getByName(ProjectDependencyType.fabricImpl.name()));
			configuration.setCanBeResolved(true);
			configuration.setCanBeConsumed(false);
		});
		
		DependencyHandler dependencies = project.getDependencies();
		for(WWProjectDependency dependency : WWProjectDependency.values()) {
			project.getLogger().log(LogLevel.INFO, "ADDING " + dependency.getModule() + ":" + dependency.getVersion() + " TO " + dependency.getType() + " CONFIGURATION");
			dependencies.add(dependency.getType().name(), dependency.toString());
			project.getLogger().log(LogLevel.INFO, "ADDING " + dependency.getModule() + ":" + dependency.getVersion() + " TO " + implementation + " CONFIGURATION");
			dependencies.add(implementation.getName(), dependency.toString());
		}
		
		project.getLogger().log(LogLevel.INFO, "ADDING JSON DEPENDENCIES " + VERSION);
		addJsonDependencies(context);
		
	}
	
	private void addJsonDependencies(WWProjectContext context) {
		Project project = context.getProject();
		RepositoryHandler repositories = project.getRepositories();
		Set<String> addedRepositories = new HashSet<>();

		project.getLogger().log(LogLevel.INFO, "Searching for json dependencies...");
		
		// Iterate over dependencies in retrieveJson configuration
		for (Dependency jsonDependency : retrieveJson.getAllDependencies()) {
			project.getLogger().info("Found json dependency: " + jsonDependency);
			// Construct the JSON file URL
			String jsonUrlPath = jsonDependency.getGroup().replace('.', '/') + "/"
					+ jsonDependency.getName() + "/" + jsonDependency.getVersion() + "/" 
					+ jsonDependency.getName() + "-" + jsonDependency.getVersion() + ".json";

			Optional<MavenArtifactRepository> repositoryOpt = repositories.withType(MavenArtifactRepository.class).stream()
					.filter(repo -> canResolveUrl(context, repo.getUrl().toString() + jsonUrlPath))
					.findFirst();

			if (!repositoryOpt.isPresent()) {
				throw new RuntimeException("Unable to determine the repository URL for the JSON dependency: " 
					+ jsonDependency.getGroup() + ":" + jsonDependency.getName() + ":" + jsonDependency.getVersion());
			}

			// Get the repository URL
			MavenArtifactRepository repository = repositoryOpt.get();
			String jsonUrl = repository.getUrl().toString() + jsonUrlPath;

			try {
				// Download and parse the JSON file
				InputStreamReader reader = new InputStreamReader(new URL(jsonUrl).openStream());
				JsonObject dependencyJsonData = JsonParser.parseReader(reader).getAsJsonObject();

				JsonObject libraries = dependencyJsonData.getAsJsonObject("libraries");
				libraries.entrySet().forEach((entry) -> {
					if(entry.getValue().isJsonArray()) {
						JsonArray dependencyGroup = entry.getValue().getAsJsonArray();
						addDependenciesToFabricDep(project, jsonDependency, dependencyGroup);
					}
				});
				
			} catch (Exception e) {
				throw new RuntimeException("Failed to process JSON dependency: " + jsonUrl, e);
			}
		}
		
		project.getLogger().log(LogLevel.INFO, "Found all json dependnecies");
	}
	
	private void addDependenciesToFabricDep(Project project, Dependency superDependency, JsonArray libraries) {
		DependencyHandler dependencies = project.getDependencies();
		for (JsonElement element : libraries) {
			JsonObject lib = element.getAsJsonObject();
			String dependencyNotation = lib.get("name").getAsString();

			// Add dependency to fabricDep and implementation configuration
			try {
				dependencies.add(fabricDep.getName(), dependencyNotation);
				dependencies.add(implementation.getName(), dependencyNotation);
				project.getLogger().log(LogLevel.INFO, "[" + superDependency.getGroup() + ":" + superDependency.getName() + "]: Registered subdependency " + dependencyNotation);

			}
			catch(Exception e) {
				throw new RuntimeException("[" + superDependency.getGroup() + ":" + superDependency.getName() + "]: Could not register dependency " + dependencyNotation, e);
			}
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
	private void setupTasks(WWProjectContext context) {
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
			task.finalizedBy(project.provider(() -> {
				DecompileJarsTask decompileTask = (DecompileJarsTask)project.getTasks().named("decompileJars").get();
				return decompileTask;
			}));
			task.finalizedBy(project.getTasks().getByName("copyLocalDependenciesToWorkspace"));
			project.getPlugins().withType(EclipsePlugin.class, eclipsePlugin -> {
				task.finalizedBy(project.getTasks().named("eclipse"));
			});
			project.getPlugins().withType(IdeaPlugin.class, ideaPlugin -> {
				task.finalizedBy(project.getTasks().named("idea"));
			});
		});
		
		//This task isn't implemented correctly yet, so we're not including it.
		
		/**
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
		*/
		
		project.getTasks().register("copyFabricDependencies", Copy.class, task -> {
			task.from(fabricDep);
			task.into(Path.of(extension.getGameDestDir()).resolve("fabric"));
		});
		
		project.getTasks().register("copyFabricImplementors", Copy.class, task -> {
			task.from(fabricImpl);
			task.into(extension.getGameDestDir());
		});
		
		project.getTasks().register("copyProjectDependencies", Copy.class, task -> {
			// Lazy evaluation of the resolvableImplementation configuration
			task.from(project.provider(() -> project.files(resolvableImplementation).getAsFileTree().matching(copySpec -> {
				// Lazy evaluation of excludedFabricDeps configuration
				Set<File> exclusionFiles = project.provider(() -> project.files(excludedFabricDeps).getFiles()).get();

				// Exclude the files found in the exclusion configuration
				copySpec.exclude(fileTreeElement -> exclusionFiles.contains(fileTreeElement.getFile()));
			})));

			// Set the destination directory
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
	private void setupPostEvaluations(WWProjectContext context) {
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
	private void addWorkspaceDependencies(WWProjectContext context) {
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
	private void setupEclipsePlugin(WWProjectContext context) {
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
	
	private boolean canResolveUrl(WWProjectContext context, String url) {
		try {
			context.getProject().getLogger().log(LogLevel.INFO, "Checking " + url);
			HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
			connection.setRequestMethod("HEAD");
			int responseCode = connection.getResponseCode();
			context.getProject().getLogger().log(LogLevel.INFO, "Got response " + responseCode);
			return responseCode == HttpsURLConnection.HTTP_OK;
		} catch (Exception e) {
			context.getProject().getLogger().log(LogLevel.INFO, "Could not open " + url, e);
			return false;
		}
	}
	
}
