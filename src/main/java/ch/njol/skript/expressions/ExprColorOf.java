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
	"The color of an item, entity, block, firework effect, or text display."
})
@Example("""
	on click on wool:
		message "Color: %color of event-block%"
		set color of event-block to red
""")
@Since("1.2, 2.10 (displays)")
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

			if (object instanceof Display display) {
				if (!(display instanceof TextDisplay text))
					return null;

				if (text.isDefaultBackground())
					return ColorRGB.fromBukkitColor(DisplayData.DEFAULT_BACKGROUND_COLOR);

				if (text.getBackgroundColor() == null)
					return null;

				return ColorRGB.fromBukkitColor(text.getBackgroundColor());
			}

			return getColor(object);
		});
	}

	@Override
	public Class<?> @Nullable [] acceptChange(ChangeMode mode) {
		Expression<?> expr = getExpr();

		if (expr.canReturn(FireworkEffect.class))
			return CollectionUtils.array(Color[].class);

		if ((mode == ChangeMode.SET || mode == ChangeMode.RESET) && expr.canReturn(Display.class))
			return CollectionUtils.array(Color.class);

		if (mode == ChangeMode.SET &&
			(expr.canReturn(Block.class) || expr.canReturn(ItemType.class) || expr.canReturn(Item.class))) {
			return CollectionUtils.array(Color.class);
		}

		return null;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
		Color[] colors = delta != null ? (Color[]) delta : null;

		Consumer<TextDisplay> displayChanger = getDisplayChanger(mode, colors);
		Consumer<FireworkEffect> fireworkChanger = getFireworkChanger(mode, colors);

		for (Object object : getExpr().getArray(event)) {

			if (object instanceof TextDisplay display) {
				displayChanger.accept(display);
				continue;
			}

			if (object instanceof FireworkEffect effect) {
				fireworkChanger.accept(effect);
				continue;
			}

			// BLOCK / ITEM COLOR
			if (mode == ChangeMode.SET && colors != null && colors.length > 0) {

				Color color = colors[0];

				// BLOCK (Banner safe 1.21+)
				if (object instanceof Block block) {
					BlockState state = block.getState();

					if (state instanceof Banner banner) {
						banner.setBaseColor(color.asDyeColor());
						banner.update(true);
						continue;
					}

					if (state instanceof Colorable colorable) {
						colorable.setColor(color.asDyeColor());
						state.update(true);
						continue;
					}
				}

				// ITEM (legacy-safe fallback)
				if (object instanceof Item item) {
					ItemStack stack = item.getItemStack();
					applyItemColor(stack, color);
					item.setItemStack(stack);
				}

				if (object instanceof ItemType type) {
					ItemStack stack = type.getRandom();
					if (stack != null)
						applyItemColor(stack, color);
				}
			}
		}
	}

	private void applyItemColor(ItemStack stack, Color color) {
		if (stack == null) return;

		try {
			org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
			if (meta instanceof org.bukkit.inventory.meta.Dyeable dyeable) {
				dyeable.setColor(color.asBukkitColor());
				stack.setItemMeta(meta);
			}
		} catch (Throwable ignored) {
			// fallback for legacy items
			try {
				MaterialData data = stack.getData();
				if (data instanceof Colorable colorable) {
					colorable.setColor(color.asDyeColor());
					stack.setData(data);
				}
			} catch (Throwable ignored2) {}
		}
	}

	private Consumer<TextDisplay> getDisplayChanger(ChangeMode mode, Color @Nullable [] colors) {
		Color color = (colors != null && colors.length > 0) ? colors[0] : null;

		return switch (mode) {
			case RESET -> display -> display.setDefaultBackground(true);
			case SET -> display -> {
				if (color != null) {
					display.setDefaultBackground(false);
					display.setBackgroundColor(color.asBukkitColor());
				}
			};
			default -> display -> {};
		};
	}

	private Consumer<FireworkEffect> getFireworkChanger(ChangeMode mode, Color @Nullable [] colors) {
		return switch (mode) {

			case SET -> effect -> {
				effect.getColors().clear();
				if (colors != null)
					for (Color c : colors)
						effect.getColors().add(c.asBukkitColor());
			};

			case ADD -> effect -> {
				if (colors != null)
					for (Color c : colors)
						effect.getColors().add(c.asBukkitColor());
			};

			case REMOVE, REMOVE_ALL -> effect -> {
				if (colors != null)
					for (Color c : colors)
						effect.getColors().remove(c.asBukkitColor());
			};

			case RESET, DELETE -> effect -> effect.getColors().clear();

			default -> effect -> {};
		};
	}

	private @Nullable Color getColor(Object object) {

		if (object instanceof Block block) {
			BlockState state = block.getState();

			if (state instanceof Banner banner)
				return SkriptColor.fromDyeColor(banner.getBaseColor());

			if (state instanceof Colorable colorable)
				return SkriptColor.fromDyeColor(colorable.getColor());
		}

		if (object instanceof Item item) {
			return getItemColor(item.getItemStack());
		}

		if (object instanceof ItemType type) {
			return getItemColor(type.getRandom());
		}

		if (object instanceof PotionEffectType potion) {
			return ColorRGB.fromBukkitColor(potion.getColor());
		}

		return null;
	}

	private @Nullable Color getItemColor(ItemStack stack) {
		if (stack == null) return null;

		org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
		if (meta instanceof org.bukkit.inventory.meta.Dyeable dyeable) {
			return ColorRGB.fromBukkitColor(dyeable.getColor());
		}

		return null;
	}

	@Override
	public Class<? extends Color> getReturnType() {
		return Color.class;
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return "color of " + getExpr().toString(event, debug);
	}
}
