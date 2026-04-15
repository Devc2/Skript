package ch.njol.skript.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.expressions.base.PropertyExpression;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.util.Color;
import ch.njol.skript.util.ColorRGB;
import ch.njol.skript.util.SkriptColor;
import ch.njol.util.Kleenean;
import ch.njol.util.coll.CollectionUtils;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.Colorable;
import org.bukkit.material.MaterialData;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.bukkit.displays.DisplayData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Name("Color of")
@Description({
        "The color of an item, entity, block, firework effect, or text display.",
        "Supports setting and getting colors for multiple Minecraft objects."
})
@Example("""
        on click on wool:
            if event-block is tagged with minecraft tag "wool":
                message "This wool block is <%color of block%>%color of block%<reset>!"
                set the color of the block to black
""")
@Since("1.2, 2.10 (displays), 1.21.11 compatibility patch")
public class ExprColorOf extends PropertyExpression<Object, Color> {

    static {
        String types = "blocks/itemtypes/entities/fireworkeffects/potioneffecttypes";
        if (Skript.isRunningMinecraft(1, 19, 4))
            types += "/displays";
        register(ExprColorOf.class, Color.class, "colo[u]r[s]", types);
    }

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        setExpr(exprs[0]);
        return true;
    }

    // =========================
    // GET
    // =========================
    @Override
    protected Color[] get(Event event, Object[] source) {

        if (source instanceof FireworkEffect[]) {
            List<Color> colors = new ArrayList<>();
            for (FireworkEffect effect : (FireworkEffect[]) source) {
                effect.getColors().stream()
                        .map(ColorRGB::fromBukkitColor)
                        .forEach(colors::add);
            }
            return colors.toArray(new Color[0]);
        }

        return get(source, object -> {

            // =========================
            // DISPLAY SUPPORT
            // =========================
            if (object instanceof Display display) {
                if (!(display instanceof TextDisplay text))
                    return null;

                if (text.isDefaultBackground())
                    return ColorRGB.fromBukkitColor(DisplayData.DEFAULT_BACKGROUND_COLOR);

                org.bukkit.Color c = text.getBackgroundColor();
                return c != null ? ColorRGB.fromBukkitColor(c) : null;
            }

            return getColor(object);
        });
    }

    // =========================
    // CHANGE SUPPORT
    // =========================
    @Override
    public Class<?> @Nullable [] acceptChange(ChangeMode mode) {
        Expression<?> expr = getExpr();

        if (expr.canReturn(FireworkEffect.class))
            return CollectionUtils.array(Color[].class);

        if ((mode == ChangeMode.RESET || mode == ChangeMode.SET) && expr.canReturn(Display.class))
            return CollectionUtils.array(Color.class);

        if (mode == ChangeMode.SET &&
                (expr.canReturn(Block.class) ||
                        expr.canReturn(ItemType.class) ||
                        expr.canReturn(Item.class))) {
            return CollectionUtils.array(Color.class);
        }

        return null;
    }

    // =========================
    // CHANGE IMPLEMENTATION
    // =========================
    @Override
    @SuppressWarnings("removal")
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {

        Color[] colors = delta != null ? (Color[]) delta : null;

        Consumer<TextDisplay> displayChanger = getDisplayChanger(mode, colors);
        Consumer<FireworkEffect> fireworkChanger = getFireworkChanger(mode, colors);

        for (Object object : getExpr().getArray(event)) {

            // =========================
            // TEXT DISPLAY
            // =========================
            if (object instanceof TextDisplay display) {
                displayChanger.accept(display);
                continue;
            }

            // =========================
            // FIREWORK EFFECT
            // =========================
            if (object instanceof FireworkEffect effect) {
                fireworkChanger.accept(effect);
                continue;
            }

            // =========================
            // BLOCK COLOR (1.21 FIX HERE)
            // =========================
            if (mode == ChangeMode.SET && object instanceof Block block) {
                if (block.getState() instanceof Banner banner && colors != null) {
                    banner.setBaseColor(colors[0].asDyeColor());
                    banner.update();
                }
                continue;
            }

            // =========================
            // ITEM COLOR (1.21 FIX)
            // =========================
            if (mode == ChangeMode.SET && (object instanceof Item || object instanceof ItemType)) {

                ItemStack stack = object instanceof Item
                        ? ((Item) object).getItemStack()
                        : ((ItemType) object).getRandom();

                if (stack == null || colors == null)
                    continue;

                ItemMeta meta = stack.getItemMeta();

                if (meta instanceof BlockStateMeta blockStateMeta) {
                    BlockState state = blockStateMeta.getBlockState();

                    if (state instanceof Banner banner) {
                        banner.setBaseColor(colors[0].asDyeColor());
                        banner.update();

                        blockStateMeta.setBlockState(banner);
                        stack.setItemMeta(blockStateMeta);
                    }
                }

                if (object instanceof Item itemEntity) {
                    itemEntity.setItemStack(stack);
                }
            }
        }
    }

    // =========================
    // RETURN TYPE
    // =========================
    @Override
    public Class<? extends Color> getReturnType() {
        return Color.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "color of " + getExpr().toString(event, debug);
    }

    // =========================
    // DISPLAY HANDLER
    // =========================
    private Consumer<TextDisplay> getDisplayChanger(ChangeMode mode, Color @Nullable [] colors) {
        Color color = (colors != null && colors.length == 1) ? colors[0] : null;

        return switch (mode) {
            case RESET -> display -> display.setDefaultBackground(true);
            case SET -> display -> {
                if (color != null) {
                    if (display.isDefaultBackground())
                        display.setDefaultBackground(false);
                    display.setBackgroundColor(color.asBukkitColor());
                }
            };
            default -> display -> {};
        };
    }

    // =========================
    // FIREWORK HANDLER
    // =========================
    private Consumer<FireworkEffect> getFireworkChanger(ChangeMode mode, Color @Nullable [] colors) {
        return switch (mode) {
            case ADD -> effect -> {
                for (Color c : colors)
                    effect.getColors().add(c.asBukkitColor());
            };
            case REMOVE, REMOVE_ALL -> effect -> {
                for (Color c : colors)
                    effect.getColors().remove(c.asBukkitColor());
            };
            case DELETE, RESET -> effect -> effect.getColors().clear();
            case SET -> effect -> {
                effect.getColors().clear();
                for (Color c : colors)
                    effect.getColors().add(c.asBukkitColor());
            };
            default -> effect -> {};
        };
    }

    // =========================
    // COLOR RESOLVER (1.21 FIXED BANNER ACCESS)
    // =========================
    private @Nullable Color getColor(Object object) {

        // BLOCK / ITEM COLORABLE
        if (object instanceof Block block) {
            if (block.getState() instanceof Banner banner)
                return SkriptColor.fromDyeColor(banner.getBaseColor());
        }

        // POTION COLOR
        if (object instanceof PotionEffectType potionEffectType) {
            return ColorRGB.fromBukkitColor(potionEffectType.getColor());
        }

        return null;
    }
}
