package dev.hephaestus.proximity.app.impl.rendering.elements;


import dev.hephaestus.proximity.app.api.Option;
import dev.hephaestus.proximity.app.api.Parent;
import dev.hephaestus.proximity.app.api.RenderJob;
import dev.hephaestus.proximity.app.api.rendering.Document;
import dev.hephaestus.proximity.app.api.rendering.elements.*;
import dev.hephaestus.proximity.app.api.rendering.util.BoundingBoxes;
import dev.hephaestus.proximity.app.api.rendering.util.ThrowingFunction;
import dev.hephaestus.proximity.app.api.text.TextStyle;
import dev.hephaestus.proximity.app.impl.rendering.DocumentImpl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface ParentImpl<D extends RenderJob> extends Parent<D> {
    void addChild(ElementImpl<D> child);
    DocumentImpl<D> getDocument();

    default <E extends ElementImpl<D>> E createElement(String id, ElementImpl.Constructor<D, E> constructor) {
        //noinspection unchecked
        E e = constructor.construct(this.getDocument(), id, this instanceof ElementImpl ? (ElementImpl<D>) this : null);

        this.addChild(e);

        return e;
    }

    @Override
    default Element<D> group(String id, Predicate<D> visibilityPredicate, Consumer<Group<D>> groupBuilder) {
        GroupImpl<D> result = this.createElement(id, GroupImpl<D>::new);

        result.visibility().set(visibilityPredicate);

        if (result.visibility().get()) {
            groupBuilder.accept(result);
        }

        return result;
    }

    @Override
    default Element<D> group(String id, Consumer<Group<D>> groupBuilder) {
        GroupImpl<D> result = this.createElement(id, GroupImpl<D>::new);

        groupBuilder.accept(result);

        return result;
    }

    @Override
    default Element<D> select(String id, Predicate<D> visibilityPredicate, Consumer<Selector<D>> selectorBuilder) {
        SelectorImpl<D> result = this.createElement(id, SelectorImpl::new);

        result.visibility().set(visibilityPredicate);

        if (result.visibility().get()) {
            selectorBuilder.accept(result);
        }

        return result;
    }

    @Override
    default Element<D> branch(String id, Function<D, String> branch, Predicate<D> visibilityPredicate, Consumer<Selector<D>> treeBuilder) {
        if (visibilityPredicate.test(this.getDocument().getData())) {
            return this.select(id, select -> {
                select.select(branch.apply(this.getDocument().getData()), treeBuilder);
            });
        }

        return new Empty<>(id, this.getDocument());
    }

    @Override
    default Element<D> branch(String id, Function<D, String> branch, Consumer<Selector<D>> treeBuilder) {
        return this.select(id, select -> {
            select.select(branch.apply(this.getDocument().getData()), treeBuilder);
        });
    }

    @Override
    default Element<D> select(String id, Consumer<Selector<D>> selectorBuilder) {
        SelectorImpl<D> result = this.createElement(id, SelectorImpl::new);

        selectorBuilder.accept(result);

        return result;
    }

    @Override
    default Element<D> branch(Function<D, String> id, Predicate<D> visibilityPredicate, Consumer<Selector<D>> treeBuilder) {
        if (visibilityPredicate.test(this.getDocument().getData())) {
            return this.select(id.apply(this.getDocument().getData()), treeBuilder);
        }

        return new Empty<>(id.apply(this.getDocument().getData()), this.getDocument());
    }

    @Override
    default Element<D> branch(Function<D, String> id, Consumer<Selector<D>> treeBuilder) {
        return this.select(id.apply(this.getDocument().getData()), treeBuilder);
    }

    @Override
    default Image<D> image(String id) {
        return this.createElement(id, ImageImpl<D>::new);
    }

    @Override
    default Image<D> image(String id, String src) {
        return this.createElement(id, ImageImpl<D>::new).src()
                .set(this.getDocument().getResourceLocation(src, ImageImpl.IMAGE_FILE_TYPES));
    }

    @Override
    default Image<D> image(String id, Option<String, ?, ? super D> src) {
        return this.image(id, src.apply(this.getDocument().getData()));
    }

    @Override
    default Image<D> image(String id, String src, Predicate<D> visibilityPredicate) {
        return this.createElement(id, ImageImpl<D>::new).visibility().set(visibilityPredicate).src()
                .set(this.getDocument().getResourceLocation(src, ImageImpl.IMAGE_FILE_TYPES));
    }

    @Override
    default Image<D> image(Function<D, String> id, Predicate<D> visibilityPredicate) {
        return this.createElement(id.apply(this.getDocument().getData()), ImageImpl<D>::new).visibility().set(visibilityPredicate);
    }

    @Override
    default Image<D> image(String id, ThrowingFunction<D, URL, MalformedURLException> srcGetter) {
        return this.createElement(id, ImageImpl<D>::new).src().set(srcGetter);
    }

    @Override
    default Text<D> text(String id, Function<D, String> textGetter, int x, int y, TextStyle base) {
        return this.text(id, textGetter).x().set(x).y().set(y).style().set(base);
    }

    @Override
    default Text<D> text(String id, Function<D, String> textGetter) {
        return this.createElement(id, TextImpl<D>::new).text().add(textGetter);
    }

    @Override
    default Text<D> text(String id, String text) {
        return this.createElement(id, TextImpl<D>::new).text().add(text);
    }

    @Override
    default Text<D> text(String id) {
        return this.createElement(id, TextImpl<D>::new);
    }

    @Override
    default TextBox<D> textBox(String id) {
        return this.createElement(id, TextBoxImpl<D>::new);
    }

    @Override
    default TextBox<D> textBox(String id, int x, int y, int width, int height) {
        return this.createElement(id, TextBoxImpl<D>::new)
                .x().set(x)
                .y().set(y)
                .width().set(width)
                .height().set(height);
    }

    record Empty<D extends RenderJob>(String id, Document<D> document) implements Element<D> {
        @Override
        public Document<D> getDocument() {
            return this.document;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public BoundingBoxes getBounds() {
            return new BoundingBoxes();
        }

        @Override
        public void invalidate() {

        }
    }
}
