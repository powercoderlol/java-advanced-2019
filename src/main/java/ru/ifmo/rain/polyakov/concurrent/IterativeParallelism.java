package ru.ifmo.rain.polyakov.concurrent;

import info.kgeorgiy.java.advanced.concurrent.ListIP;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;


public class IterativeParallelism implements ListIP {

    @Override
    public String join(int threads, List<?> values) throws InterruptedException {
        Function<List<?>, String> join = list -> list.stream().map(Object::toString).collect(Collectors.joining());
        List<String> results = splitAndApply(threads, values, join);
        return join.apply(results);
    }

    @Override
    public <T> List<T> filter(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        Function<List<? extends T>, List<T>> filterFunc = arg -> arg.stream().filter(predicate).collect(Collectors.toList());
        List<List<T>> threadsResults = splitAndApply(threads, values, filterFunc);
        return toFlat(threadsResults);
    }

    @Override
    public <T, U> List<U> map(int threads, List<? extends T> values, Function<? super T, ? extends U> function) throws InterruptedException {
        Function<List<? extends T>, List<U>> mapFunc = arg -> arg.stream().map(function).collect(Collectors.toList());
        List<List<U>> threadsResults = splitAndApply(threads, values, mapFunc);
        return toFlat(threadsResults);
    }

    @Override
    public <T> T maximum(int threads, List<? extends T> values, Comparator<? super T> comparator) throws InterruptedException, NoSuchElementException {
        Function<List<? extends T>, T> maxFunc = arg -> arg.stream().max(comparator).get();
        List<T> threadsResults = splitAndApply(threads, values, maxFunc);
        return maxFunc.apply(threadsResults);
    }

    @Override
    public <T> T minimum(int threads, List<? extends T> values, Comparator<? super T> comparator) throws InterruptedException, NoSuchElementException {
        return maximum(threads, values, comparator.reversed());
    }

    @Override
    public <T> boolean all(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        List<Boolean> threadsResults = splitAndApply(threads, values, list -> list.stream().allMatch(predicate));
        return threadsResults.stream().allMatch(Predicate.isEqual(true));
    }

    @Override
    public <T> boolean any(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return !all(threads, values, predicate.negate());
    }

    private <T, R> List<R> splitAndApply(int threads, List<? extends T> args, Function<List<? extends T>, R> func) throws InterruptedException {
        threads = Math.min(threads, args.size());
        List<List<? extends T>> parts = split(args, threads);

        List<FunctionRunnable<T, R>> runnableList = parts.stream().map(part -> new FunctionRunnable<>(func, part)).collect(Collectors.toList());
        List<Thread> threadList = runnableList.stream().map(Thread::new).collect(Collectors.toList());
        threadList.forEach(Thread::start);
        for (Thread t : threadList) {
            t.join();
        }
        return runnableList.stream().map(FunctionRunnable::getResult).collect(Collectors.toList());
    }

    private <T> List<List<? extends T>> split(List<? extends T> list, int n) {
        List<List<? extends T>> parts = new ArrayList<>();
        int step = list.size() / n;
        int to = 0;
        for (int i = 0; i < n; i += 1) {
            int from = to;
            to += step + (i < (list.size() % n) ? 1 : 0);
            parts.add(list.subList(from, to));
        }
        return parts;
    }

    private <T> List<T> toFlat(List<List<T>> listOfList) {
        return listOfList.stream().flatMap(List::stream).collect(Collectors.toList());
    }

    private class FunctionRunnable<X, Y> implements Runnable {
        private List<? extends X> values;
        private Function<List<? extends X>, Y> functions;
        private Y result;

        public FunctionRunnable(Function<List<? extends X>, Y> functions, List<? extends X> values) {
            this.functions = functions;
            this.values = values;
        }

        @Override
        public void run() {
            result = functions.apply(values);
        }

        public Y getResult() {
            return result;
        }
    }
}