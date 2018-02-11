package org.noway.checkedfunctional;

@FunctionalInterface
public interface CheckedConsumer<T, E extends Exception>
{
    public void consume(T argument) throws E;
}
