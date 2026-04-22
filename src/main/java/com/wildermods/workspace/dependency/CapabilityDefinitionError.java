package com.wildermods.workspace.dependency;

public class CapabilityDefinitionError extends AssertionError {

	public CapabilityDefinitionError(String message) {
		super(message);
	}
	
	public CapabilityDefinitionError(Throwable cause) {
		super(cause);
	}
	
	public CapabilityDefinitionError(String message, Throwable cause) {
		super(message, cause);
	}
	
}
