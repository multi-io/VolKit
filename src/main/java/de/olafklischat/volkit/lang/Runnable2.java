package de.olafklischat.volkit.lang;

/**
 * A Runnable like {@link Runnable java.lang.Runnable}, but with two
 * parameters.
 * 
 * @author olaf
 * 
 * @param <P0>
 * @param <P1>
 */
public interface Runnable2<P0, P1> {
	void run(P0 p0, P1 p1);
}
