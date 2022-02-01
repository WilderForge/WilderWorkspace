package com.wildermods.workspace;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.wildermods.workspace.wilder.WilderForgeUI;

public interface UI<I extends InstallationProperties<G>, G extends GameInfo> extends InstallationProperties<G> {
	
	void initialize();
	
	String[] checkErrors();
	
	String[] checkWarnings();
	
	String[] checkInfo();
	
	public static UI getUI(String[] args) throws ClassNotFoundException, NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		String uiClassProperty = System.getProperty("WWUI");
		Class<? extends UI> uiClass = null;
		if(uiClassProperty == null) {
			uiClass = WilderForgeUI.class;
		}
		if(uiClass != null) {
			uiClass = (Class<? extends UI>) Class.forName(uiClassProperty);
		}
		Constructor c = uiClass.getConstructor(String[].class);
		c.setAccessible(true);
		return (UI) c.newInstance((Object[])args);
	}
}
