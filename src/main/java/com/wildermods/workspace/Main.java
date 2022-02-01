package com.wildermods.workspace;

import java.lang.reflect.InvocationTargetException;
import com.wildermods.workspace.wilder.WilderForgeUI;

public class Main {
	
	public static void main(String[] args) throws InterruptedException, ClassNotFoundException, NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		if(args.length == 0) {
			System.err.println("Warning: Calling Main.main(String[]) with no arguments is discouraged. Call your UI.main(String[]) instead!");
			WilderForgeUI.main(args);
		}
		else {
			main(UI.getUI(args));
		}
	}
	
	public static void main(InstallationProperties<?> properties) throws InterruptedException {
		properties.getInstallation().install();
	}
	
}
