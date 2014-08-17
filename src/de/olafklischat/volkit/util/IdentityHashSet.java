package de.olafklischat.volkit.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * {@link Set} implementation that uses object identity (==) instead of
 * {@link Object#equals(Object)} / {@link Object#hashCode()} for checking for
 * duplicate elements.
 * 
 * @author Olaf Klischat
 */
public class IdentityHashSet<E> implements Set<E> {

	private Map<E, Object> backend = new IdentityHashMap<E, Object>();
	private final static Object VALUE = new Object();

	public IdentityHashSet() {
    }
	
    public IdentityHashSet(Collection<? extends E> c) {
        addAll(c);
    }
	
    @Override
	public boolean add(E e) {
		return null == backend.put(e, VALUE);
	}

    @Override
	public boolean addAll(Collection<? extends E> c) {
		boolean hasChanged = false;
		for (E e : c) {
			boolean changed = add(e);
			hasChanged = hasChanged || changed;
		}
		return hasChanged;
	}

    @Override
	public void clear() {
		backend.clear();
	}

    @Override
	public boolean contains(Object o) {
		return backend.containsKey(o);
	}

    @Override
	public boolean containsAll(Collection<?> c) {
		for (Object e : c) {
			if (!contains(e)) {
				return false;
			}
		}
		return true;
	}

    @Override
	public boolean isEmpty() {
		return backend.isEmpty();
	}

    @Override
	public Iterator<E> iterator() {
		return backend.keySet().iterator();
	}

    @Override
	public boolean remove(Object o) {
		return null != backend.remove(o);
	}

    @Override
	public boolean removeAll(Collection<?> c) {
		boolean hasChanged = false;
		for (Object e : c) {
			boolean changed = remove(e);
			hasChanged = hasChanged || changed;
		}
		return hasChanged;
	}

    @Override
	public boolean retainAll(Collection<?> c) {
		boolean hasChanged = false;
		for (E e : new ArrayList<E>(this)) {
			if (!c.contains(e)) {
				boolean changed = remove(e);
				hasChanged = hasChanged || changed;
			}
		}
		return hasChanged;
	}

    @Override
	public int size() {
		return backend.size();
	}

    @Override
	public Object[] toArray() {
		return backend.keySet().toArray();
	}

    @Override
	public <T> T[] toArray(T[] a) {
		return backend.keySet().toArray(a);
	}

}
