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
import ch.njol.util.coll.CollectionUtils;
import org.bukkit.DyeColor;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.bukkit.displays.DisplayData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Name("Color of")
@Description({
	"The color of an item, entity, block, firework effect, or display.",
	"Now supports modern item meta like banners, leather armor, and potions."
})
@Since("1.2, patched modern support")
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
		List<Color> colors = new ArrayList<>();

		for (Object object : source) {
			Color c = getColor(object);
			if (c != null)
				colors.add(c);
		}

		return colors.toArray(new Color[0]);
	}

	@Override
	public Class<?> @Nullable [] acceptChange(ChangeMode mode) {
		if (mode == ChangeMode.SET)
			return CollectionUtils.array(Color.class);
		return null;
	}

	@Override
	public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
		if (mode != ChangeMode.SET || delta == null) return;

		Color color = (Color) delta[0];

		for (Object object : getExpr().getArray(event)) {

			// ===== ITEMS =====
			if (object instanceof Item || object instanceof ItemType) {
				ItemStack stack = object instanceof Item
						? ((Item) object).getItemStack()
						: ((ItemType) object).getRandom();

				if (stack == null) continue;

				ItemMeta meta = stack.getItemMeta();
				if (meta == null) continue;

				// Leather armor
				if (meta instanceof LeatherArmorMeta leather) {
					leather.setColor(color.asBukkitColor());
				}

				// Banner
				else if (meta instanceof BannerMeta banner) {
					banner.setBaseColor(color.asDyeColor());
				}

				// Potion
				else if (meta instanceof PotionMeta potion) {
					potion.setColor(color.asBukkitColor());
				}

				stack.setItemMeta(meta);

				if (object instanceof Item item)
					item.setItemStack(stack);
			}

			// ===== BLOCKS =====
			else if (object instanceof Block block) {
				BlockState state = block.getState();

				if (state instanceof Banner banner) {
					banner.setBaseColor(color.asDyeColor());
					banner.update();
				}
			}

			// ===== TEXT DISPLAY =====
			else if (object instanceof TextDisplay display) {
				display.setBackgroundColor(color.asBukkitColor());
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

	// =========================
	// 🔍 MODERN COLOR DETECTION
	// =========================
	private @Nullable Color getColor(Object object) {

		// ===== ITEM =====
		if (object instanceof Item || object instanceof ItemType) {
			ItemStack stack = object instanceof Item
					? ((Item) object).getItemStack()
					: ((ItemType) object).getRandom();

			if (stack == null) return null;

			ItemMeta meta = stack.getItemMeta();
			if (meta == null) return null;

			// Leather armor
			if (meta instanceof LeatherArmorMeta leather) {
				return ColorRGB.fromBukkitColor(leather.getColor());
			}

			// Banner
			if (meta instanceof BannerMeta banner) {
				DyeColor dye = banner.getBaseColor();
				if (dye != null)
					return SkriptColor.fromDyeColor(dye);
			}

			// Potion
			if (meta instanceof PotionMeta potion && potion.hasColor()) {
				return ColorRGB.fromBukkitColor(potion.getColor());
			}
		}

		// ===== BLOCK =====
		if (object instanceof Block block) {
			BlockState state = block.getState();
			if (state instanceof Banner banner) {
				return SkriptColor.fromDyeColor(banner.getBaseColor());
			}
		}

		// ===== DISPLAY =====
		if (object instanceof TextDisplay display) {
			if (display.isDefaultBackground())
				return ColorRGB.fromBukkitColor(DisplayData.DEFAULT_BACKGROUND_COLOR);

			if (display.getBackgroundColor() != null)
				return ColorRGB.fromBukkitColor(display.getBackgroundColor());
		}

		return null;
	}
}
