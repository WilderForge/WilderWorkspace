package com.wildermods.workspace;

import java.io.File;

public abstract class WriteRule {
	
	private final String regex;
	
	public WriteRule(String regex) {
		this.regex = regex;
	}
	
	public abstract Throwable write(File source, File dest);
	
	public boolean matches(File source) {
		if(source.getAbsolutePath().matches(regex)) {
			System.out.println(source + " matches writerule " + regex);
			return true;
		}
		return false;
	}
}
