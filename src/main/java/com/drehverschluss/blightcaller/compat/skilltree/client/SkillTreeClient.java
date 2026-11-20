package com.drehverschluss.blightcaller.compat.skilltree.client;

import com.drehverschluss.blightcaller.Blightcaller;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.skill_tree_rpgs.utils.TranslationUtil;
import net.spell_engine.mixin.client.ItemStackTooltipAccessor;

/**
 * Tooltips of the Blightcaller skill nodes. Each definition has the description {@code {"skill_definition_id": id}}, Skill
 * Tree resolves it through {@code TranslationUtil.resolvers} on the client:
 * <ul>
 *     <li>passives and spell nodes: description and details of their spell (tokens like {@code {power_multiplier}} are filled
 *     in by Spell Engine, the own ones by {@code client.SkillNodeTooltips});</li>
 *     <li>root and boosts: the attribute bonus; the root also shows the class lock.</li>
 * </ul>
 * The nodes of phase 7B have plain translated texts and no resolver. Only loaded when Skill Tree is there.
 */
public final class SkillTreeClient {
    /** Bonus of the root and every boost, same as in definitions.json ({@code gen_phase7a_data.py}). */
    private static final double BOOST_VALUE = 0.01;
    private static final ResourceLocation SOUL_POWER = ResourceLocation.fromNamespaceAndPath("spell_power", "soul");

    /** Spells of the nodes with a function in phase 7A; the definition id is {@code blightcaller_<spell>}. */
    public static final List<String> SPELL_NODES = List.of(
            "soul_migration", "deepening_rot",
            "improved_soul_rot", "spreading_rot", "tenacious_rot",
            "improved_enfeeble", "deep_frailty", "enervation");

    private SkillTreeClient() {}

    public static void init() {
        String prefix = Blightcaller.MODID + "_";
        TranslationUtil.resolvers.put(prefix + "root", SkillTreeClient::root);
        TranslationUtil.resolvers.put(prefix + "boost", SkillTreeClient::attributeLines);
        for (String spell : SPELL_NODES) {
            ResourceLocation spellId = Blightcaller.id(spell);
            TranslationUtil.resolvers.put(prefix + spell, () -> TranslationUtil.resolveSpellDetails(spellId));
        }
    }

    private static List<Component> root() {
        List<Component> lines = new ArrayList<>(attributeLines());
        // grey line: own tab with class lock, or the Class Skills tab (one class there anyway); red line: own tab only
        if (ClassLockClient.enabled() || ClassLockClient.classTree()) {
            lines.add(Component.translatable("skill." + Blightcaller.MODID + ".class_lock.info").withStyle(ChatFormatting.GRAY));
        }
        if (ClassLockClient.ownBlocked()) {
            lines.add(Component.translatable("skill." + Blightcaller.MODID + ".class_lock.blocked").withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    /** "+1% Soul Spell Power", in the same form as on items. */
    private static List<Component> attributeLines() {
        LocalPlayer player = Minecraft.getInstance().player;
        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(SOUL_POWER).map(h -> (Holder<Attribute>) h).orElse(null);
        if (player == null || attribute == null) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        AttributeModifier modifier = new AttributeModifier(Blightcaller.id("skill_boost"), BOOST_VALUE,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        ((ItemStackTooltipAccessor) (Object) ItemStack.EMPTY)
                .spellEngine_appendAttributeModifierTooltip(lines::add, player, attribute, modifier);
        return lines;
    }
}
