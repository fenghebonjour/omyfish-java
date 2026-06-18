package com.omyfish.shared.domain;

public abstract class ValueObject {

    protected abstract boolean sameValueAs(ValueObject other);

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();
}
