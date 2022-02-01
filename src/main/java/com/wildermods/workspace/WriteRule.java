package com.wildermods.workspace;

import java.io.File;

public abstract class WriteRule {
	
	private final String regex;
	private boolean matched = false;
	
	public WriteRule(String regex) {
		this.regex = regex;
	}
	
	public abstract Throwable write(Installation installation, File source, File dest);
	
	public boolean matches(File source) {
		if(source.getAbsolutePath().matches(regex)) {
			System.out.println(source + " matches writerule " + regex);
			matched = true;
			return true;
		}
		return false;
	}
	
	public boolean matchFound() {
		return matched;
	}
}
