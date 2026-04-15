package ch.njol.skript.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.classes.Changer.ChangeMode;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.bukkit.displays.DisplayData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
			if (object instanceof Display) {
				if (!(object instanceof TextDisplay display))
					return null;
				if (display.isDefaultBackground())
					return ColorRGB.fromBukkitColor(DisplayData.DEFAULT_BACKGROUND_COLOR);
				if (display.getBackgroundColor() == null)
					return null;
				return ColorRGB.fromBukkitColor(display.getBackgroundColor());
			}
			return getColor(object);
		});
	}

	@Override
	public Class<?> @Nullable [] acceptChange(ChangeMode mode) {
		Expression<?> expression = getExpr();

		if (expression.canReturn(FireworkEffect.class))
			return CollectionUtils.array(Color[].class);

		if ((mode == ChangeMode.RESET || mode == ChangeMode.SET) && expression.canReturn(Display.class))
			return CollectionUtils.array(Color.class);

		if (mode == ChangeMode.SET &&
				(expression.canReturn(Entity.class) || expression.canReturn(Block.class) || expression.canReturn(ItemType.class))) {
			return CollectionUtils.array(Color.class);
		}

		return null;
	}

	@Override
	public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
		Color[] colors = delta != null ? (Color[]) delta : null;

		for (Object object : getExpr().getArray(event)) {

			// 🎨 TEXT DISPLAY
			if (object instanceof TextDisplay display) {
				if (mode == ChangeMode.RESET) {
					display.setDefaultBackground(true);
				} else if (mode == ChangeMode.SET && colors != null) {
					display.setDefaultBackground(false);
					display.setBackgroundColor(colors[0].asBukkitColor());
				}
			}

			// 🎆 FIREWORK EFFECT
			else if (object instanceof FireworkEffect effect && colors != null) {
				switch (mode) {
					case SET -> {
						effect.getColors().clear();
						for (Color c : colors)
							effect.getColors().add(c.asBukkitColor());
					}
					case ADD -> {
						for (Color c : colors)
							effect.getColors().add(c.asBukkitColor());
					}
					case REMOVE, REMOVE_ALL -> {
						for (Color c : colors)
							effect.getColors().remove(c.asBukkitColor());
					}
					case RESET, DELETE -> effect.getColors().clear();
				}
			}

			// 🧱 BLOCKS
			else if (mode == ChangeMode.SET && object instanceof Block block && colors != null) {
				if (block.getState() instanceof Banner banner) {
					banner.setBaseColor(colors[0].asDyeColor());
					banner.update();
				}
			}

			// 📦 ITEMS (FIXED MODERN SYSTEM)
			else if (mode == ChangeMode.SET && (object instanceof Item || object instanceof ItemType) && colors != null) {

				ItemStack stack = object instanceof Item
						? ((Item) object).getItemStack()
						: ((ItemType) object).getRandom();

				if (stack == null) continue;

				ItemMeta meta = stack.getItemMeta();
				if (meta == null) continue;

				var bukkitColor = colors[0].asBukkitColor();

				if (meta instanceof LeatherArmorMeta leather) {
					leather.setColor(bukkitColor);
					stack.setItemMeta(leather);
				}
				else if (meta instanceof BannerMeta banner) {
					banner.setBaseColor(colors[0].asDyeColor());
					stack.setItemMeta(banner);
				}
				else if (meta instanceof PotionMeta potion) {
					potion.setColor(bukkitColor);
					stack.setItemMeta(potion);
				}
				else if (meta instanceof MapMeta map) {
					map.setColor(bukkitColor);
					stack.setItemMeta(map);
				}
				else if (meta instanceof FireworkEffectMeta fwMeta) {
					FireworkEffect effect = fwMeta.getEffect();
					if (effect != null) {
						FireworkEffect newEffect = FireworkEffect.builder()
								.with(effect.getType())
								.withColor(bukkitColor)
								.flicker(effect.hasFlicker())
								.trail(effect.hasTrail())
								.build();
						fwMeta.setEffect(newEffect);
						stack.setItemMeta(fwMeta);
					}
				}

				if (object instanceof Item item) {
					item.setItemStack(stack);
				}
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

	// 🔍 GET COLOR (FIXED)
	private @Nullable Color getColor(Object object) {

		// 📦 ITEMS
		if (object instanceof Item || object instanceof ItemType) {
			ItemStack stack = object instanceof Item
					? ((Item) object).getItemStack()
					: ((ItemType) object).getRandom();

			if (stack == null) return null;

			ItemMeta meta = stack.getItemMeta();
			if (meta == null) return null;

			if (meta instanceof LeatherArmorMeta leather)
				return ColorRGB.fromBukkitColor(leather.getColor());

			if (meta instanceof BannerMeta banner && banner.getBaseColor() != null)
				return SkriptColor.fromDyeColor(banner.getBaseColor());

			if (meta instanceof PotionMeta potion && potion.getColor() != null)
				return ColorRGB.fromBukkitColor(potion.getColor());

			if (meta instanceof MapMeta map && map.getColor() != null)
				return ColorRGB.fromBukkitColor(map.getColor());

			if (meta instanceof FireworkEffectMeta fwMeta) {
				FireworkEffect effect = fwMeta.getEffect();
				if (effect != null && !effect.getColors().isEmpty())
					return ColorRGB.fromBukkitColor(effect.getColors().get(0));
			}
		}

		// 🧱 BLOCKS
		if (object instanceof Block block) {
			if (block.getState() instanceof Banner banner)
				return SkriptColor.fromDyeColor(banner.getBaseColor());
		}

		// 🧪 POTION EFFECT TYPES
		if (object instanceof PotionEffectType potionEffectType) {
			return ColorRGB.fromBukkitColor(potionEffectType.getColor());
		}

		return null;
	}
}
