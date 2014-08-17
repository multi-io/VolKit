package de.olafklischat.volkit.lang;

/**
 * A function (essentially a {@link Runnable} with a return value) with three
 * parameters.
 * 
 * @author olaf
 * 
 * @param <P0>
 * @param <P1>
 * @param <P2>
 * @param <R>
 */
public interface Function3<P0, P1, P2, R> {
	R run(P0 p0, P1 p1, P2 p2);
}
