package com.wildermods.workspace.dependency;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import com.wildermods.thrixlvault.Vault;
import com.wildermods.thrixlvault.exception.MissingVersionException;

/** A game version and named Thrixlvault context to construct in the workspace. */
public record VaultedDependencySpec(Vault vault, String version, String variant) {

	public static final String DEFAULT_VARIANT = "coremod-base";
	public static final String ORIGINAL_VARIANT = "blobs";

	public VaultedDependencySpec {
		Objects.requireNonNull(vault, "vault");
		Objects.requireNonNull(version, "version");
		validateVariant(variant);
	}

	/** Existing callers select coremod-base unless they explicitly request another variant. */
	public VaultedDependencySpec(Vault vault, String version) {
		this(vault, version, DEFAULT_VARIANT);
	}

	public VaultedDependencySpec(Path vaultDir, String version) throws IOException, MissingVersionException {
		this(new Vault(vaultDir), version);
	}

	public VaultedDependencySpec(Path vaultDir, String version, String variant) throws IOException {
		this(new Vault(vaultDir), version, variant);
	}

	public VaultedDependencySpec(String version) {
		this(Vault.DEFAULT, version);
	}

	public VaultedDependencySpec(String version, String variant) {
		this(Vault.DEFAULT, version, variant);
	}

	public static String validateVariant(String variant) {
		Objects.requireNonNull(variant, "variant");
		if (variant.isBlank() || variant.equals(".") || variant.equals("..")
				|| variant.contains("/") || variant.contains("\\") || variant.contains(":")) {
			throw new IllegalArgumentException("Expected a variant name without path components: " + variant);
		}
		return variant;
	}
}
