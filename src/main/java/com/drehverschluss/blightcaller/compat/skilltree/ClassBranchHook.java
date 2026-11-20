package com.drehverschluss.blightcaller.compat.skilltree;

import com.drehverschluss.blightcaller.Blightcaller;
import com.drehverschluss.blightcaller.compat.Couplings;
import com.drehverschluss.blightcaller.mixin.skills.SkillsMixinPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.puffish.skillsmod.api.config.ConfigContext;
import net.puffish.skillsmod.api.json.JsonPath;
import net.puffish.skillsmod.api.util.Problem;
import net.puffish.skillsmod.api.util.Result;
import net.puffish.skillsmod.config.CategoryConfig;
import net.puffish.skillsmod.config.reader.PackConfigReader;
import net.puffish.skillsmod.util.DisposeContext;
import net.puffish.skillsmod.util.VersionedConfigContext;
import org.jetbrains.annotations.Nullable;

/**
 * Puts the Blightcaller branch into the Class Skills tab of Skill Tree while Pufferfish's Skills loads its data (server start
 * and {@code /reload}). Called from the mixins in {@code mixin.skills}.
 * <p>
 * <b>One step earlier than Stormcaller</b> (phase 1 report §8): Stormcaller replaces the tab at the return of
 * {@code ConfigReader.readCategory}; a second handler there would never run (the first one cancels), and Stormcaller reads the
 * raw files again. So Blightcaller works on the files themselves, before anyone parses them:
 * <ol>
 *     <li>At the first of: the category list of the {@code blightcaller} data pack (it is read before {@code skill_tree_rpgs},
 *     the packs come sorted by namespace), or the first read of a Class Skills file. Blightcaller reads the final files of the
 *     tab (after all data packs and KubeJS), checks that they load on their own, adds its branch ({@link ClassBranchLayout})
 *     and checks the result with Pufferfish's own parser ({@code CategoryConfig.parse}).</li>
 *     <li>From then on, {@code PackConfigReader.read} hands the extended {@code definitions.json}, {@code skills.json} and
 *     {@code connections.json} of {@code skill_tree_rpgs:class_skills} to everyone who reads them in this load: Pufferfish's
 *     Skills, and Stormcaller's unchanged hook, which adds its branch on top.</li>
 *     <li>The own tab is left out only if the branch is ready: never both, never none. On any problem the files stay the
 *     originals (Stormcaller then hooks in as without Blightcaller) and the own tab is used, with a warning that says why.</li>
 * </ol>
 * <b>Stormcaller after us:</b> it picks its form by the average radius of the roots. If Stormcaller is going to add its branch
 * too, the outward shift of {@code respace} is capped so that its choice stays the same, and {@code gap} is only used if it
 * keeps the choice too (otherwise {@code respace}). Titles, texts, icons and rewards come from the definitions of the own tab,
 * the place from {@code blightcaller:blightcaller_skill_tree/class_branch.json}.
 */
public final class ClassBranchHook {
    public static final String SKILL_TREE_NAMESPACE = "skill_tree_rpgs";
    public static final String CLASS_TAB = "class_skills";
    public static final String WEAPON_TAB = "weapon_skills";
    /** Prefix of our node ids in the Weapon Skills tab; the fixed node is {@code <prefix>root}. */
    public static final String WEAPON_PREFIX = Blightcaller.MODID + "_weapon_blight_";
    public static final String WEAPON_MOD_NAME = "Skill Tree (Weapon Skills)";
    /** Prefix of all our node and definition ids in foreign tabs. */
    public static final String PREFIX = Blightcaller.MODID + "_";
    public static final String MOD_NAME = "Skill Tree";
    private static final ResourceLocation LAYOUT = Blightcaller.id("blightcaller_skill_tree/class_branch.json");
    private static final ResourceLocation STORMCALLER_LAYOUT = ResourceLocation.fromNamespaceAndPath("stormcaller",
            "stormcaller_skill_tree/class_branch.json");
    private static final ResourceLocation WEAPON_LAYOUT = Blightcaller.id("blightcaller_skill_tree/weapon_branch.json");
    private static final ResourceLocation STORMCALLER_WEAPON_LAYOUT = ResourceLocation.fromNamespaceAndPath("stormcaller",
            "stormcaller_skill_tree/weapon_branch.json");
    private static final List<String> REPLACED = List.of("definitions.json", "skills.json", "connections.json");

