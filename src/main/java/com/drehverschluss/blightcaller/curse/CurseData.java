package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Data attachment {@code blightcaller:curses}: all curse entries on one living entity, grouped by curse (mob effect id).
 * <b>This is the curse</b>: entries tick from here ({@link Curses#tickAll}), whether or not the target took the curse's
 * mob effect. Saved with the entity, not synced (clients get the small {@link CurseView} instead). An empty attachment is
 * removed again ({@link Curses}).
 * <p>
 * Per curse it also keeps:
 * <ul>
 *     <li><b>amplifier</b>: the strength of curses with an effect of their own (Enfeeble, Gravebind), from the spell's
 *     status effect impact that applies to this target. Bosses that refuse effects need it, since they have no effect
 *     instance to carry it.</li>
 *     <li><b>shown</b>: the target took the display effect. If such an effect disappears without an event (a mod calling
 *     {@code removeEffectNoUpdate}), the curse goes as well, like with milk. Curses on targets that refuse effects are not
 *     shown and never miss their effect.</li>
 * </ul>
 */
public final class CurseData {
    private static final Codec<Map<ResourceLocation, List<CurseEntry>>> ENTRIES_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, CurseEntry.CODEC.listOf());

    /** Phase 2 to 5 saved the entry map alone; that format is still read. */
    private static final Codec<CurseData> LEGACY_CODEC = ENTRIES_CODEC.xmap(
            entries -> new CurseData(entries, Map.of(), List.of()), data -> data.curses);

    private static final Codec<CurseData> FULL_CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRIES_CODEC.fieldOf("entries").forGetter(d -> d.curses),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("amplifiers", Map.of()).forGetter(d -> d.amplifiers),
            ResourceLocation.CODEC.listOf().optionalFieldOf("shown", List.of()).forGetter(d -> List.copyOf(d.shown))
    ).apply(i, CurseData::new));

    public static final Codec<CurseData> CODEC = Codec.withAlternative(FULL_CODEC, LEGACY_CODEC);

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Blightcaller.MODID);

    public static final Supplier<AttachmentType<CurseData>> TYPE = ATTACHMENTS.register("curses",
            () -> AttachmentType.builder(() -> new CurseData(Map.of(), Map.of(), List.of()))
                    .serialize(CODEC, data -> !data.isEmpty()).build());

    private final Map<ResourceLocation, List<CurseEntry>> curses = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> amplifiers = new HashMap<>();
    private final Set<ResourceLocation> shown = new HashSet<>();

    private CurseData(Map<ResourceLocation, List<CurseEntry>> loaded, Map<ResourceLocation, Integer> amplifiers,
                      List<ResourceLocation> shown) {
        loaded.forEach((curse, entries) -> {
            if (!entries.isEmpty()) {
                curses.put(curse, new ArrayList<>(entries));
            }
        });
        amplifiers.forEach((curse, amplifier) -> {
            if (curses.containsKey(curse)) {
                this.amplifiers.put(curse, amplifier);
            }
        });
        for (ResourceLocation curse : shown) {
            if (curses.containsKey(curse)) {
                this.shown.add(curse);
            }
        }
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        CurseView.register(ATTACHMENTS);
    }

    /** Entries of one curse (live list), empty if there are none. */
    public List<CurseEntry> entries(ResourceLocation curse) {
        return curses.getOrDefault(curse, List.of());
    }

    /** Entries of one curse, created if missing (live list). */
    List<CurseEntry> entriesForWrite(ResourceLocation curse) {
        return curses.computeIfAbsent(curse, k -> new ArrayList<>());
    }

    /** All curses with entries (live view). */
    public Map<ResourceLocation, List<CurseEntry>> all() {
        return curses;
    }

    /** Strength of a curse on this target (0 if never set). */
    public int amplifier(ResourceLocation curse) {
        return amplifiers.getOrDefault(curse, 0);
    }

    void setAmplifier(ResourceLocation curse, int amplifier) {
        amplifiers.put(curse, Math.max(0, amplifier));
    }

    /** Whether the target holds the display effect of this curse. */
    public boolean shown(ResourceLocation curse) {
        return shown.contains(curse);
    }

    void setShown(ResourceLocation curse, boolean value) {
        if (value) {
            shown.add(curse);
        } else {
            shown.remove(curse);
        }
    }

    void remove(ResourceLocation curse) {
        curses.remove(curse);
        amplifiers.remove(curse);
        shown.remove(curse);
    }

    void clearAll() {
        curses.clear();
        amplifiers.clear();
        shown.clear();
    }

    /** Drops curses without entries (with their amplifier and shown flag). */
    void compact() {
        curses.values().removeIf(List::isEmpty);
        amplifiers.keySet().retainAll(curses.keySet());
        shown.retainAll(curses.keySet());
    }

    public boolean isEmpty() {
        for (List<CurseEntry> entries : curses.values()) {
            if (!entries.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Number of curses with at least one entry. */
    public int curseCount() {
        int n = 0;
        for (List<CurseEntry> entries : curses.values()) {
            if (!entries.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public int size() {
        int n = 0;
        for (List<CurseEntry> entries : curses.values()) {
            n += entries.size();
        }
        return n;
    }
}
