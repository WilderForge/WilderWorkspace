package com.wildermods.workspace.util;

import java.io.IOException;

import org.gradle.api.Task;
import org.gradle.api.logging.LogLevel;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;

import net.fabricmc.loom.util.IOStringConsumer;

public class GradlePrintStreamLogger extends IFernflowerLogger implements IOStringConsumer {

	private final Task task;
	
	public GradlePrintStreamLogger(Task task) {
		this.task = task;
	}
	
	@Override
	public void writeMessage(String message, Severity severity) {
		switch(severity) {
			case ERROR:
				task.getLogger().error(message);
				break;
			case INFO:
				task.getLogger().info(message);
				break;
			case TRACE:
				task.getLogger().trace(message);
				break;
			case WARN:
				task.getLogger().warn(message);
				break;
			default:
				break;
		}
	}

	@Override
	public void writeMessage(String message, Severity severity, Throwable t) {
		switch(severity) {
		case ERROR:
			task.getLogger().log(LogLevel.ERROR, message, t);
			break;
		case INFO:
			task.getLogger().log(LogLevel.INFO, message, t);
			break;
		case TRACE:
			task.getLogger().log(LogLevel.DEBUG, message, t);
			break;
		case WARN:
			task.getLogger().log(LogLevel.WARN, message, t);
			break;
		default:
			break;
		
		}
	}

	@Override
	public void accept(String data) throws IOException {
		writeMessage(data, Severity.INFO);
	}

}
