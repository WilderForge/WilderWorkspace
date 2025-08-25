package com.wildermods.workspace;

import com.wildermods.workspace.dependency.WWProjectDependency;

/**
 * A PluginDependency represents a dependency that is required to build and run
 * the WilderWorkspace gradle plugin itself.
 * 
 * @see {@link WWProjectDependency} for dependencies that modded environments made
 * using WilderWorkspace will need in order to run
 */
enum PluginDependency {

		COMMONS_IO("commons-io", "commons-io", "@commonsIOVersion@"),
		COMMONS_LANG("org.apache.commons", "commons-lang3", "@commonsLangVersion@"),
		COMMONS_TEXT("org.apache.commons", "commons-text", "@commonsTextVersion@"),
		GSON("com.google.code.gson", "gson", "@gsonVersion@"),
		LOOM("net.fabricmc", "fabric-loom", "@loomVersion@"),
		VINEFLOWER("org.vineflower", "vineflower", "@vineFlowerVersion@"),
		THRIXLVAULT("com.wildermods", "thrixlvault", "@thrixlvaultVersion@")
		
	;
	
	private final String groupID;
	private final String artifact;
	private final String version;
	private final String dependencyString;
	private String reason;

	
	private PluginDependency(String groupID, String artifact, String version) {
		this.groupID = groupID;
		this.artifact = artifact;
		this.version = version;
		this.dependencyString = String.join(":", groupID, artifact, version);
	}
	
	public String toString() {
		return dependencyString;
	}


	public String getGroup() {
		return groupID;
	}


	public String getName() {
		return artifact;
	}


	public String getReason() {
		return reason;
	}

	public String getVersion() {
		return version;
	}
	
}
