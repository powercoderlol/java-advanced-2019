package ru.ifmo.rain.polyakov.arrayset;

import java.util.*;

public class ArraySet<T extends Comparable<? super T>> extends AbstractSet<T> implements NavigableSet<T> {
    private final List<T> list;
    private final Comparator<? super T> comparator;

    public ArraySet(Collection<T> collection, Comparator<? super T> comparator) {
        Objects.requireNonNull(collection);
        this.comparator = comparator;

        if (!collection.isEmpty()) {
            TreeSet<T> ts = new TreeSet<>(comparator);
            ts.addAll(collection);
            this.list = Collections.unmodifiableList(new ArrayList<>(ts));
        } else {
            this.list = Collections.emptyList();
        }
    }

    public ArraySet(Collection<T> collection) {
        this(collection, null);
    }

    public ArraySet() {
        this(Collections.emptyList(), null);
    }

    public ArraySet(Comparator<? super T> comp) {
        this(Collections.emptyList(), comp);
    }

    private ArraySet(List<T> list, Comparator<? super T> comp) {
        this.list = list;
        this.comparator = comp;
    }

    @Override
    public T lower(T e) {
        Objects.requireNonNull(e);
        int i = lowerIndex(e);
        return i == -1 ? null : list.get(i);
    }

    @Override
    public T floor(T e) {
        Objects.requireNonNull(e);
        int index = floorIndex(e);
        return index == -1 ? null : list.get(index);
    }

    @Override
    public T ceiling(T e) {
        Objects.requireNonNull(e);
        int index = ceilingIndex(e);
        return index == list.size() ? null : list.get(index);
    }

    @Override
    public T higher(T e) {
        Objects.requireNonNull(e);
        int index = higherIndex(e);
        return index == list.size() ? null : list.get(index);
    }

    @Override
    public T pollFirst() {
        throw new UnsupportedOperationException();
    }

    @Override
    public T pollLast() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<T> iterator() {
        return list.iterator();
    }

    @Override
    public NavigableSet<T> descendingSet() {
        if (list instanceof ArraySet.ReverseList) {
            return new ArraySet<T>(new ReverseList<>(((ReverseList<T>) list)), Collections.reverseOrder(comparator));
        } else {
            return new ArraySet<>(new ReverseList<>(list, true), Collections.reverseOrder(comparator));
        }
    }

    @Override
    public Iterator<T> descendingIterator() {
        return descendingSet().iterator();
    }

    @Override
    public NavigableSet<T> subSet(T fromElement, boolean fromInclusive, T toElement, boolean toInclusive) {
        Objects.requireNonNull(fromElement);
        Objects.requireNonNull(toElement);
        int from = fromInclusive ? ceilingIndex(fromElement) : higherIndex(fromElement);
        int to = toInclusive ? floorIndex(toElement) : lowerIndex(toElement);
        Comparator<? super T> comp = comparator != null ? comparator : Comparator.naturalOrder();
        if (comp.compare(fromElement, toElement) > 0) throw new IllegalArgumentException();
        if (comp.compare(fromElement, toElement) == 0 && (!fromInclusive || !toInclusive)) {
            return new ArraySet<T>(Collections.emptyList(), comparator);
        }
        return new ArraySet<>(list.subList(from, to + 1), comparator);
    }

    @Override
    public NavigableSet<T> headSet(T toElement, boolean inclusive) {
        return new ArraySet<>(list.subList(0, (inclusive ? floorIndex(toElement) : lowerIndex(toElement)) + 1), comparator);
    }

    @Override
    public NavigableSet<T> tailSet(T fromElement, boolean inclusive) {
        return new ArraySet<>(list.subList(inclusive ? ceilingIndex(fromElement) : higherIndex(fromElement), list.size()), comparator);
    }

    @Override
    public Comparator<? super T> comparator() {
        return comparator;
    }

    @Override
    public SortedSet<T> subSet(T fromElement, T toElement) {
        return subSet(fromElement, true, toElement, false);
    }

    @Override
    public SortedSet<T> headSet(T toElement) {
        return headSet(toElement, false);
    }

    @Override
    public SortedSet<T> tailSet(T fromElement) {
        return tailSet(fromElement, true);
    }

    @Override
    public T first() {
        if (size() == 0) {
            throw new NoSuchElementException();
        }
        return list.get(0);
    }

    @Override
    public T last() {
        if (size() == 0) {
            throw new NoSuchElementException();
        }
        return list.get(list.size() - 1);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        return Collections.binarySearch(list, (T) o, comparator) >= 0;
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(T t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    class ReverseList<T1> extends AbstractList<T1> {
        private List<T1> list;
        private boolean reversed;

        ReverseList(ReverseList<T1> reverseList) {
            list = reverseList.list;
            reversed = !reverseList.reversed;
        }

        ReverseList(List<T1> list, boolean reversed) {
            this.list = list;
            this.reversed = reversed;
        }

        @Override
        public T1 get(int index) {
            return reversed ? list.get(list.size() - index - 1) : list.get(index);
        }

        @Override
        public int size() {
            return list.size();
        }
    }

    private int floorIndex(T e) {
        int index = Collections.binarySearch(list, e, comparator);
        return index < 0 ? -index - 2 : index;
    }

    private int ceilingIndex(T e) {
        int index = Collections.binarySearch(list, e, comparator);
        return index < 0 ? -index - 1 : index;
    }

    private int lowerIndex(T e) {
        int index = Collections.binarySearch(list, e, comparator);
        return index < 0 ? -index - 2 : index - 1;
    }

    private int higherIndex(T e) {
        int index = Collections.binarySearch(list, e, comparator);
        return index < 0 ? -index - 1 : index + 1;
    }

}