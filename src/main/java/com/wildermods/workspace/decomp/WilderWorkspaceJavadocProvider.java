package com.wildermods.workspace.decomp;

import java.io.File;

import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;

import net.fabricmc.loom.decompilers.vineflower.TinyJavadocProvider;

public class WilderWorkspaceJavadocProvider extends TinyJavadocProvider {
	
	private final TinyJavadocProvider parent;

	public WilderWorkspaceJavadocProvider(File tinyFile) {
		super(tinyFile);
		if(tinyFile == null) {
			parent = null;
		}
		else {
			parent = new TinyJavadocProvider(tinyFile);
		}
	}

	@Override
	public String getClassDoc(StructClass structClass) {
		if(parent == null) {
			return "";
		}
		return parent.getClassDoc(structClass);
	}

	@Override
	public String getFieldDoc(StructClass structClass, StructField structField) {
		if(parent == null) {
			return "";
		}
		return parent.getFieldDoc(structClass, structField);
	}

	@Override
	public String getMethodDoc(StructClass structClass, StructMethod structMethod) {
		if(parent == null) {
			return "";
		}
		return parent.getMethodDoc(structClass, structMethod);
	}

	
	
}
