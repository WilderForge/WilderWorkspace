package com.wildermods.workspace.util;

public class ExceptionUtil {

	/**
	 * Returns the root cause of the given throwable.
	 * <p>
	 * This method recursively traverses the cause chain of the provided throwable
	 * to find the first non-null cause. If the given throwable does not have a cause,
	 * the method returns the throwable itself.
	 * 
	 * @param t the throwable for which the root cause is to be found
	 * @return the root cause of the throwable, or the throwable itself if it has no cause
	 */
	public static Throwable getInitialCause(Throwable t) {
	    if (t.getCause() != null) {
	        return getInitialCause(t.getCause());
	    }
	    return t;
	}
	
}
