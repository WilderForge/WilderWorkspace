package com.wildermods.workspace;

import org.gradle.api.Plugin;

/**
 * A Gradle plugin for setting up and managing the WilderWorkspace development environment.
 * 
 * This is a dummy class. The actual implementation is in {@link WilderWorkspacePluginImpl}
 * 
 * This workaround is needed because gradle cannot directly launch and decorate a plugin 
 * that directly classloads dependencies (I.E. Gson, apache commons, etc)
 * 
 * Basically, if any external dependency required by the plugin is referenced by the
 * class gradle directly launches, a NoClassDefFoundError occurs.
 * 
 */

public class WilderWorkspacePlugin implements Plugin<Object> {

	@Override
	public void apply(Object target) {
		new WilderWorkspacePluginImpl().apply((Object)target);
	}

    
}
