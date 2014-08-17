package de.olafklischat.volkit.lang;

/**
 * A Runnable like {@link Runnable java.lang.Runnable}, but with one parameter.
 * 
 * @author olaf
 * 
 * @param <P0>
 */
public interface Runnable1<P0> {
	void run(P0 p0);
}
