package com.wildermods.workspace.capabilities;

import java.util.Optional;

import org.gradle.api.Project;

import com.wildermods.workspace.capabilities.CapabilityHandler.CanonicalModule;
import com.wildermods.workspace.capabilities.CapabilityHandler.VersionExtractor;

class GradleProjectVarHandler {
	static VersionExtractor createGradleProjectVarExtractor(GradleProject gp, CanonicalModule module, String varName) {
	    Project project = gp.getProject();
		return file -> {
			project.getLogger().info("Executing " + module + ": projectVar - " + varName);
			return Optional.ofNullable(project.findProperty(varName))
					.map(Object::toString);
		};
	}
}
