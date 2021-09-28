package dev.hephaestus.proximity.xml;

import dev.hephaestus.proximity.cards.layers.LayerGroupRenderer;
import dev.hephaestus.proximity.cards.predicates.CardPredicate;
import dev.hephaestus.proximity.util.Pair;
import dev.hephaestus.proximity.util.Result;
import dev.hephaestus.proximity.util.StatefulGraphics;
import dev.hephaestus.proximity.util.XMLUtil;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;

public abstract class LayerRenderer {
    private static final Map<String, LayerRenderer> LAYERS = new HashMap<>();

    public abstract Result<Optional<Rectangle2D>> renderLayer(RenderableCard card, RenderableCard.XMLElement element, StatefulGraphics graphics, Rectangle2D wrap, boolean draw, float scale, Rectangle2D bounds);

    public final Result<Optional<Rectangle2D>> render(RenderableCard card, RenderableCard.XMLElement element, StatefulGraphics graphics, Rectangle2D wrap, boolean draw, float scale, Rectangle2D bounds) {
        List<String> errors = new ArrayList<>();
        List<CardPredicate> predicates = new ArrayList<>();
        boolean render = true;

        element.apply("conditions", conditions -> {
            conditions.iterate((condition, i) -> XMLUtil.parsePredicate(condition, card::getPredicate, card::exists)
                    .ifPresent(predicates::add)
                    .ifError(errors::add));
        });

        for (CardPredicate predicate : predicates) {
            Result<Boolean> r = predicate.test(card);

            if (r.isOk() && !r.get()) {
                render = false;
            }
        }

        if (!errors.isEmpty()) {
            return Result.error("Error(s) parsing predicates:\n\t%s", String.join("\n\t", errors));
        }

        if (!render) {
            return Result.of(Optional.empty());
        }

        Optional<Pair<RenderableCard.XMLElement, LayerRenderer>> mask = element.apply("Mask", e -> {
            return new Pair<>(e, new LayerGroupRenderer());
        });

        if (mask.isPresent()) {
            BufferedImage maskImage = new BufferedImage(graphics.getImage().getWidth(), graphics.getImage().getHeight(), BufferedImage.TYPE_INT_ARGB);
            Result<Optional<Rectangle2D>> maskResult = mask.get().right().render(card, mask.get().left(), new StatefulGraphics(maskImage), wrap, draw,scale, bounds);

            if (maskResult.isError()) return maskResult;

            BufferedImage layerImage = new BufferedImage(graphics.getImage().getWidth(), graphics.getImage().getHeight(), BufferedImage.TYPE_INT_ARGB);
            Result<Optional<Rectangle2D>> layerResult = this.renderLayer(card, element, new StatefulGraphics(layerImage), wrap, draw,scale, bounds);

            if (layerResult.isError()) return layerResult;

            int width = layerImage.getWidth();
            int height = layerImage.getHeight();

            int[] layer = layerImage.getRGB(0, 0, width, height, null, 0, width);
            int[] masks = maskImage.getRGB(0, 0, width, height, null, 0, width);

            for (int i = 0; i < layer.length; i++) {
                int color = layer[i] & 0x00FFFFFF;
                int alpha = masks[i] & 0xFF000000;
                layer[i] = color | alpha;
            }

            layerImage.setRGB(0, 0, width, height, layer, 0, width);

            graphics.drawImage(layerImage, null, null);

            return maskResult;
        } else {
            return this.renderLayer(card, element, graphics, wrap, draw, scale, bounds);
        }
    }

    public static void register(LayerRenderer layer, String... tagNames) {
        for (String tagName : tagNames) {
            LAYERS.put(tagName, layer);
        }
    }

    public static LayerRenderer get(String tagName) {
        return LAYERS.get(tagName);
    }
}