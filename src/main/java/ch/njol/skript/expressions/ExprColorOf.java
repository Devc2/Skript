package ch.njol.skript.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.*;
import ch.njol.skript.expressions.base.PropertyExpression;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.util.Color;
import ch.njol.skript.util.ColorRGB;
import ch.njol.skript.util.SkriptColor;
import ch.njol.util.Kleenean;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

@Name("Color of")
@Description("Gets or sets the color of blocks, items, banners, firework effects, and text displays.")
@Example("set color of player's tool to red")
@Since("2.14 (1.21 compatible)")
public class ExprColorOf extends PropertyExpression<Object, Color> {

	static {
		register(ExprColorOf.class, Color.class,
			"colo[u]r[s] of %blocks/itemtypes/entities/fireworkeffects/displays%");
	}

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern,
						Kleenean isDelayed, ParseResult parseResult) {

		if (exprs.length != 1 || exprs[0] == null)
			return false;

		setExpr(exprs[0]);
		return true;
	}

	@Override
	protected Color[] get(Event event, Object[] source) {
		return get(source, this::getColor);
	}

	private Color getColor(Object obj) {

		// =========================
		// TEXT DISPLAY
		// =========================
		if (obj instanceof TextDisplay display) {
			if (display.isDefaultBackground())
				return null;
			return ColorRGB.fromBukkitColor(display.getBackgroundColor());
		}

		// =========================
		// BLOCK (BANNERS ONLY)
		// =========================
		if (obj instanceof Block block) {
			BlockState state = block.getState();

			if (state instanceof Banner banner) {
				DyeColor dye = banner.getBaseColor();
				return dye != null ? SkriptColor.fromDyeColor(dye) : null;
			}
		}

		// =========================
		// ITEM ENTITY
		// =========================
		if (obj instanceof Item item) {
			return getItemColor(item.getItemStack());
		}

		// =========================
		// ITEMTYPE
		// =========================
		if (obj instanceof ItemType type) {
			ItemStack stack = type.getRandom();
			if (stack != null)
				return getItemColor(stack);
		}

		// =========================
		// FIREWORK EFFECT
		// =========================
		if (obj instanceof FireworkEffect effect) {
			if (effect.getColors().isEmpty())
				return null;
			return ColorRGB.fromBukkitColor(effect.getColors().get(0));
		}

		return null;
	}

	private Color getItemColor(ItemStack stack) {
		if (stack == null)
			return null;

		ItemMeta meta = stack.getItemMeta();
		if (meta == null)
			return null;

		// Banner item
		if (meta instanceof BlockStateMeta stateMeta) {
			if (stateMeta.getBlockState() instanceof Banner banner) {
				DyeColor dye = banner.getBaseColor();
				return dye != null ? SkriptColor.fromDyeColor(dye) : null;
			}
		}

		// Leather armor / dyeable items (1.21)
		try {
			if (meta.hasColor()) {
				return ColorRGB.fromBukkitColor(meta.getColor());
			}
		} catch (Throwable ignored) {
		}

		return null;
	}

	@Override
	public Class<?> @Nullable [] acceptChange(ChangeMode mode) {
		return (mode == ChangeMode.SET || mode == ChangeMode.RESET)
			? new Class<?>[]{Color.class}
			: null;
	}

	@Override
	public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {

		Color color = (delta != null && delta.length > 0) ? (Color) delta[0] : null;

		for (Object obj : getExpr().getArray(event)) {

			// TEXT DISPLAY
			if (obj instanceof TextDisplay display) {
				if (mode == ChangeMode.RESET) {
					display.setDefaultBackground(true);
				} else if (color != null) {
					display.setDefaultBackground(false);
					display.setBackgroundColor(color.asBukkitColor());
				}
				continue;
			}

			// BLOCK (BANNERS ONLY)
			if (obj instanceof Block block) {
				BlockState state = block.getState();

				if (state instanceof Banner banner && color != null) {
					banner.setBaseColor(color.asDyeColor());
					banner.update(true);
				}
				continue;
			}

			// ITEMS
			if (obj instanceof Item item && color != null) {
				ItemStack stack = item.getItemStack();
				ItemMeta meta = stack.getItemMeta();

				if (meta != null && meta.hasColor()) {
					meta.setColor(color.asBukkitColor());
					stack.setItemMeta(meta);
				}

				item.setItemStack(stack);
			}
		}
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