    private static boolean loading;
    private static boolean prepared;
    private static boolean computing;
    private static boolean injected;
    /** The extended files of the Class Skills tab, by file name, while {@link #injected}. */
    private static final Map<String, net.puffish.skillsmod.api.json.JsonElement> FILES = new HashMap<>();
    /** Short description of the last result, for the probe and the log. */
    private static String lastResult = "";
    private static boolean weaponPrepared;
    private static boolean weaponInjected;
    /** The extended files of the Weapon Skills tab, by file name, while {@link #weaponInjected}. */
    private static final Map<String, net.puffish.skillsmod.api.json.JsonElement> WEAPON_FILES = new HashMap<>();
    private static String lastWeaponResult = "";

    private ClassBranchHook() {}

    /** Start of a load of Pufferfish's Skills. */
    public static void onLoadStart() {
        loading = true;
        prepared = false;
        injected = false;
        FILES.clear();
        weaponPrepared = false;
        weaponInjected = false;
        WEAPON_FILES.clear();
    }

    /** End of a load of Pufferfish's Skills: nothing is handed out any more. */
    public static void onLoadEnd() {
        loading = false;
        FILES.clear();
        WEAPON_FILES.clear();
    }

    /** Our three nodes were put into the Weapon Skills tab in the last load. */
    public static boolean weaponInjected() {
        return weaponInjected;
    }

    public static String lastWeaponResult() {
        return lastWeaponResult;
    }

    /** The branch was put into the Class Skills tab in the last load. */
    public static boolean injected() {
        return injected;
    }

    public static String lastResult() {
        return lastResult;
    }

    /** The category list of one data pack: leaves out the own tab if the branch is in the Class Skills tab. */
    public static List<String> filterOwnTab(ResourceManager resources, String namespace, List<String> ids) {
        if (!Blightcaller.MODID.equals(namespace)) {
            return ids;
        }
        prepare(resources);
        if (injected && ids.contains(SkillTab.BLIGHTCALLER_CATEGORY.getPath())) {
            List<String> out = new ArrayList<>(ids);
            out.remove(SkillTab.BLIGHTCALLER_CATEGORY.getPath());
            return out;
        }
        return ids;
    }

    /** A file read by a data pack reader: the extended Class Skills file, or what was read. */
    public static Result<net.puffish.skillsmod.api.json.JsonElement, Problem> afterRead(
            ResourceManager resources, String namespace, Path path, Result<net.puffish.skillsmod.api.json.JsonElement, Problem> original) {
        if (!loading || computing || !SKILL_TREE_NAMESPACE.equals(namespace) || path.getNameCount() != 3
                || !"categories".equals(path.getName(0).toString())) {
            return original;
        }
        String tab = path.getName(1).toString();
        if (CLASS_TAB.equals(tab)) {
            prepare(resources);
            net.puffish.skillsmod.api.json.JsonElement file = FILES.get(path.getName(2).toString());
            return file != null ? Result.success(file) : original;
        }
        if (WEAPON_TAB.equals(tab)) {
            prepareWeapon(resources);
            net.puffish.skillsmod.api.json.JsonElement file = WEAPON_FILES.get(path.getName(2).toString());
            return file != null ? Result.success(file) : original;
        }
        return original;
    }

    // ------------------------------------------------------------------------------------------------ preparing

