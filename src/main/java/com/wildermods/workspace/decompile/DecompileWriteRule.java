package com.wildermods.workspace.decompile;

import java.io.File;
import java.io.IOException;

import com.wildermods.workspace.WriteRule;

public class DecompileWriteRule extends WriteRule {

	private final Decompiler decompiler;
	
	public DecompileWriteRule(String regex, Decompiler decompiler) {
		super(regex);
		this.decompiler = decompiler;
	}

	@Override
	public void write(File source, File dest) throws IOException {
		decompiler.Decompile(source, dest);
	}

}
