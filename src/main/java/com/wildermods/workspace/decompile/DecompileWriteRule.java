package com.wildermods.workspace.decompile;

import java.io.File;
import com.wildermods.workspace.WriteRule;

public class DecompileWriteRule extends WriteRule {

	private final Decompiler decompiler;
	
	public DecompileWriteRule(String regex, Decompiler decompiler) {
		super(regex);
		this.decompiler = decompiler;
	}

	@Override
	public Throwable write(File source, File dest) {
		System.out.println("Decompiling " + dest);
		return decompiler.Decompile(source, dest);
	}

}
