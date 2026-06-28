package com.wildermods.workspace.capabilities.vault;

import java.io.IOException;

import com.wildermods.thrixlvault.Chrysalis;
import com.wildermods.thrixlvault.ChrysalisizedVault;

public class RemappedChrysalisizedVault extends ChrysalisizedVault {
	
	public RemappedChrysalisizedVault(ChrysalisizedVault vault, Chrysalis chrysalis) throws IOException {
		super(vault.getArtifact(), vault, chrysalis);
	}
	
}
