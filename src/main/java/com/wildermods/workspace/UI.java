package com.wildermods.workspace;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.wildermods.workspace.wilder.WilderForgeUI;

/**
 * @param <I> The InstallationProperties for your game.
 * @param <G> The GameInfo for your game.
 * 
 * @see InstallationProperties
 * @see GameInfo
 */
public interface UI<I extends InstallationProperties<G>, G extends GameInfo> extends InstallationProperties<G> {
	
	void initialize();
	
	/**
	 * @return any errors which prevent the user from running the installation
	 */
	String[] checkErrors();
	
	/**
	 * @return any warnings to tell the user before running the installation
	 */
	String[] checkWarnings();
	
	/**
	 * @return Other information to show the user before running the installation
	 */
	String[] checkInfo();
	
	/**
	 * Set the system property "WWUI" to the fully qualified class name of your UI
	 * before running.
	 * 
	 * If you do not wish to have a UI, you can just create and pass an implementation of 
	 * InstallationProperties into {@link Main#main(InstallationProperties)}.
	 * 
	 * @param args the arguments used when calling your main class.
	 * @return an instance of UI.
	 * 
	 * @throws ClassNotFoundException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	public static UI getUI(String[] args) throws ClassNotFoundException, NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		String uiClassProperty = System.getProperty("WWUI");
		Class<? extends UI> uiClass = null;
		if(uiClassProperty == null) {
			uiClass = WilderForgeUI.class;
		}
		if(uiClass == null) {
			uiClass = (Class<? extends UI>) Class.forName(uiClassProperty);
		}
		if(uiClass == null) {
			throw new ClassNotFoundException("UI class not specified. Specify a UI by setting the system argument 'WWUI' to the fully qualified name of your UI class, or call Main.main(InstallationProperties) if you do not have a UI");
		}
		Constructor c = uiClass.getConstructor(String[].class);
		c.setAccessible(true);
		return (UI) c.newInstance(new Object[] {args});
	}
}
