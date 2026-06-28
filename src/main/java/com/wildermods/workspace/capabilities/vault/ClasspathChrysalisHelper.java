package com.wildermods.workspace.capabilities.vault;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.function.Function;

import com.wildermods.thrixlvault.ChrysalisizedVault;

public class ClasspathChrysalisHelper {
	
	private final ChrysalisizedVault original;
	private final LinkedHashSet<Function<Path, Path>> remapRules = new LinkedHashSet<>();
	
	public ClasspathChrysalisHelper(ChrysalisizedVault vault) {
		this.original = vault;
	}
	
	public ClasspathChrysalisHelper addRule(Function<Path, Path> rule) {
		this.remapRules.add(rule);
		return this;
	}
	
	public RemappedChrysalisizedVault cloneAndMutate() throws IOException {
		RemappedChrysalis remappedChrysalis = new RemappedChrysalis(original.getChrysalis(), remapRules);
		return new RemappedChrysalisizedVault(original, remappedChrysalis);
	}
	
}
