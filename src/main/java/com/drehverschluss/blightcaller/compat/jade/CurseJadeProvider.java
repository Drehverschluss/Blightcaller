package com.drehverschluss.blightcaller.compat.jade;

import com.drehverschluss.blightcaller.curse.CurseData;
import com.drehverschluss.blightcaller.curse.CurseEntry;
import com.drehverschluss.blightcaller.curse.Curses;
import com.drehverschluss.blightcaller.curse.DeepeningRot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

/**
 * Curses in the Jade tooltip of a living entity (concept 8, wish 20.11.: with remaining time).
 * <ul>
 *     <li><b>Server:</b> curse level and per curse its strength, its end (game time) and whether the looking player has an
 *     own entry. The end is the player's own entry if there is one (Last Rites always count 4 s, so one wants to see when
 *     the own curse runs out), otherwise the longest entry. Comes straight from the curse entries, so curses on targets
 *     that refuse effects (Wither) are there too. Curses whose display effect is visible are left out (Jade's own effect
 *     list shows those); Blightcaller's own effects are always hidden.</li>
 *     <li><b>Client:</b> the remaining time is counted down every frame from the end, so it runs smoothly between Jade's
 *     data requests. Own curses are drawn in full, curses of other players dimmed. Two styles ({@link
 *     BlightcallerJadePlugin.Style}): compact (icon and time per curse, one line) or list (one line per curse).</li>
 * </ul>
 */
public enum CurseJadeProvider implements IEntityComponentProvider, StreamServerDataProvider<EntityAccessor, CurseJadeProvider.Data> {
    INSTANCE;

    /** Text colour of own curses on dark themes (highlighted). */
    static final int OWN_COLOR = 0xFFFFFFFF;
    /** Text colour of curses of other players (dimmed). */
    static final int FOREIGN_COLOR = 0xFF767676;

    /** One curse on the entity as the tooltip needs it. */
    public record Line(Holder<MobEffect> curse, int amplifier, long end, boolean own, int casters, int bonus) {
        static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.holderRegistry(Registries.MOB_EFFECT), Line::curse,
                ByteBufCodecs.VAR_INT, Line::amplifier,
                ByteBufCodecs.VAR_LONG, Line::end,
                ByteBufCodecs.BOOL, Line::own,
                ByteBufCodecs.VAR_INT, Line::casters,
                ByteBufCodecs.VAR_INT, Line::bonus,
                Line::new);
    }

    /** Curse level and the curses. */
    public record Data(int level, List<Line> lines) {
        static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Data::level,
                Line.STREAM_CODEC.apply(ByteBufCodecs.list()), Data::lines,
                Data::new);
    }

    @Override
    public ResourceLocation getUid() {
        return BlightcallerJadePlugin.CURSES;
    }

    /** After Jade's own effect list. */
    @Override
    public int getDefaultPriority() {
        return 6000;
    }

    // ------------------------------------------------------------------------------------------------ server

    @Override
    public boolean shouldRequestData(EntityAccessor accessor) {
        return accessor.getEntity() instanceof LivingEntity;
    }

    @Nullable
    @Override
    public Data streamData(EntityAccessor accessor) {
        if (!(accessor.getEntity() instanceof LivingEntity living)) {
            return null;
        }
        Optional<CurseData> data = Curses.data(living);
        if (data.isEmpty() || data.get().isEmpty()) {
            return null;
        }
        Player player = accessor.getPlayer();
        UUID viewer = player == null ? null : player.getUUID();
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<ResourceLocation, List<CurseEntry>> e : data.get().all().entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            Optional<Holder.Reference<MobEffect>> curse = BuiltInRegistries.MOB_EFFECT.getHolder(e.getKey());
            if (curse.isEmpty()) {
                continue;
            }
            MobEffectInstance shown = living.getEffect(curse.get());
            if (shown != null && shown.isVisible()) {
                continue;
            }
            long longest = Long.MIN_VALUE;
            long own = Long.MIN_VALUE;
            int bonus = 0;
            for (CurseEntry entry : e.getValue()) {
                longest = Math.max(longest, entry.expires());
                if (viewer != null && entry.sameCaster(viewer)) {
                    own = Math.max(own, entry.expires());
                    // Deepening Rot: the bonus of the viewer's own last tick on this target, in percent
                    if (Curses.dealsDamage(curse.get().value())) {
                        bonus = (int) Math.round(100.0 * DeepeningRot.currentBonus(player, entry));
                    }
                }
            }
            boolean hasOwn = own != Long.MIN_VALUE;
            lines.add(new Line(curse.get(), data.get().amplifier(e.getKey()), hasOwn ? own : longest, hasOwn, e.getValue().size(), bonus));
        }
        return lines.isEmpty() ? null : new Data(Curses.curseLevel(living), List.copyOf(lines));
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
        return Data.STREAM_CODEC;
    }

    // ------------------------------------------------------------------------------------------------ client

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        Optional<Data> decoded = decodeFromData(accessor);
        if (decoded.isEmpty() || decoded.get().lines().isEmpty()) {
            return;
        }
        Data data = decoded.get();
        BlightcallerJadePlugin.Style style = config.getEnum(BlightcallerJadePlugin.STYLE);
        float now = accessor.getLevel().getGameTime() + net.minecraft.client.Minecraft.getInstance().getTimer()
                .getGameTimeDeltaPartialTick(false);
        IElementHelper helper = IElementHelper.get();
        IThemeHelper theme = IThemeHelper.get();
        Component header = theme.danger(Component.translatable("jade.blightcaller.curse_level", data.level()));
        if (style == BlightcallerJadePlugin.Style.LIST) {
            tooltip.add(header);
            for (Line line : data.lines()) {
                String time = time(line.end() - now);
                Component name = line.curse().value().getDisplayName();
                Component text = line.bonus() > 0
                        ? Component.translatable("jade.blightcaller.curse_line_bonus", name, time, line.bonus())
                        : Component.translatable("jade.blightcaller.curse_line", name, time);
                tooltip.add(new CurseIconElement(line.curse(), text, line.own(), 0));
            }
            return;
        }
        List<IElement> row = new ArrayList<>();
        row.add(helper.text(header));
        row.add(helper.spacer(4, 0));
        for (Line line : data.lines()) {
            row.add(new CurseIconElement(line.curse(), Component.literal(time(line.end() - now)), line.own(), 4));
        }
        tooltip.add(row);
    }

    /** Remaining time: one decimal under 10 s (decimal separator from the language), whole seconds above. */
    static String time(float ticks) {
        float seconds = Math.max(0.0F, ticks / 20.0F);
        String number;
        if (seconds < 10.0F) {
            number = String.format(Locale.ROOT, "%.1f", seconds).replace(".", I18n.get("jade.blightcaller.decimal"));
        } else {
            number = Integer.toString((int) Math.ceil(seconds));
        }
        return I18n.get("jade.blightcaller.seconds", number);
    }
}
