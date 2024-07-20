package com.wildermods.workspace;

import org.gradle.api.artifacts.Dependency;

enum Dependencies implements Dependency {

		COMMONS_IO("commons-io", "commons-io", "@commonsIOVersion@"),
		COMMONS_LANG("org.apache.commons", "commons-lang3", "@commonsLangVersion@"),
		COMMONS_TEXT("org.apache.commons", "commons-text", "@commonsTextVersion@"),
		LOOM("net.fabricmc", "fabric-loom", "@loomVersion@"),
		VINEFLOWER("org.vineflower", "vineflower", "@vineFlowerVersion@");
	
	;
	
	private final String groupID;
	private final String artifact;
	private final String version;
	private final String dependencyString;
	private String reason;

	
	private Dependencies(String groupID, String artifact, String version) {
		this.groupID = groupID;
		this.artifact = artifact;
		this.version = version;
		this.dependencyString = String.join(":", groupID, artifact, version);
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

	@Override
	public String getVersion() {
		return version;
	}
	
}
