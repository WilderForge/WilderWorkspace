package com.wildermods.workspace;

public enum ProjectDependencyType {

	/**
	 * normal gradle api dependency type
	 */
	api,
	
	/**
	 * normal gradle compile time only dependencies
	 */
	compileOnly,
	
	/**
	 * normal gradle implementation dependencies
	 */
	implementation,
	
	/**
	 * dependencies that fabric requires to be in the "fabricDependencyPath" at runtime. {@code ./bin/fabric/} is
	 * the default location of where these dependencies are located.
	 */
	fabricDep, 
	
	/**
	 * dependencies that are required to be in the "fabricPath" at runtime. Usually {@code ./bin/} is the default
	 * location of where these dependencies are located
	 */
	fabricImpl,
	
	/**
	 * All implementation dependencies (including configurations which extend 'implementation'
	 * 
	 * This configuration is resolvable by gradle.
	 * 
	 * For internal use only, used to copy all of the project's dependencies to the workspace.
	 */
	resolvableImplementation,
	
	/**
	 * A configuration that includes {@link fabricDep} and {@link fabricImpl} dependencies.
	 * 
	 * For internal use only, used to exclude fabric dependencies when copying all of the
	 * project's dependencies to the workspace
	 */
	excludedFabricDeps,
	
	/**
	 * A non-transitive subdependency of a dependency. These dependencies are needed only at compile time, as
	 * they will be included by other dependencies at runtime.
	 * 
	 * For example, fabric loader has mixinExtras as a transitive dependency. MixinExtras is automatically included
	 * at runtime by extracting it from fabric-loader's jar via JarInJar.
	 * 
	 * At compile time, however, it is unavailable as it's nested inside of fabric-loader
	 */
	retrieveJson
}
