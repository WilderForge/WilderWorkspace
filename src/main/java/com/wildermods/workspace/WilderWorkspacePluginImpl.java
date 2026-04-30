package com.wildermods.workspace;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.file.FileTree;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.jvm.tasks.Jar;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry;
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
import com.wildermods.thrixlvault.utils.version.Version;
import com.wildermods.workspace.dependency.CapabilityHandler;
import com.wildermods.workspace.dependency.ProjectDependencyType;
import com.wildermods.workspace.dependency.WWProjectDependency;
import com.wildermods.workspace.tasks.ClearLocalRuntimeTask;
import com.wildermods.workspace.tasks.CopyLocalDependenciesToWorkspaceTask;
import com.wildermods.workspace.tasks.DecompileJarsTask;
import com.wildermods.workspace.tasks.GenNestedMetadataJarsTask;
import com.wildermods.workspace.tasks.GenerateLauncherMetadataTask;
import com.wildermods.workspace.tasks.JarJarTask;
import com.wildermods.workspace.tasks.eclipse.GenerateRunConfigurationTask;
import com.wildermods.workspace.util.ExceptionUtil;

import net.fabricmc.loom.build.nesting.NestableJarGenerationTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.net.ssl.HttpsURLConnection;

import org.gradle.api.DefaultTask;
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
	public static final String GAME_LIBS_REPO_NAME = "gameLibs";
	
	static {
		JavaPlugin.class.arrayType();
	}
	
	private Configuration implementation;
	private Configuration compileOnly;
	private Configuration compileClasspath;
	private Configuration testCompileClasspath;
	private Configuration fabricDep;
	private Configuration fabricImpl;
	private Configuration provider;
	private Configuration retrieveJson;
	private Configuration retrieveJsonProvider;
	private Configuration jsonDependencies;
	private Configuration resolvableImplementation;
	private Configuration excludedFabricDeps;
	private Configuration nest;
	private Configuration nestTransitive;
	
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

			//Set up capabilities FIRST (Ivy + Maven repositories, scan, generate Ivy, etc.)
			setupCapabilities(context);

			project.getLogger().log(LogLevel.INFO, "SETTING UP CONFIGURATIONS ");
			setupConfigurations(context);

			setupTasks(context);
			setupPostEvaluations(context);

			try {
				addJsonDependencies(context);
			} catch (Throwable t) {
				project.getLogger().error("Could not retrieve json dependencies...", t);
				jsonDependencies.getAllDependencies().clear();
			}
		} catch (Throwable t) {
			project.getLogger().log(LogLevel.ERROR, t.getMessage(), t);
			Throwable cause = ExceptionUtil.getInitialCause(t);
			if (cause instanceof NoClassDefFoundError) {
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
	@SuppressWarnings("resource")
	private void addPluginDependencies(Project project) {
		ScriptHandler buildscript = project.getBuildscript();
		{
			
			MavenArtifactRepository fabricRepository = buildscript.getRepositories().maven((c) -> {
				c.setUrl("https://maven.fabricmc.net");
			});
			MavenArtifactRepository mavenCentral = buildscript.getRepositories().mavenCentral();
			MavenArtifactRepository mavenLocal = buildscript.getRepositories().mavenLocal();
			MavenArtifactRepository sonatypeSnapshot = buildscript.getRepositories().maven((c) -> {
				c.setUrl("https://central.sonatype.com/repository/maven-snapshots/");
			});
			
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
	private void setupConfigurations(WWProjectContext context) {
		Project project = context.getProject();
		implementation = project.getConfigurations().getByName(ProjectDependencyType.implementation.name());
		nest = project.getConfigurations().create(ProjectDependencyType.nest.name(), configuration -> {
			configuration.setCanBeResolved(true);
			configuration.setCanBeConsumed(false);
			configuration.setTransitive(false);
		});
		nestTransitive = project.getConfigurations().create(ProjectDependencyType.nestTransitive.name(), configuration -> {
			configuration.setCanBeResolved(true);
			configuration.setCanBeConsumed(false);
			configuration.setTransitive(true);
			
			// Lazily mirror nest's dependencies, but force them to transitive = false
			configuration.withDependencies(deps -> {
				for (Dependency dep : nest.getDependencies()) {
					Dependency copy = dep.copy();
					if(copy instanceof ModuleDependency) {
						((ModuleDependency) copy).setTransitive(false);
					}
					deps.add(copy);
				}
			});
		});
		
		retrieveJson = project.getConfigurations().create(ProjectDependencyType.retrieveJson.name(), configuration -> {
			configuration.setCanBeResolved(true);
		});
		
		retrieveJsonProvider = project.getConfigurations().create(ProjectDependencyType.retrieveJsonProvider.name(), configuration -> {
			configuration.setCanBeConsumed(true);
		});
		
		jsonDependencies = project.getConfigurations().create(ProjectDependencyType.jsonDependency.name(), config -> {
			config.setCanBeResolved(true);
			config.setCanBeConsumed(true);
		});
		
		fabricDep = project.getConfigurations().create(ProjectDependencyType.fabricDep.name());
		fabricImpl = project.getConfigurations().create(ProjectDependencyType.fabricImpl.name());
		fabricImpl.setCanBeResolved(true);
		
		compileOnly = project.getConfigurations().getByName(ProjectDependencyType.compileOnly.name());
		
		provider = project.getConfigurations().create(ProjectDependencyType.provider.name());
		
		compileClasspath = project.getConfigurations().getByName(ProjectDependencyType.compileClasspath.name());
		compileClasspath.extendsFrom(nestTransitive);
		compileClasspath.extendsFrom(fabricDep);
		compileClasspath.extendsFrom(fabricImpl);
		compileClasspath.extendsFrom(provider);
		compileClasspath.extendsFrom(retrieveJson);
		compileClasspath.extendsFrom(jsonDependencies);
		
		testCompileClasspath = project.getConfigurations().getByName(ProjectDependencyType.testCompileClasspath.name());
		testCompileClasspath.extendsFrom(compileClasspath);
		
		resolvableImplementation = project.getConfigurations().create(ProjectDependencyType.resolvableImplementation.name(), configuration -> {
			configuration.extendsFrom(implementation);
			configuration.setCanBeResolved(true);
			configuration.setCanBeConsumed(true);
		});
		resolvableImplementation.extendsFrom(jsonDependencies);
		
		excludedFabricDeps = project.getConfigurations().create(ProjectDependencyType.excludedFabricDeps.name(), configuration -> {
			configuration.extendsFrom(project.getConfigurations().getByName(ProjectDependencyType.fabricDep.name()));
			configuration.extendsFrom(project.getConfigurations().getByName(ProjectDependencyType.fabricImpl.name()));
			configuration.setCanBeResolved(true);
			configuration.setCanBeConsumed(true);
		});
		
		DependencyHandler dependencies = project.getDependencies();
		for(WWProjectDependency dependency : WWProjectDependency.values()) {
			for(ProjectDependencyType type : dependency.getTypes()) {
				project.getLogger().log(LogLevel.INFO, "ADDING " + dependency.getModule() + ":" + dependency.getVersion() + " TO " + type + " CONFIGURATION");
				dependencies.add(type.name(), dependency.toString());
				//project.getLogger().log(LogLevel.INFO, "ADDING " + dependency.getModule() + ":" + dependency.getVersion() + " TO " + compileClasspath + " CONFIGURATION");
				//dependencies.add(compileClasspath.getName(), dependency.toString());
			}
		}
	}
	
	private void addDependenciesToConfiguration(Project project, Dependency superDep, JsonArray libraries, Configuration targetConfig) {
		DependencyHandler dependencies = project.getDependencies();

		for (JsonElement element : libraries) {
			JsonObject lib = element.getAsJsonObject();
			String notation = lib.get("name").getAsString();
			dependencies.add(targetConfig.getName(), notation);
			project.getLogger().info("[" + superDep + "] Registered subdependency " + notation);
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
			task.setPlatform(extension.getPlatform());
			task.setSteamUser(extension.getSteamUser());
			task.finalizedBy(project.getTasks().getByName("copyProjectDependencies"));
			task.getOutputs().cacheIf(t -> false);
			task.getOutputs().upToDateWhen(t -> false);
		});
		
		project.getTasks().register("decompileJars", DecompileJarsTask.class, task -> {
			task.setCompiledDir(extension.getGameDestDir());
			task.setDecompDir(extension.getDecompDir());
		});
		
		project.getTasks().register("clearLocalRuntime", ClearLocalRuntimeTask.class, task -> {
			task.setDecompDir(extension.getDecompDir());
			task.setDestDir(extension.getGameDestDir());
		});
		
		project.getTasks().register("setupDecompWorkspace", DefaultTask.class, task -> {
			task.getOutputs().upToDateWhen(taskOutput -> false);
			task.getOutputs().cacheIf(taskOutput -> false);
			
			task.dependsOn(project.getTasks().getByName("copyLocalDependenciesToWorkspace"));
			task.dependsOn(project.provider(() -> {
				DecompileJarsTask decompileTask = (DecompileJarsTask)project.getTasks().named("decompileJars").get();

				return decompileTask;
			}));


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
			task.into(Path.of(extension.getGameDestDir()).resolve("modDeps"));
		});
		
		project.getTasks().register("regenEclipseRuns", GenerateRunConfigurationTask.class, task -> {
			task.overwrite = true;
			task.dependsOn("eclipse");
		});
		
		project.getTasks().register("genEclipseRuns", GenerateRunConfigurationTask.class, task -> {
			
		});
		
		project.getTasks().register("generateLauncherMetadata", GenerateLauncherMetadataTask.class, task -> {
			task.getOutputs().upToDateWhen(taskOutput -> false);
			task.getOutputs().cacheIf(taskOutput -> false);
		});
		
		project.getTasks().register("prepareDevLaunch", GenNestedMetadataJarsTask.class, task -> {
			task.getOutputs().upToDateWhen(taskOutput -> false);
			task.getOutputs().cacheIf(taskOutput -> false);
			task.getOutputDir().set(
				project.getProjectDir().toPath().resolve("bin").resolve(".wilderworkspace").resolve("processedMods").toFile()
			);
		});
		
		TaskProvider<NestableJarGenerationTask> genNestJars = project.getTasks().register("generateNestableJars", NestableJarGenerationTask.class, task -> {
			task.from(nestTransitive);
			task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("nested-jars"));
			task.getOutputs().upToDateWhen(t -> false);
			
			task.doFirst(t -> {
				File outputDir = task.getOutputDirectory().get().getAsFile();
				if (outputDir.exists()) {
					project.delete(outputDir);
					project.mkdir(outputDir);
				}
			});
		});
		
		TaskProvider<JarJarTask> nestJarsTask = project.getTasks().register("nestJars", JarJarTask.class, task -> {
			task.dependsOn(project.getTasks().named("jar"));
			task.dependsOn(project.getTasks().named("generateNestableJars"));
			
			task.getMainJar().set(
				project.getTasks().named("jar", Jar.class).flatMap(Jar::getArchiveFile)	
			);
			
			task.getNestedJars().from(project.provider(() -> {
				File outputDir = project.getTasks()
					.named("generateNestableJars", NestableJarGenerationTask.class)
					.get()
					.getOutputDirectory()
					.get()
					.getAsFile();
				return project.fileTree(outputDir).matching(spec -> spec.include("*.jar"));
			}));
			
			//extract fabric.mod.json from the main jar, and place it in project.getLayout().getBuildDirectory().dir("nested-jars")
			
			task.doLast(t ->{
				File jsonOutputDir = project.getLayout().getBuildDirectory().dir("nested-jars").get().getAsFile();
				File mainJarFile = task.getMainJar().get().getAsFile();
				
				project.copy(copySpec -> {
					copySpec.from(project.zipTree(mainJarFile), spec -> {
						spec.include("fabric.mod.json");
						spec.rename(name -> "nested.fabric.mod.json");
					});
					copySpec.into(jsonOutputDir);
				});
			});
		});
		
		project.getTasks().named("assemble").configure(assemble -> assemble.dependsOn(nestJarsTask));
		project.getTasks().named("publish").configure(publish -> {
			publish.dependsOn(project.getTasks().named("assemble"));
			project.getTasks().named("jar").get().dependsOn(genNestJars);
		});
		
		if(project.getPlugins().hasPlugin("eclipse")) {
			project.getTasks().named("eclipseClasspath").configure(eclipse -> eclipse.dependsOn(genNestJars));
		}
		
		project.getTasks().named("jar", Jar.class, jar -> {
			jar.getOutputs().upToDateWhen(t -> false);
			jar.doFirst(t -> {
				File outputJar = jar.getArchiveFile().get().getAsFile();
				if (outputJar.exists()) {
					project.getLogger().lifecycle("Deleting existing jar: " + outputJar);
					outputJar.delete();
				}
			});
			
			Provider<FileTree> nestedJars = project.provider(() -> {
				if (project.getGradle().getTaskGraph().hasTask(":publish")) {
					return project.fileTree(project.getLayout().getBuildDirectory().dir("nested-jars").get(), spec -> {
						spec.include("*.jar");
						spec.include("fabric.mod.json");
					});
				} else {
					return project.files().getAsFileTree(); // empty FileTree
				}
			});

			jar.from(nestedJars, copySpec -> {
				copySpec.into("META-INF/jars");
				copySpec.rename(name -> {
					if(name.equals("nested.fabric.mod.json")) {
						return "fabric.mod.json";
					}
					return name;
				});
			});

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
		setupEclipsePlugin(context);
	}
	
	private void setupCapabilities(WWProjectContext context) {
		try {
			Project project = context.getProject();

			// 1. Add flatDir for scanning
			project.getRepositories().flatDir(repo -> {
				repo.setName(GAME_LIBS_REPO_NAME);
				repo.dir("bin/lib");
				repo.dir("bin");
			});

			// 2. Scan and generate Ivy repo
			CapabilityHandler handler = new CapabilityHandler(project);
			Map<String, ModuleInfo> flatDirModuleInfo = scanFlatDirModules(project, handler);
			project.getLogger().info("Found " + flatDirModuleInfo.size() + " flatDir modules");

			generateIvyRepository(project, flatDirModuleInfo);

			// 3. Add Ivy repository FIRST
			addIvyRepository(project);

			// 4. Add external Maven repositories (will come after Ivy)
			RepositoryHandler repos = project.getRepositories();
			repos.mavenLocal();
			repos.mavenCentral();
			repos.maven(repo -> repo.setUrl("https://maven.fabricmc.net/"));
			repos.maven(repo -> repo.setUrl("https://maven.wildermods.com/"));
			repos.maven(repo -> repo.setUrl("https://central.sonatype.com/repository/maven-snapshots/"));

			// 5. Register Maven alias rules (conflict resolution)
			registerMavenAliasRules(project, handler);

			// 6. Add compileOnly dependencies with version
			for (ModuleInfo info : flatDirModuleInfo.values()) {
				project.getDependencies().add("compileOnly",
					project.getDependencies().create(info.group() + ":" + info.artifact() + ":" + info.version())
				);
			}

			// 7. Remove the temporary flatDir
			project.getRepositories().remove(project.getRepositories().getByName(GAME_LIBS_REPO_NAME));

		} catch (Exception e) {
			throw new Error("Failed to set up local Ivy repository", e);
		}
	}
	
	private void addIvyRepository(Project project) {
		Path ivyRepoDir = project.getBuildDir().toPath().resolve("ivy").toAbsolutePath();
		// Construct explicit file: URL (double slash after colon)
		String path = ivyRepoDir.toString().replace('\\', '/');
		String repoUrl = path.startsWith("/") ? "file:" + path : "file:/" + path;
		project.getLogger().info("Ivy repository URL: " + repoUrl);
		project.getRepositories().ivy(repo -> {
			repo.setName("WildermythGameIvy");
			repo.setUrl(repoUrl);
			repo.patternLayout(layout -> {
				// Where Ivy descriptors are found
				layout.ivy("[organisation]/[module]/ivy-[revision].xml");
				// Where artifacts are found – matches the symlink location
				layout.artifact("[organisation]/[module]/[revision]/[artifact]-[revision].[ext]");
			});
			repo.metadataSources(sources -> sources.ivyDescriptor());
		});
	}
	
	private void registerMavenAliasRules(Project project, CapabilityHandler handler) {
		ComponentMetadataHandler components = project.getDependencies().getComponents();
		
		for (CapabilityHandler.CanonicalModule module : handler.modules.values()) {
			String canonicalGroup = module.getGroup();
			String canonicalName = module.getName();
			
			for (String mavenCoord : module.mavenAliases) {
				String[] parts = mavenCoord.split(":", 2);
				String mavenGroup = parts[0];
				String mavenName = parts[1];
				
				components.withModule(mavenGroup + ":" + mavenName, MavenAliasCapabilityRule.class, spec -> {
					spec.params(canonicalGroup, canonicalName);
				});
			}
		}
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
		
		/*
		 if(project.getPlugins().hasPlugin("eclipse")) {
			project.getTasks().register("addEclipseFabricJsonsToDevClasspath", AddToDevRuntimeClassPathTask.class, task -> {
				task.setAddedDir(project.getRootDir().toPath().resolve("build").resolve("dev-runtime").toFile());

			});
		}*/
		
		project.afterEvaluate(proj -> {
			if (project.getPlugins().hasPlugin("eclipse")) {

				EclipseModel eclipseModel = proj.getExtensions().getByType(EclipseModel.class);
				EclipseClasspath classpath = eclipseModel.getClasspath();
				
				classpath.file(xmlFileContent -> {
					xmlFileContent.getWhenMerged().add((classPathMerged) -> {
						Classpath c = (Classpath) classPathMerged;
						
						Set<String> compilePaths = project.getConfigurations().getByName(ProjectDependencyType.compileClasspath.name()).getResolvedConfiguration().getResolvedArtifacts().stream()
								.map(a -> a.getFile().getAbsoluteFile().toPath().normalize().toString())
								.collect(Collectors.toSet());
						
						Set<String> pPaths = new HashSet<>();
						{
							Set<String> providerPaths = project.getConfigurations()
									.getByName(ProjectDependencyType.provider.name())
									.getResolvedConfiguration().getResolvedArtifacts()
									.stream()
									.map(a -> a.getFile().getAbsoluteFile().toPath().normalize().toString())
									.collect(Collectors.toSet());
							
							Set<String> jsonProviderPaths = project.getConfigurations()
									.getByName(ProjectDependencyType.retrieveJsonProvider.name())
									.getResolvedConfiguration().getResolvedArtifacts()
									.stream()
									.map(a -> a.getFile().getAbsoluteFile().toPath().normalize().toString())
									.collect(Collectors.toSet());
							
							
							pPaths.addAll(providerPaths);
							pPaths.addAll(jsonProviderPaths);
						}
						List<String> knotClasspath = new ArrayList<>();
						
						Iterator<ClasspathEntry> it = (Iterator<ClasspathEntry>)(Object)c.getEntries().iterator();
						String path = null;
						while(it.hasNext()) {
							ClasspathEntry cpe = it.next();
							if(cpe instanceof AbstractClasspathEntry entry) {
								path = Path.of(entry.getPath()).toAbsolutePath().normalize().toString();
								boolean isProvider = false;
								
								project.getLogger().warn(entry.getClass().getCanonicalName());
								if(entry instanceof Library lib) {
	
									
									
									GameJars gameJar = GameJars.fromPathString(lib.getPath());
									if(gameJar != null) {
										project.getLogger().info("Found a game jar to add sources to: " + lib.getPath());
										FileReference source = c.fileReference(Path.of(extension.getDecompDir()).resolve("decomp").resolve(gameJar.getJarName()).normalize().toAbsolutePath().toFile());
										lib.setSourcePath(source);
										project.getLogger().info("Setting sources of " + gameJar + " to " + source.getPath());
									}
									
									if(pPaths.contains(lib.getPath())) {
										project.getLogger().info("Provider dependency found: " + path);
										continue;
									}
									
									if(compilePaths.contains(lib.getPath())) {
										project.getLogger().info("Ignoring compile only dlependency: " + path);
										continue;
									}
								}
								knotClasspath.add(path);
							}
							
							if(path != null) {
								project.getLogger().info("Not a provider dependency: " + path);
							}
							//it.remove();
						}
						
						project.getExtensions().getExtraProperties()
							.set("knotClasspath", String.join(File.pathSeparator, knotClasspath));
						project.getLogger().info("Setting knot classpath to: " + knotClasspath);
					});
				});

				project.getTasks().getByName("eclipse").finalizedBy(project.getTasks().getByName("genEclipseRuns"));
				
			} else {
				project.getLogger().warn("Eclipse plugin is not applied. The eclipse source attachment will not be configured, and the eclipse run configurations will not be generated.");
			}
		});
	}
	
	private void addJsonDependencies(WWProjectContext context) {
		Project project = context.getProject();

		RepositoryHandler repositories = project.getRepositories();

		project.getLogger().log(LogLevel.INFO, "Searching for json dependencies...");
		
		ArrayList<Dependency> dependencies = new ArrayList();
		dependencies.addAll(retrieveJson.getAllDependencies());
		dependencies.addAll(retrieveJsonProvider.getAllDependencies());
		
		// Iterate over dependencies in retrieveJson configuration
		for (Dependency jsonDependency : dependencies) {
			project.getLogger().info("Found json dependency: " + jsonDependency);
			// Construct the JSON file URL
			String jsonUrlPath = jsonDependency.getGroup().replace('.', '/') + "/"
					+ jsonDependency.getName() + "/" + jsonDependency.getVersion() + "/" 
					+ jsonDependency.getName() + "-" + jsonDependency.getVersion() + ".json";
			
			repositories.withType(MavenArtifactRepository.class).stream()
					.forEach((repository) -> {project.getLogger().log(LogLevel.INFO, "Found repository: " + repository.getUrl());});

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
						addDependenciesToConfiguration(project, jsonDependency, dependencyGroup, jsonDependencies);
						if(retrieveJsonProvider.getAllDependencies().contains(jsonDependency)) {
							addDependenciesToConfiguration(project, jsonDependency, dependencyGroup, provider);
						}
					}
				});
				
			} catch (Exception e) {
				throw new RuntimeException("Failed to process JSON dependency: " + jsonUrl, e);
			}
		}
			
		project.getLogger().log(LogLevel.INFO, "Found all json dependnecies");

	}
	
	private Map<String, ModuleInfo> scanFlatDirModules(Project project, CapabilityHandler handler) throws IOException {
		Map<String, ModuleInfo> result = new HashMap<>();
		FlatDirectoryArtifactRepository repo = (FlatDirectoryArtifactRepository)
				project.getRepositories().getByName(GAME_LIBS_REPO_NAME);

		for (File dir : repo.getDirs()) {
			Path path = dir.toPath();
			if (!Files.isDirectory(path)) continue;
			project.getLogger().info("Found DIRECTORY, searching inside " + dir);
			Files.list(path)
				.filter(p -> p.toString().endsWith(".jar"))
				.forEach(jar -> {
					project.getLogger().info("Found JAR " + jar);
					String fileName = jar.getFileName().toString();
					String moduleName = fileName.substring(0, fileName.length() - 4); // remove .jar
					handler.findModuleForFile(jar).ifPresent(module -> {
						project.getLogger().info("Creating modules for " + jar);
						module.fileAliases.stream()
							.filter(alias -> alias.matches(jar))
							.findFirst()
							.flatMap(alias -> alias.extractVersion(jar))
							.ifPresent(version -> {
								Path relative = project.getRootDir().toPath().relativize(jar);
								ModuleInfo m = new ModuleInfo(module.getGroup(), module.getName(), version, relative, project.getRootDir().toPath());
								result.put(moduleName, m);
								project.getLogger().info("Created module for " + jar + " (" + m + ")");
							});
					});
				});
		}
		return result;
	}
	
	private void generateIvyRepository(Project project, Map<String, ModuleInfo> moduleInfoMap) throws IOException {
		Path ivyRepoRoot = project.getBuildDir().toPath().resolve("ivy");
		// Clean previous
		if (Files.exists(ivyRepoRoot)) {
			Files.walk(ivyRepoRoot)
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
		}
		Files.createDirectories(ivyRepoRoot);

		Path projectRoot = project.getRootDir().toPath();

		for (ModuleInfo info : moduleInfoMap.values()) {
			Path sourceJar = projectRoot.resolve(info.relativeJarPath()).normalize();
			if (!Files.isRegularFile(sourceJar)) {
				project.getLogger().warn("Skipping missing JAR: " + sourceJar);
				continue;
			}

			// Module directory: [organisation]/[module]
			Path moduleDir = ivyRepoRoot.resolve(info.group()).resolve(info.artifact());
			Files.createDirectories(moduleDir);

			// Ivy descriptor (no <artifact url>)
			Path ivyFile = moduleDir.resolve("ivy-" + info.version() + ".xml");
			String ivyContent = String.format(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<ivy-module version=\"2.0\">\n" +
				"	<info organisation=\"%s\" module=\"%s\" revision=\"%s\"/>\n" +
				"	<publications>\n" +
				"		<artifact name=\"%s\" type=\"jar\" ext=\"jar\"/>\n" +
				"	</publications>\n" +
				"</ivy-module>",
				info.group(), info.artifact(), info.version(), info.artifact()
			);
			Files.writeString(ivyFile, ivyContent);

			// Create symlink at standard Ivy layout: [organisation]/[module]/[revision]/[artifact]-[revision].jar
			Path revisionDir = moduleDir.resolve(info.version().toString());
			Files.createDirectories(revisionDir);
			Path targetJar = revisionDir.resolve(info.artifact() + "-" + info.version() + ".jar");
			Files.deleteIfExists(targetJar);
			try {
				Files.createSymbolicLink(targetJar, sourceJar);
				project.getLogger().info("Created symlink: " + targetJar + " -> " + sourceJar);
			} catch (UnsupportedOperationException | IOException e) {
				project.getLogger().warn("Symlinks not supported, copying JAR instead.", e);
				Files.copy(sourceJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}
	
	private boolean canResolveUrl(WWProjectContext context, String urlString) {
		Project project = context.getProject();
		project.getLogger().log(LogLevel.INFO, "Checking URL: " + urlString);

		try {
			URL url = new URL(urlString);
			URLConnection connection = new URL(urlString).openConnection();

			if (url.getProtocol().equals("file")) {
				// Handle file URLs
				boolean exists = Files.exists(Path.of(url.toURI()));
				project.getLogger().log(LogLevel.INFO, "File URL check: " + (exists ? "File exists" : "File does NOT exist"));
				return exists;
			} else if (url.getProtocol().equals("https")) {
				// Handle HTTPS URLs
				HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
				httpsConnection.setRequestMethod("HEAD");
				int responseCode = httpsConnection.getResponseCode();
				project.getLogger().log(LogLevel.INFO, "Got response " + responseCode + " for " + url);
				return responseCode == HttpsURLConnection.HTTP_OK;
			} else {
				throw new IllegalArgumentException("Unsupported URL type (" + url.getProtocol() + ") for url " + url);
			}
		} catch (Exception e) {
			project.getLogger().log(LogLevel.INFO, "Could not resolve " + urlString, e);
			return false;
		}
	}

	// ---------- ComponentMetadataRule for Maven aliases ----------
	public static abstract class MavenAliasCapabilityRule implements ComponentMetadataRule {
		private final String canonicalGroup;
		private final String canonicalName;

		@Inject
		public MavenAliasCapabilityRule(String canonicalGroup, String canonicalName) {
			this.canonicalGroup = canonicalGroup;
			this.canonicalName = canonicalName;
		}

		@Override
		public void execute(ComponentMetadataContext context) {
			ComponentMetadataDetails details = context.getDetails();
			String version = details.getId().getVersion();
			details.allVariants(variant -> variant.withCapabilities(caps ->
				caps.addCapability(canonicalGroup, canonicalName, version)
			));
		}
	}
	
	public static record ModuleInfo(String group, String artifact, Version version, Path relativeJarPath, Path projectRoot) implements Serializable {}
}
