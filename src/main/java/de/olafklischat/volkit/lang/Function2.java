package de.olafklischat.volkit.lang;

/**
 * A function (essentially a {@link Runnable} with a return value) with two
 * parameters.
 * 
 * @author olaf
 * 
 * @param <P0>
 * @param <P1>
 * @param <R>
 */
public interface Function2<P0, P1, R> {
	R run(P0 p0, P1 p1);
}
