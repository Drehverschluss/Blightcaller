package com.drehverschluss.blightcaller.command;

import com.drehverschluss.blightcaller.curse.CurseApplication;
import com.drehverschluss.blightcaller.curse.CurseDamage;
import com.drehverschluss.blightcaller.curse.CurseData;
import com.drehverschluss.blightcaller.curse.CurseEffects;
import com.drehverschluss.blightcaller.curse.CurseEntry;
import com.drehverschluss.blightcaller.curse.Curses;
import com.drehverschluss.blightcaller.curse.Pestilence;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.spell_power.api.SpellPower;
import net.spell_power.api.SpellSchools;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /blightcaller curse ...} (permission level 2), for testing and for pack developers.
 * <ul>
 *     <li>{@code apply <targets> <curse> <seconds> [power] [factor]}: curses the targets like a spell would. The caster is
 *     the player running the command (none from the console or a command block). Without power: the player's current
 *     soul spell power ({@value #DEFAULT_POWER} without a player). Factor: damage per tick = power x factor, default
 *     {@value #DEFAULT_FACTOR}. Critical is rolled like for a spell, Curse Duration applies.</li>
 *     <li>{@code info <targets>}: curse level and every entry (curse, caster, damage per tick, critical, time left).</li>
 *     <li>{@code clear <targets> [curse]}: removes all curses, or one.</li>
 * </ul>
 */
public final class BlightcallerCommands {
    public static final double DEFAULT_FACTOR = 0.25;
    public static final double DEFAULT_POWER = 10.0;

    private static final DynamicCommandExceptionType NOT_A_CURSE =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.blightcaller.curse.not_a_curse", id));

    private BlightcallerCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher(), event.getBuildContext());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("blightcaller")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("curse")
                        .then(Commands.literal("apply")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(curseArgument(context)
                                                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.05, 3600.0))
                                                        .executes(ctx -> apply(ctx, null, DEFAULT_FACTOR))
                                                        .then(Commands.argument("power", DoubleArgumentType.doubleArg(0.0))
                                                                .executes(ctx -> apply(ctx, DoubleArgumentType.getDouble(ctx, "power"), DEFAULT_FACTOR))
                                                                .then(Commands.argument("factor", DoubleArgumentType.doubleArg(0.0))
                                                                        .executes(ctx -> apply(ctx, DoubleArgumentType.getDouble(ctx, "power"),
                                                                                DoubleArgumentType.getDouble(ctx, "factor")))))))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(BlightcallerCommands::info)))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> clear(ctx, false))
                                        .then(curseArgument(context).executes(ctx -> clear(ctx, true)))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> curseArgument(CommandBuildContext context) {
        return Commands.argument("curse", ResourceArgument.resource(context, Registries.MOB_EFFECT))
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(curseIds(), builder));
    }

    private static List<ResourceLocation> curseIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        BuiltInRegistries.MOB_EFFECT.getTagOrEmpty(CurseEffects.CURSES).forEach(holder -> ids.add(Curses.id(holder)));
        return ids;
    }

    private static Holder<MobEffect> curse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Holder<MobEffect> curse = ResourceArgument.getResource(ctx, "curse", Registries.MOB_EFFECT);
        if (!Curses.isCurse(curse)) {
            throw NOT_A_CURSE.create(Curses.id(curse).toString());
        }
        return curse;
    }

    // ------------------------------------------------------------------------------------------------ apply

    private static int apply(CommandContext<CommandSourceStack> ctx, @Nullable Double power, double factor) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        Holder<MobEffect> curse = curse(ctx);
        double ticks = DoubleArgumentType.getDouble(ctx, "seconds") * 20.0;
        ServerPlayer caster = source.getPlayer();
        SpellPower.Result spellPower = caster != null
                ? SpellPower.getSpellPower(SpellSchools.SOUL, caster)
                : new SpellPower.Result(SpellSchools.SOUL, DEFAULT_POWER, 0.0, 1.0);
        if (power != null) {
            spellPower = new SpellPower.Result(SpellSchools.SOUL, power, spellPower.criticalChance(), spellPower.criticalDamage());
        }
        int done = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            CurseApplication app = CurseApplication.cast(curse, caster, spellPower, factor, ticks, living.getRandom());
            if (Pestilence.isPestilence(curse)) {
                // like a cast: a chain of its own with the carrier budget from the config
                app = Pestilence.startChain(app, Pestilence.maxCarriers(caster, null, null, null));
            }
            CurseEntry entry = Curses.apply(living, app);
            Pestilence.afterCast(living, app, entry);
            if (entry == null) {
                source.sendFailure(Component.translatable("commands.blightcaller.curse.apply.failed", living.getDisplayName(), name(curse)));
                continue;
            }
            done++;
            double left = entry.end - living.level().getGameTime();
            if (!Curses.dealsDamage(curse.value())) {
                source.sendSuccess(() -> Component.translatable("commands.blightcaller.curse.apply.done_no_damage",
                        living.getDisplayName(), name(curse), seconds(left)), true);
                continue;
            }
            source.sendSuccess(() -> Component.translatable("commands.blightcaller.curse.apply.done", living.getDisplayName(),
                    name(curse), seconds(left), number(CurseDamage.amount(living, entry)), number(entry.power), number(entry.factor),
                    critical(entry)), true);
        }
        return done;
    }

    // ------------------------------------------------------------------------------------------------ info

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        int found = 0;
        for (Entity entity : EntityArgument.getEntities(ctx, "targets")) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            CurseData data = Curses.data(living).orElse(null);
            int curses = Curses.curseCount(living);
            if (curses == 0 && (data == null || data.isEmpty())) {
                source.sendSuccess(() -> Component.translatable("commands.blightcaller.curse.info.none", living.getDisplayName()), false);
                continue;
            }
            found++;
            int entries = data == null ? 0 : data.size();
            source.sendSuccess(() -> Component.translatable("commands.blightcaller.curse.info.header", living.getDisplayName(),
                    Curses.curseLevel(living), count("commands.blightcaller.curse.count.curses", curses),
                    count("commands.blightcaller.curse.count.entries", entries)), false);
            if (data == null) {
                continue;
            }
            long now = living.level().getGameTime();
            for (Map.Entry<ResourceLocation, List<CurseEntry>> e : data.all().entrySet()) {
                var holder = BuiltInRegistries.MOB_EFFECT.getHolder(e.getKey());
                Component curseName = holder.<Component>map(BlightcallerCommands::name).orElse(Component.literal(e.getKey().toString()));
                boolean damaging = holder.map(h -> Curses.dealsDamage(h.value())).orElse(true);
                for (CurseEntry entry : e.getValue()) {
                    if (!damaging) {
                        source.sendSuccess(() -> Component.translatable("commands.blightcaller.curse.info.entry_no_damage", curseName,
                                casterName(living, entry), seconds(entry.end - now)), false);
                        continue;
                    }
                    source.sendSuccess(() -> Component.translatable("commands.blightcaller.curse.info.entry", curseName,
                            casterName(living, entry), number(CurseDamage.amount(living, entry)), critical(entry),
                            seconds(entry.end - now), seconds(Math.max(0, entry.nextTick - now)),
                            count("commands.blightcaller.curse.count.ticks", entry.ticks)), false);
                }
            }
        }
        return found;
    }

    // ------------------------------------------------------------------------------------------------ clear

    private static int clear(CommandContext<CommandSourceStack> ctx, boolean one) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Holder<MobEffect> only = one ? curse(ctx) : null;
        int total = 0;
        for (Entity entity : EntityArgument.getEntities(ctx, "targets")) {
            if (entity instanceof LivingEntity living) {
                int removed = Curses.clear(living, only);
                total += removed;
                source.sendSuccess(() -> Component.translatable("commands.blightcaller.curse.clear.done",
                        count("commands.blightcaller.curse.count.curses", removed), living.getDisplayName()), true);
            }
        }
        return total;
    }

    // ------------------------------------------------------------------------------------------------ formatting

    private static Component name(Holder<MobEffect> effect) {
        return effect.value().getDisplayName();
    }

    private static Component casterName(LivingEntity target, CurseEntry entry) {
        if (entry.caster == null) {
            return Component.translatable("commands.blightcaller.curse.info.no_caster");
        }
        if (target.level() instanceof ServerLevel level) {
            LivingEntity caster = CurseDamage.resolveCaster(level, entry.caster);
            if (caster != null) {
                return caster.getDisplayName();
            }
        }
        return Component.translatable("commands.blightcaller.curse.info.offline", entry.caster.toString().substring(0, 8));
    }

    private static Component critical(CurseEntry entry) {
        return entry.critical
                ? Component.translatable("commands.blightcaller.curse.critical", number(entry.criticalDamage))
                : Component.empty();
    }

    /** Seconds with two decimals, the decimal separator from the reader's language ("30.00" / "30,00"). */
    private static Component seconds(double ticks) {
        return number(ticks / 20.0);
    }

    /**
     * A number with two decimals. The command runs on the server, which does not know the reader's language, so the
     * decimal separator comes from the language file ({@code commands.blightcaller.number}: "%s.%s" / "%s,%s").
     */
    private static Component number(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        int dot = text.indexOf('.');
        return dot < 0 ? Component.literal(text)
                : Component.translatable("commands.blightcaller.number", text.substring(0, dot), text.substring(dot + 1));
    }

    /** "1 curse" / "2 curses": keys {@code <key>.one} and {@code <key>.many}. */
    private static Component count(String key, int n) {
        return Component.translatable(key + (n == 1 ? ".one" : ".many"), n);
    }
}
