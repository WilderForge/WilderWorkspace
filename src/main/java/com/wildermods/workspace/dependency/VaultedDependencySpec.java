package com.wildermods.workspace.dependency;

import java.io.IOException;
import java.nio.file.Path;

import com.wildermods.thrixlvault.Vault;
import com.wildermods.thrixlvault.exception.MissingVersionException;

public record VaultedDependencySpec(Vault vault, String version) {
	
	public VaultedDependencySpec(Path vaultDir, String version) throws IOException, MissingVersionException {
		this(new Vault(vaultDir), version);
	}
	
	public VaultedDependencySpec(String version) {
		this(Vault.DEFAULT, version);
	}
	
}
