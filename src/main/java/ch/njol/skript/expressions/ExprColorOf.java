package ch.njol.skript.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.*;
import ch.njol.skript.expressions.base.PropertyExpression;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
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

@Name("Color of")
@Description("Gets or sets the color of blocks, items, banners, firework effects, and text displays.")
@Example("set color of player's tool to red")
@Since("2.14 (1.21 compatible)")
public class ExprColorOf extends PropertyExpression<Object, Color> {

	static {
		Skript.registerExpression(
			ExprColorOf.class,
			Color.class,
			ExpressionType.PROPERTY,
			"colo[u]r[s] of %objects%"
		);
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
		// FIREWORK (READ ONLY)
		// =========================
		if (obj instanceof FireworkEffect effect) {
			if (effect.getColors().isEmpty())
				return null;

			return ColorRGB.fromBukkitColor(effect.getColors().get(0));
		}

		return null;
	}

	// =========================
	// ITEM COLOR (1.21 SAFE)
	// =========================
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

		// 1.21 Dyeable items (leather armor, etc.)
		if (meta instanceof org.bukkit.inventory.meta.Dyeable dyeable) {
			if (dyeable.hasColor()) {
				return ColorRGB.fromBukkitColor(dyeable.getColor());
			}
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

			// =========================
			// TEXT DISPLAY
			// =========================
			if (obj instanceof TextDisplay display) {
				if (mode == ChangeMode.RESET) {
					display.setDefaultBackground(true);
				} else if (color != null) {
					display.setDefaultBackground(false);
					display.setBackgroundColor(color.asBukkitColor());
				}
org.bukkit.inventory.meta.Dyeable
				continue;
			}

			// =========================
			// BLOCK (BANNERS ONLY)
			// =========================
			if (obj instanceof Block block && color != null) {
				BlockState state = block.getState();

				if (state instanceof Banner banner) {
					banner.setBaseColor(color.asDyeColor());
					banner.update(true);
				}
				continue;
			}

			// =========================
			// ITEM ENTITY
			// =========================
			if (obj instanceof Item item && color != null) {

				ItemStack stack = item.getItemStack();
				ItemMeta meta = stack.getItemMeta();

				if (meta instanceof org.bukkit.inventory.meta.Dyeable dyeable) {
					dyeable.setColor(color.asBukkitColor());
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