    /** Once per load: the extended Class Skills tab, or nothing (own tab). */
    private static void prepare(ResourceManager resources) {
        if (!loading || prepared) {
            return;
        }
        prepared = true;
        SkillTreeVariant.Choice choice = SkillTreeVariant.choose();
        if (!choice.classTree()) {
            lastResult = "own tab (" + choice.reason() + ")";
            Couplings.foundAgain(MOD_NAME, "own Blightcaller tab (" + choice.reason() + ")");
            return;
        }
        if (!SkillsMixinPlugin.hooksReady()) {
            fallback("the loading hooks of Pufferfish's Skills could not be applied (other version?)");
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            fallback("no server");
            return;
        }
        computing = true;
        try {
            long start = System.nanoTime();
            Extended extended = extend(resources, server, choice);
            for (String file : REPLACED) {
                FILES.put(file, extended.files().get(file));
            }
            injected = true;
            lastResult = extended.summary();
            Couplings.foundAgain(MOD_NAME, "branch added to the Class Skills tab (mode " + choice.mode() + ", layout " + extended.layout() + ", "
                    + extended.summary() + ")");
            Blightcaller.LOGGER.debug("[Blightcaller] Class Skills branch: {}, {} ({} ms)", choice.reason(), extended.description(),
                    (System.nanoTime() - start) / 1_000_000);
        } catch (Exception e) {
            fallback(Couplings.reason(e));
        } finally {
            computing = false;
        }
    }

    private static void fallback(String reason) {
        lastResult = "own tab (fallback: " + reason + ")";
        Couplings.forget(MOD_NAME);
        // every load may fail for another reason, so this warning is not once per game like the other couplings
        Blightcaller.LOGGER.warn("[Blightcaller] Skill Tree found, but the branch could not be added to the Class Skills tab ({}); "
                + "using the own Blightcaller tab", reason);
    }

    private record Extended(Map<String, net.puffish.skillsmod.api.json.JsonElement> files, String layout, String summary,
                            String description) {}

    private static Extended extend(ResourceManager resources, MinecraftServer server, SkillTreeVariant.Choice choice) throws Exception {
        PackConfigReader reader = new PackConfigReader(resources, SKILL_TREE_NAMESPACE);
        ConfigContext context = context(server, reader);
        JsonObject general = read(reader, "category.json");
        JsonObject definitions = read(reader, "definitions.json").deepCopy();
        JsonObject skills = read(reader, "skills.json").deepCopy();
        JsonElement connectionsElement = gson(reader.read(categoryPath("connections.json")));
        if (!connectionsElement.isJsonObject()) {
            throw new IllegalStateException("connections.json of the Class Skills tab has the old list form");
        }
        JsonObject connections = connectionsElement.getAsJsonObject().deepCopy();
        Optional<net.puffish.skillsmod.api.json.JsonElement> experience = optional(reader, "experience.json");
        Optional<net.puffish.skillsmod.api.json.JsonElement> exchange = optional(reader, "exchange.json");
        String base = SKILL_TREE_NAMESPACE + ":puffish_skills/categories/" + CLASS_TAB + "/";
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SKILL_TREE_NAMESPACE, CLASS_TAB);

        // the original must load on its own, otherwise it stays untouched
        check(CategoryConfig.parse(id, 0, wrap(general, base + "category.json"), wrap(definitions, base + "definitions.json"),
                wrap(skills, base + "skills.json"), wrap(connections, base + "connections.json"), experience, exchange, context),
                server, "the Class Skills tab fails to load on its own");

        PackConfigReader own = new PackConfigReader(resources, Blightcaller.MODID);
        JsonObject ownDefinitions = gson(own.read(Path.of("categories", "blightcaller", "definitions.json"))).getAsJsonObject();
        JsonObject ownSkills = gson(own.read(Path.of("categories", "blightcaller", "skills.json"))).getAsJsonObject();
        ClassBranchLayout.Settings settings = ClassBranchLayout.parse(readJson(resources, LAYOUT));
        ClassBranchLayout.Form form = ClassBranchLayout.chooseForm(settings, skills);

