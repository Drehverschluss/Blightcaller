package com.drehverschluss.blightcaller.curse;

import com.drehverschluss.blightcaller.Blightcaller;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Data attachment {@code blightcaller:curses}: all curse entries on one living entity, grouped by curse (mob effect id).
 * Saved with the entity, not synced to clients (clients see the curse effects through Spell Engine's synchronized
 * effects). An empty attachment is removed again ({@link Curses}).
 */
public final class CurseData {
    public static final Codec<CurseData> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, CurseEntry.CODEC.listOf())
            .xmap(CurseData::new, data -> data.curses);

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Blightcaller.MODID);

    public static final Supplier<AttachmentType<CurseData>> TYPE = ATTACHMENTS.register("curses",
            () -> AttachmentType.builder(() -> new CurseData(Map.of())).serialize(CODEC, data -> !data.isEmpty()).build());

    private final Map<ResourceLocation, List<CurseEntry>> curses = new LinkedHashMap<>();

    private CurseData(Map<ResourceLocation, List<CurseEntry>> loaded) {
        loaded.forEach((curse, entries) -> {
            if (!entries.isEmpty()) {
                curses.put(curse, new ArrayList<>(entries));
            }
        });
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
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

    void remove(ResourceLocation curse) {
        curses.remove(curse);
    }

    /** Drops curses without entries. */
    void compact() {
        curses.values().removeIf(List::isEmpty);
    }

    public boolean isEmpty() {
        for (List<CurseEntry> entries : curses.values()) {
            if (!entries.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int size() {
        int n = 0;
        for (List<CurseEntry> entries : curses.values()) {
            n += entries.size();
        }
        return n;
    }
}
