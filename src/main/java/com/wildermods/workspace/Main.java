package com.wildermods.workspace;

import java.lang.reflect.InvocationTargetException;

public class Main {
	
	@Deprecated
	public static void main(String[] args) throws InterruptedException, ClassNotFoundException, NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		UI.getUI(args);
	}
	
	public static void install(InstallationProperties<?> properties) throws InterruptedException {
		properties.getInstallation().install();
	}
	
}
