package com.wildermods.workspace;

import java.lang.reflect.InvocationTargetException;

public class Main {
	
	/**
	 * You should generally just create your UI class yourself using
	 * args passed to your main function.
	 * 
	 * @param args
	 * @throws InterruptedException
	 * @throws ClassNotFoundException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	@Deprecated
	public static void main(String[] args) throws InterruptedException, ClassNotFoundException, NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		UI.getUI(args);
	}
	
	public static void install(InstallationProperties<?> properties) throws InterruptedException {
		properties.getInstallation().install();
	}
	
}
