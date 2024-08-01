package com.wildermods.workspace;

import org.gradle.api.artifacts.Dependency;
import static com.wildermods.workspace.ProjectDependencyType.*;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A WWProject dependency represents a dependency common to all projects made
 * using WilderWorkspace.
 * <p>
 * For example, all projects made with the same WilderWorkspace version will 
 * should be compiled against the same version of SpongePowered Mixin.
 * <p>
 * @see {@link PluginDependency} for dependencies the  WilderWorkspace gradle
 * plugin itself needs in order to be built and run.
 */
public enum WWProjectDependency implements Dependency {
		
		commonsText(fabricDep, "org.apache.commons", "commons-text", "@commonsTextVersion@"),
		accessWidener(fabricDep, "net.fabricmc", "access-widener", "@accessWidenerVersion@"),
		tinyMappingsParser(fabricDep, "net.fabricmc", "tiny-mappings-parser", "@tinyMappingsVersion@"),
		mixin(fabricDep, "org.spongepowered", "mixin", "@mixinVersion@"),
		guava(fabricDep, "com.google.guava", "guava", "@guavaVersion@"),
		gson(fabricDep, "com.google.code.gson", "gson", "@gsonVersion@"),
		gameProvider(fabricDep, "com.wildermods", "provider", "@providerVersion@", "https://wildermods.com/WildermythGameProvider.git"),
		
		asm(fabricDep, "org.ow2.asm", "asm", "@asmVersion@"),
		asmAnalysis(fabricDep, "org.ow2.asm", "asm-analysis", "@asmVersion@"),
		asmCommons(fabricDep, "org.ow2.asm", "asm-commons", "@asmVersion@"),
		asmTree(fabricDep, "org.ow2.asm", "asm-tree", "@asmVersion@"),
		asmUtil(fabricDep, "org.ow2.asm", "asm-util", "@asmVersion@"),
		
		
		fabricLoader(fabricImpl, "net.fabricmc", "fabric-loader", "@fabricLoaderVersion@"),
		wilderLoader(fabricImpl, "com.wildermods", "wilderloader", "@wilderLoaderVersion@", "https://wildermods.com/WilderLoader.git")
		
	;

	private final ProjectDependencyType type;
	private final String groupID;
	private final String artifact;
	private final String version;
	private final String dependencyString;
	private final URI gitRepo;
	private String reason;

	
	private WWProjectDependency(ProjectDependencyType type, String groupID, String artifact, String version) {
		this(type, groupID, artifact, version, null);
	}
	
	private WWProjectDependency(ProjectDependencyType type, String groupID, String artifact, String version, String gitRepo) {
		this.type = type;
		this.groupID = groupID;
		this.artifact = artifact;
		this.version = version;
		this.dependencyString = String.join(":", groupID, artifact, version);
		try {
			if(gitRepo != null) {
				this.gitRepo = new URI(gitRepo);
			}
			else {
				this.gitRepo = null;
			}
		} catch (URISyntaxException e) {
			throw new AssertionError(e);
		}
	}

	public String toString() {
		return dependencyString;
	}

	@Override
	public void because(String reason) {
		this.reason = reason;
	}

	@Override
	public boolean contentEquals(Dependency dependency) {
		return dependency == this;
	}

	@Override
	public Dependency copy() {
		throw new AssertionError();
	}
	
	public ProjectDependencyType getType() {
		return type;
	}

	@Override
	public String getGroup() {
		return groupID;
	}

	@Override
	public String getName() {
		return artifact;
	}

	@Override
	public String getReason() {
		return reason;
	}
	
	public String getModule() {
		return String.join(":", getGroup(), getName());
	}
	
	public URI getRepo() {
		return gitRepo;
	}

	@Override
	public String getVersion() {
		return version;
	}

}