        // Stormcaller adds its branch after us: keep the form it picks by the root radius
        StormcallerPeer peer = StormcallerPeer.of(resources);
        double radius = ClassBranchLayout.rootRadius(skills);
        int shiftCap = peer == null ? Integer.MAX_VALUE : ClassBranchLayout.shiftKeepingForm(peer.forms(), radius);

        ClassBranchLayout.Placement placement = null;
        String layout = choice.layout();
        if (!choice.respace()) {
            placement = ClassBranchLayout.gap(skills, connections, settings, form);
            if (peer != null && peer.changesForm(skills, placement)) {
                Blightcaller.LOGGER.info("[Blightcaller] Class Skills branch: layout gap would change the form the Stormcaller branch "
                        + "picks, using layout respace");
                placement = null;
                layout = "respace";
            }
        }
        if (placement == null) {
            try {
                placement = ClassBranchLayout.respace(skills, connections, settings, form, shiftCap);
            } catch (ClassBranchLayout.NoPlace e) {
                Blightcaller.LOGGER.info("[Blightcaller] Class Skills branch: layout respace not possible ({}), using layout gap",
                        e.getMessage());
                placement = ClassBranchLayout.gap(skills, connections, settings, form);
                layout = "gap";
                if (peer != null && peer.changesForm(skills, placement)) {
                    throw new IllegalStateException("no place that keeps the form of the Stormcaller branch");
                }
            }
        }

        // foreign nodes: only their positions change (respace)
        for (Map.Entry<String, int[]> e : placement.moved().entrySet()) {
            JsonObject node = skills.getAsJsonObject(e.getKey());
            node.addProperty("x", e.getValue()[0]);
            node.addProperty("y", e.getValue()[1]);
        }
        // our definitions (single source: the own tab)
        for (String key : ownDefinitions.keySet()) {
            if (definitions.has(key)) {
                throw new IllegalStateException("definition id " + key + " is already used in the Class Skills tab");
            }
            definitions.add(key, ownDefinitions.get(key).deepCopy());
        }
        // our nodes
        for (Map.Entry<String, int[]> e : placement.ours().entrySet()) {
            String node = e.getKey();
            String skillId = PREFIX + node;
            if (skills.has(skillId)) {
                throw new IllegalStateException("node id " + skillId + " is already used in the Class Skills tab");
            }
            JsonObject ownNode = ownSkills.has(node) ? ownSkills.getAsJsonObject(node) : null;
            if (ownNode == null || !ownNode.has("definition")) {
                throw new IllegalStateException("node " + node + " of the layout is not in the own tab");
            }
            JsonObject out = new JsonObject();
            out.addProperty("definition", ownNode.get("definition").getAsString());
            out.addProperty("x", e.getValue()[0]);
            out.addProperty("y", e.getValue()[1]);
            if ("root".equals(node)) {
                out.addProperty("root", true);
            }
            skills.add(skillId, out);
        }
        addLinks(connections, "normal", form.normal());
        addLinks(connections, "exclusive", form.exclusive());

