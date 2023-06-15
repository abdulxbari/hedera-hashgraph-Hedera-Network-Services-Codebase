package com.swirlds.platform.componentframework.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.BlockingQueue;

/**
 * A dynamic proxy that submits tasks to a queue for any method that gets called
 */
public class QueueSubmitter implements InvocationHandler {
	private final BlockingQueue<Object> queue;

	private QueueSubmitter(final BlockingQueue<Object> queue) {
		this.queue = queue;
	}

	/**
	 * Create a new {@link QueueSubmitter} for the given class and queue
	 *
	 * @param clazz
	 * 		the class to create a submitter for
	 * @param queue
	 * 		the queue to submit tasks to
	 * @return a new {@link QueueSubmitter} for the given class and queue
	 */
	@SuppressWarnings("unchecked")
	public static <T> T create(final Class<T> clazz, final BlockingQueue<Object> queue) {
		return (T) Proxy.newProxyInstance(
				QueueSubmitter.class.getClassLoader(),
				new Class[] { clazz },
				new QueueSubmitter(queue));
	}

	@Override
	public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
		switch (method.getName()) {
			case "hashCode":
				return System.identityHashCode(proxy);
			case "equals":
				return false;
			case "toString":
				return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
			case "getProcessingMethods":
				throw new UnsupportedOperationException();
			default:
				queue.put(args[0]);
		}
		return null;
	}
}
