package de.olafklischat.volkit.lang;

/**
 * A Runnable like {@link Runnable java.lang.Runnable}, but with three
 * parameters.
 * 
 * @author olaf
 * 
 * @param <P0>
 * @param <P1>
 * @param <P2>
 */
public interface Runnable3<P0, P1, P2> {
	void run(P0 p0, P1 p1, P2 p2);
}