        Map<String, net.puffish.skillsmod.api.json.JsonElement> files = new HashMap<>();
        files.put("definitions.json", wrap(definitions, base + "definitions.json (+blightcaller)"));
        files.put("skills.json", wrap(skills, base + "skills.json (+blightcaller)"));
        files.put("connections.json", wrap(connections, base + "connections.json (+blightcaller)"));
        check(CategoryConfig.parse(id, 0, wrap(general, base + "category.json"), files.get("definitions.json"), files.get("skills.json"),
                files.get("connections.json"), experience, exchange, context), server, "the extended tab does not load");
        return new Extended(files, layout, placement.summary(), placement.description());
    }

    /** Throws with the problem if the parse failed; frees the parsed tab (only a check). */
    private static void check(Result<CategoryConfig, Problem> result, MinecraftServer server, String what) {
        Optional<CategoryConfig> ok = result.getSuccess();
        if (ok.isEmpty()) {
            throw new IllegalStateException(what + ": " + result.getFailure().map(Object::toString).orElse("?"));
        }
        ok.get().dispose(new DisposeContext(server));
    }

    /** A config context like the one Pufferfish's Skills uses for the pack, with its version; warnings only go to the debug log. */
    private static ConfigContext context(MinecraftServer server, PackConfigReader reader) {
        int version = 3;
        try {
            JsonElement config = gson(reader.read(Path.of("config.json")));
            if (config.isJsonObject() && config.getAsJsonObject().has("version")) {
                version = config.getAsJsonObject().get("version").getAsInt();
            }
        } catch (Exception ignored) {
            // keep version 3, the one Skill Tree 1.6.1 uses
        }
        ConfigContext plain = new ConfigContext() {
            @Override
            public MinecraftServer getServer() {
                return server;
            }

            @Override
            public void emitWarning(String message) {
                Blightcaller.LOGGER.debug("[Blightcaller] Class Skills check: {}", message);
            }
        };
        return new VersionedConfigContext(plain, version);
    }

    private static Path categoryPath(String file) {
        return categoryPath(CLASS_TAB, file);
    }

    private static Path categoryPath(String tab, String file) {
        return Path.of("categories", tab, file);
    }

    private static JsonObject read(PackConfigReader reader, String file) {
        return read(reader, CLASS_TAB, file);
    }

    private static JsonObject read(PackConfigReader reader, String tab, String file) {
        return gson(reader.read(categoryPath(tab, file))).getAsJsonObject();
    }

    private static JsonElement gson(Result<net.puffish.skillsmod.api.json.JsonElement, Problem> result) {
        net.puffish.skillsmod.api.json.JsonElement element = result.getSuccess()
                .orElseThrow(() -> new IllegalStateException(result.getFailure().map(Object::toString).orElse("cannot be read")));
        return element.getJson();
    }

    private static Optional<net.puffish.skillsmod.api.json.JsonElement> optional(PackConfigReader reader, String file) {
        return optional(reader, CLASS_TAB, file);
    }

    private static Optional<net.puffish.skillsmod.api.json.JsonElement> optional(PackConfigReader reader, String tab, String file) {
        Path path = categoryPath(tab, file);
        return reader.exists(path) ? Optional.of(reader.read(path).getSuccess().orElseThrow()) : Optional.empty();
    }

    static JsonObject readJson(ResourceManager resources, ResourceLocation file) throws Exception {
        Resource resource = resources.getResource(file).orElseThrow(() -> new IllegalStateException(file + " missing"));
        try (Reader in = resource.openAsReader()) {
            return JsonParser.parseReader(in).getAsJsonObject();
        }
    }

    private static void addLinks(JsonObject connections, String group, List<String[]> pairs) {
        addLinks(connections, group, pairs, PREFIX);
    }

    private static void addLinks(JsonObject connections, String group, List<String[]> pairs, String prefix) {
        JsonObject g = connections.has(group) ? connections.getAsJsonObject(group) : new JsonObject();
        JsonArray list = g.has("bidirectional") ? g.getAsJsonArray("bidirectional") : new JsonArray();
        for (String[] pair : pairs) {
            JsonArray a = new JsonArray();
            a.add(prefix + pair[0]);
            a.add(prefix + pair[1]);
            list.add(a);
        }
        g.add("bidirectional", list);
        connections.add(group, g);
    }

    private static net.puffish.skillsmod.api.json.JsonElement wrap(JsonObject json, String name) {
        return net.puffish.skillsmod.api.json.JsonElement.parseString(json.toString(), JsonPath.create(name)).getSuccess().orElseThrow();
    }

    // ------------------------------------------------------------------------------------------------ Weapon Skills tab

    /**
     * Once per load, at the first read of a Weapon Skills file: the tab with our three nodes (Hex Bolt and Blight Burst), or nothing.
     * Same steps as the class branch; on any problem only our three nodes are missing, the tab loads like before and the class
     * branch is not touched. Stormcaller adds its Lightning Bolt group after us: its place is foreseen with its own rules and kept
     * free, so neither mod pushes the other away.
     */
    private static void prepareWeapon(ResourceManager resources) {
        if (!loading || weaponPrepared) {
            return;
        }
        weaponPrepared = true;
        SkillTreeVariant.WeaponChoice choice = SkillTreeVariant.weapon();
        if (!choice.on()) {
            lastWeaponResult = "no nodes (" + choice.reason() + ")";
            Couplings.foundAgain(WEAPON_MOD_NAME, "no Hex Bolt and Blight Burst nodes in the Weapon Skills tab (" + choice.reason() + ")");
            return;
        }
        if (!SkillsMixinPlugin.hooksReady()) {
            weaponFallback("the loading hooks of Pufferfish's Skills could not be applied (other version?)");
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            weaponFallback("no server");
            return;
        }
        computing = true;
        try {
            long start = System.nanoTime();
            Extended extended = extendWeapon(resources, server);
            for (String file : REPLACED) {
                WEAPON_FILES.put(file, extended.files().get(file));
            }
            weaponInjected = true;
            lastWeaponResult = extended.summary();
            Couplings.foundAgain(WEAPON_MOD_NAME, "Hex Bolt and Blight Burst nodes added to the " + choice.reason() + " ("
                    + extended.summary() + ")");
            Blightcaller.LOGGER.debug("[Blightcaller] Weapon Skills nodes: {} ({} ms)", extended.description(),
                    (System.nanoTime() - start) / 1_000_000);
        } catch (Exception e) {
            weaponFallback(Couplings.reason(e));
        } finally {
            computing = false;
        }
    }

    private static void weaponFallback(String reason) {
        lastWeaponResult = "no nodes (fallback: " + reason + ")";
        Couplings.forget(WEAPON_MOD_NAME);
        Blightcaller.LOGGER.warn("[Blightcaller] Skill Tree found, but the Hex Bolt and Blight Burst nodes could not be added to the "
                + "Weapon Skills tab ({}); it loads without them, taken nodes stay saved", reason);
    }

    private static Extended extendWeapon(ResourceManager resources, MinecraftServer server) throws Exception {
        PackConfigReader reader = new PackConfigReader(resources, SKILL_TREE_NAMESPACE);
        ConfigContext context = context(server, reader);
        JsonObject general = read(reader, WEAPON_TAB, "category.json");
        JsonObject definitions = read(reader, WEAPON_TAB, "definitions.json").deepCopy();
        JsonObject skills = read(reader, WEAPON_TAB, "skills.json").deepCopy();
        JsonElement connectionsElement = gson(reader.read(categoryPath(WEAPON_TAB, "connections.json")));
        if (!connectionsElement.isJsonObject()) {
            throw new IllegalStateException("connections.json of the Weapon Skills tab has the old list form");
        }
        JsonObject connections = connectionsElement.getAsJsonObject().deepCopy();
        Optional<net.puffish.skillsmod.api.json.JsonElement> experience = optional(reader, WEAPON_TAB, "experience.json");
        Optional<net.puffish.skillsmod.api.json.JsonElement> exchange = optional(reader, WEAPON_TAB, "exchange.json");
        String base = SKILL_TREE_NAMESPACE + ":puffish_skills/categories/" + WEAPON_TAB + "/";
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SKILL_TREE_NAMESPACE, WEAPON_TAB);

        check(CategoryConfig.parse(id, 0, wrap(general, base + "category.json"), wrap(definitions, base + "definitions.json"),
                wrap(skills, base + "skills.json"), wrap(connections, base + "connections.json"), experience, exchange, context),
                server, "the Weapon Skills tab fails to load on its own");

        WeaponBranchLayout.Settings settings = WeaponBranchLayout.parseOwn(readJson(resources, WEAPON_LAYOUT));
        // Stormcaller places its group after us: foresee where (its rules on the original tab) and keep those cells free
        List<double[]> reserved = new ArrayList<>();
        WeaponBranchLayout.Group peer = null;
        String peerInfo = "";
        WeaponBranchLayout.Placement stormcaller = StormcallerPeer.weaponPlace(resources, skills, connections);
        if (stormcaller != null) {
            Map<String, int[]> p = stormcaller.positions();
            for (int[] cell : p.values()) {
                reserved.add(new double[]{cell[0], cell[1]});
            }
            peer = new WeaponBranchLayout.Group(new double[]{p.get("root")[0], p.get("root")[1]},
                    List.of(new double[]{p.get("mod_1")[0], p.get("mod_1")[1]}, new double[]{p.get("mod_2")[0], p.get("mod_2")[1]}));
            peerInfo = ", Stormcaller group kept free at (" + p.get("root")[0] + ", " + p.get("root")[1] + ")";
        }
        WeaponBranchLayout.Placement placement = WeaponBranchLayout.place(skills, connections, settings, reserved, peer);

        for (Map.Entry<String, JsonElement> e : settings.definitions().entrySet()) {
            if (definitions.has(e.getKey())) {
                throw new IllegalStateException("definition id " + e.getKey() + " is already used in the Weapon Skills tab");
            }
            definitions.add(e.getKey(), e.getValue().deepCopy());
        }
        for (Map.Entry<String, int[]> e : placement.positions().entrySet()) {
            String skillId = WEAPON_PREFIX + e.getKey();
            if (skills.has(skillId)) {
                throw new IllegalStateException("node id " + skillId + " is already used in the Weapon Skills tab");
            }
            String definition = settings.nodes().get(e.getKey());
            if (!definitions.has(definition)) {
                throw new IllegalStateException("node " + e.getKey() + " uses the unknown definition " + definition);
            }
            JsonObject out = new JsonObject();
            out.addProperty("definition", definition);
            out.addProperty("x", e.getValue()[0]);
            out.addProperty("y", e.getValue()[1]);
            if ("root".equals(e.getKey())) {
                out.addProperty("root", true);
            }
            skills.add(skillId, out);
        }
        addLinks(connections, "normal", settings.normal(), WEAPON_PREFIX);
        addLinks(connections, "exclusive", settings.exclusive(), WEAPON_PREFIX);

        Map<String, net.puffish.skillsmod.api.json.JsonElement> files = new HashMap<>();
        files.put("definitions.json", wrap(definitions, base + "definitions.json (+blightcaller)"));
        files.put("skills.json", wrap(skills, base + "skills.json (+blightcaller)"));
        files.put("connections.json", wrap(connections, base + "connections.json (+blightcaller)"));
        check(CategoryConfig.parse(id, 0, wrap(general, base + "category.json"), files.get("definitions.json"), files.get("skills.json"),
                files.get("connections.json"), experience, exchange, context), server, "the extended Weapon Skills tab does not load");
        int[] root = placement.positions().get("root");
        return new Extended(files, "", "fixed node at (" + root[0] + ", " + root[1] + ")" + peerInfo, placement.description() + peerInfo);
    }

    // ------------------------------------------------------------------------------------------------ Stormcaller

    /**
     * Stormcaller (same author) adds its branch to the Class Skills tab after us. Read only: its layout file (data) and, by
     * reflection, whether it is going to hook in ({@code SkillTreeVariant.choose().classTree()} and its mixin check). No
     * compile dependency; if anything of it cannot be read, Blightcaller assumes it hooks in (the cap only keeps the circle
     * closer).
     */
    private record StormcallerPeer(List<ClassBranchLayout.Form> forms) {
        @Nullable
        static StormcallerPeer of(ResourceManager resources) {
            if (!ModList.get().isLoaded("stormcaller") || !hooksIn()) {
                return null;
            }
            try {
                return new StormcallerPeer(ClassBranchLayout.parse(readJson(resources, STORMCALLER_LAYOUT)).forms());
            } catch (Exception e) {
                Blightcaller.LOGGER.debug("[Blightcaller] layout of the Stormcaller branch not readable: {}", e.toString());
                return null;
            }
        }

        /**
         * Where Stormcaller will put its Lightning Bolt group in the Weapon Skills tab (its own rules on the tab without us), or
         * {@code null} if it does not hook in there or its data cannot be read.
         */
        @Nullable
        static WeaponBranchLayout.Placement weaponPlace(ResourceManager resources, JsonObject skills, JsonObject connections) {
            if (!ModList.get().isLoaded("stormcaller") || !weaponHooksIn()) {
                return null;
            }
            try {
                WeaponBranchLayout.Settings settings = WeaponBranchLayout.parse(readJson(resources, STORMCALLER_WEAPON_LAYOUT));
                // its own search: no row rule, no reserved cells
                WeaponBranchLayout.Settings plain = new WeaponBranchLayout.Settings(settings.near(), false, false, settings.maxAngle(),
                        settings.freeFactor(), settings.nodes(), settings.normal(), settings.exclusive(), settings.definitions());
                return WeaponBranchLayout.place(skills, connections, plain);
            } catch (Exception e) {
                Blightcaller.LOGGER.debug("[Blightcaller] place of the Stormcaller weapon group not foreseen: {}", e.toString());
                return null;
            }
        }

        private static boolean weaponHooksIn() {
            try {
                Class<?> variant = Class.forName("com.drehverschluss.stormcaller.compat.skilltree.SkillTreeVariant");
                Object choice = variant.getMethod("weapon").invoke(null);
                boolean on = (Boolean) choice.getClass().getMethod("on").invoke(choice);
                Method ready = Class.forName("com.drehverschluss.stormcaller.mixin.skills.SkillsMixinPlugin").getMethod("hooksReady");
                return on && (Boolean) ready.invoke(null);
            } catch (Throwable t) {
                Blightcaller.LOGGER.debug("[Blightcaller] Stormcaller weapon state not readable, assuming it hooks in: {}", t.toString());
                return true;
            }
        }

        private static boolean hooksIn() {
            try {
                Class<?> variant = Class.forName("com.drehverschluss.stormcaller.compat.skilltree.SkillTreeVariant");
                Object choice = variant.getMethod("choose").invoke(null);
                boolean classTree = (Boolean) choice.getClass().getMethod("classTree").invoke(choice);
                Method ready = Class.forName("com.drehverschluss.stormcaller.mixin.skills.SkillsMixinPlugin").getMethod("hooksReady");
                return classTree && (Boolean) ready.invoke(null);
            } catch (Throwable t) {
                Blightcaller.LOGGER.debug("[Blightcaller] Stormcaller skill tree state not readable, assuming it hooks in: {}", t.toString());
                return true;
            }
        }

        /** Whether the tab with our branch makes Stormcaller pick another form than the original tab. */
        boolean changesForm(JsonObject original, ClassBranchLayout.Placement placement) {
            double before = ClassBranchLayout.rootRadius(original);
            int roots = (int) original.entrySet().stream()
                    .filter(e -> e.getValue().getAsJsonObject().has("root") && e.getValue().getAsJsonObject().get("root").getAsBoolean())
                    .count();
            double sum = 0;
            for (Map.Entry<String, JsonElement> e : original.entrySet()) {
                JsonObject node = e.getValue().getAsJsonObject();
                if (node.has("root") && node.get("root").getAsBoolean()) {
                    int[] moved = placement.moved().get(e.getKey());
                    sum += moved != null ? Math.hypot(moved[0], moved[1]) : Math.hypot(node.get("x").getAsDouble(), node.get("y").getAsDouble());
                }
            }
            int[] ourRoot = placement.ours().get("root");
            double after = (sum + Math.hypot(ourRoot[0], ourRoot[1])) / (roots + 1);
            return ClassBranchLayout.chooseForm(forms, before) != ClassBranchLayout.chooseForm(forms, after);
        }
    }
}
