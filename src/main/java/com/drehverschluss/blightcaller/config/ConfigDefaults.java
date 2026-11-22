package com.drehverschluss.blightcaller.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.spell_engine.rpg_series.config.AttributeModifier;
import org.jetbrains.annotations.Nullable;

/**
 * Helpers for taking over changed defaults into JSON configs that already exist (phase 9B).
 * <p>
 * Spell Engine and tiny_config keep every value that is already in a file, so a changed default in the code never reaches a pack
 * that has the file from an older version. The files therefore carry a defaults version: when it is older than the version of the
 * mod, an entry that still has exactly the old default values gets the new defaults, an entry that someone changed stays as it is
 * (and the log says so). Afterwards the version is raised, so a value set later on purpose is never touched again.
 */
public final class ConfigDefaults {
    /** Allowed difference of two config values (floats written as text). */
    private static final double EPSILON = 1.0E-4;

    private ConfigDefaults() {}

    /** Whether both lists hold the same attributes with the same operations and values, in any order. */
    public static boolean same(@Nullable List<AttributeModifier> a, @Nullable List<AttributeModifier> b) {
        List<AttributeModifier> left = a == null ? List.of() : a;
        List<AttributeModifier> right = new ArrayList<>(b == null ? List.of() : b);
        if (left.size() != right.size()) {
            return false;
        }
        for (AttributeModifier modifier : left) {
            AttributeModifier match = null;
            for (AttributeModifier candidate : right) {
                if (same(modifier, candidate)) {
                    match = candidate;
                    break;
                }
            }
            if (match == null) {
                return false;
            }
            right.remove(match);
        }
        return true;
    }

    /** Same attribute, operation and value (the optional id is ignored). */
    public static boolean same(@Nullable AttributeModifier a, @Nullable AttributeModifier b) {
        if (a == null || b == null) {
            return a == b;
        }
        return Objects.equals(a.attribute, b.attribute) && a.operation == b.operation && Math.abs(a.value - b.value) < EPSILON;
    }
}
