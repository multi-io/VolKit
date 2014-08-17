package de.olafklischat.volkit.lang;

/**
 * A function (essentially a {@link Runnable} with a return value).
 * 
 * @author olaf
 *
 * @param <R>
 */
public interface Function<R> {
	R run();
}
