package dev.hephaestus.proximity.app.impl.rendering.properties;

import com.google.common.collect.ImmutableList;
import dev.hephaestus.proximity.app.api.rendering.properties.ListProperty;
import dev.hephaestus.proximity.app.api.rendering.util.Stateful;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class ListPropertyImpl<D, V, R extends Stateful> implements ListProperty<D, V, R>, Stateful {
    private final D data;
    private final R result;
    private final List<Function<D, Optional<V>>> getters = new ArrayList<>(1);
    private ImmutableList<V> values;

    public ListPropertyImpl(R result, D data) {
        this.data = data;
        this.result = result;
    }

    @Override
    public ImmutableList<V> get() {
        if (this.values == null) {
            ImmutableList.Builder<V> builder = ImmutableList.builder();

            for (var getter : this.getters) {
                getter.apply(this.data).ifPresent(builder::add);
            }

            this.values = builder.build();
        }

        return this.values;
    }

    @Override
    public R add(V value) {
        this.getters.add(d -> Optional.ofNullable(value));

        return this.result;
    }

    @Override
    public R add(Function<D, Optional<V>> getter) {
        this.getters.add(getter);

        return this.result;
    }

    @Override
    public void invalidate() {
        this.values = null;
    }
}
