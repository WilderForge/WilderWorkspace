package com.wildermods.workspace.decompile;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import com.wildermods.workspace.WriteRule;

public class DecompileWriteRule extends WriteRule {

	private File originCopyDest;
	
	public DecompileWriteRule(String regex) {
		super(regex);
	}
	
	public DecompileWriteRule setOriginCopyDest(File dest) {
		originCopyDest = dest;
		return this;
	}

	@Override
	public Throwable write(File origin, File sourceDest) {
		System.out.println("Decompiling " + origin.getName() + " to " + sourceDest);
		Throwable t = new Decompiler(origin).decompile();
		if(t != null) {
			return t;
		}
		if(originCopyDest != null) {
			System.out.println("Copying " + origin.getName() + " to " + originCopyDest);
			try {
				FileUtils.copyFile(origin, originCopyDest);
			} catch (IOException e) {
				return e;
			}
		}
		else {
			System.out.println("Warning: No origin copy destination specified for " + origin);
		}
		return null;
	}

}
