package com.wildermods.workspace;

public enum ProjectDependencyType {

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
	fabricImpl;
	
}
