package de.olafklischat.volkit.lang;

/**
 * A function (essentially a {@link Runnable} with a return value) with one
 * parameter.
 * 
 * @author olaf
 * 
 * @param <P0>
 * @param <R>
 */
public interface Function1<P0, R> {
	R run(P0 p0);
}
