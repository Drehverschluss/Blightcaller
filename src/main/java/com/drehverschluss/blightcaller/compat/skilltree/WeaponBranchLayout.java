package com.drehverschluss.blightcaller.compat.skilltree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Place of a group of three nodes in the Weapon Skills tab (honeycomb of weapon groups: one fixed node and two choices). Taken over
 * from Stormcaller's {@code WeaponBranchLayout} (MIT, same author) and extended for two mods in the same tab:
 * <ul>
 *     <li>The grid (row step and half step) comes from the data; a free triangle of cells is searched that points the same way as
 *     the groups of the roots in {@code near}, as close to them as possible.</li>
 *     <li>{@code align_rows}: triangles whose root lies in the same group rows as the reference roots (every second grid row) come
 *     first, so the group lines up with the staves instead of sitting between two rows.</li>
 *     <li>{@code reserved}: cells another mod is going to fill after us (Stormcaller's Lightning Bolt group). They count as taken,
 *     and with {@code near_peer} that group is a reference group too ("next to the Lightning Staff").</li>
 * </ul>
 * With no reserved cells, no peer and {@code align_rows} off this is exactly Stormcaller's search, used to foresee its place.
 * Pure maths on the JSON of the tab, no Pufferfish's Skills classes. Settings in
 * {@code blightcaller:blightcaller_skill_tree/weapon_branch.json}.
 */
public final class WeaponBranchLayout {
    private WeaponBranchLayout() {}

    public record Settings(List<String> near, boolean nearPeer, boolean alignRows, double maxAngle, double freeFactor,
                           Map<String, String> nodes, List<String[]> normal, List<String[]> exclusive, JsonObject definitions) {}

    /** @param positions node key of the file (root, mod_1, mod_2) -> position */
    public record Placement(Map<String, int[]> positions, String description) {}

    /** A group of another mod that is not in the tab yet: its root and its choices. */
    public record Group(double[] root, List<double[]> mods) {}

    private record Node(String id, String definition, double x, double y) {}

    public static Settings parse(JsonObject json) {
        JsonObject place = json.getAsJsonObject("place");
        List<String> near = new ArrayList<>();
        for (JsonElement e : place.getAsJsonArray("near")) {
            near.add(e.getAsString());
        }
        Map<String, String> nodes = new LinkedHashMap<>();
        if (json.has("nodes")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("nodes").entrySet()) {
                nodes.put(e.getKey(), e.getValue().getAsString());
            }
        }
        JsonObject links = json.has("links") ? json.getAsJsonObject("links") : new JsonObject();
        return new Settings(near, place.has("near_peer") && place.get("near_peer").getAsBoolean(),
                place.has("align_rows") && place.get("align_rows").getAsBoolean(), place.get("max_angle").getAsDouble(),
                place.get("free_factor").getAsDouble(), nodes, pairs(links.getAsJsonArray("normal"), nodes),
                pairs(links.getAsJsonArray("exclusive"), nodes), json.has("definitions") ? json.getAsJsonObject("definitions") : new JsonObject());
    }

    /** Our own file: needs the three nodes. */
    public static Settings parseOwn(JsonObject json) {
        Settings settings = parse(json);
        for (String key : List.of("root", "mod_1", "mod_2")) {
            if (!settings.nodes().containsKey(key)) {
                throw new IllegalArgumentException("weapon_branch.json: node '" + key + "' missing");
            }
        }
        return settings;
    }

    private static List<String[]> pairs(@Nullable JsonArray array, Map<String, String> nodes) {
        List<String[]> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (JsonElement e : array) {
            JsonArray p = e.getAsJsonArray();
            String a = p.get(0).getAsString();
            String b = p.get(1).getAsString();
            if (!nodes.containsKey(a) || !nodes.containsKey(b)) {
                throw new IllegalArgumentException("weapon_branch.json: link to an unknown node: " + a + " - " + b);
            }
            out.add(new String[]{a, b});
        }
        return out;
    }

    /** Stormcaller's search (no reserved cells, no peer, no row rule). */
    public static Placement place(JsonObject skills, JsonObject connections, Settings settings) throws ClassBranchLayout.NoPlace {
        return place(skills, connections, settings, List.of(), null);
    }

    public static Placement place(JsonObject skills, JsonObject connections, Settings settings, List<double[]> reserved, @Nullable Group peer)
            throws ClassBranchLayout.NoPlace {
        List<Node> all = new ArrayList<>();
        Map<String, Node> byId = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : skills.entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            Node n = new Node(e.getKey(), o.has("definition") ? o.get("definition").getAsString() : "?", o.get("x").getAsDouble(),
                    o.get("y").getAsDouble());
            all.add(n);
            byId.put(n.id, n);
        }
        if (all.size() < 6) {
            throw new ClassBranchLayout.NoPlace("weapon tab has fewer than 6 nodes");
        }
        double dy = rowStep(all);
        double dx = halfStep(all, dy);
        if (dy <= 0 || dx <= 0) {
            throw new ClassBranchLayout.NoPlace("no honeycomb grid found");
        }
        Node base = gridBase(all, dx, dy);

        // neighbours over the connections (a weapon group = root and its two choices)
        Map<String, List<String>> neighbours = new HashMap<>();
        for (String group : new String[]{"normal", "exclusive"}) {
            if (!connections.has(group) || !connections.get(group).isJsonObject()) {
                continue;
            }
            for (String kind : new String[]{"bidirectional", "unidirectional"}) {
                JsonObject g = connections.getAsJsonObject(group);
                if (!g.has(kind)) {
                    continue;
                }
                for (JsonElement e : g.getAsJsonArray(kind)) {
                    JsonArray p = e.getAsJsonArray();
                    neighbours.computeIfAbsent(p.get(0).getAsString(), k -> new ArrayList<>()).add(p.get(1).getAsString());
                    neighbours.computeIfAbsent(p.get(1).getAsString(), k -> new ArrayList<>()).add(p.get(0).getAsString());
                }
            }
        }
        // the reference groups: roots in 'near', plus the peer group if wanted
        List<Group> groups = new ArrayList<>();
        List<String> foundNames = new ArrayList<>();
        for (String definition : settings.near()) {
            Node root = all.stream().filter(n -> n.definition.equals(definition) && skills.getAsJsonObject(n.id).has("root")
                    && skills.getAsJsonObject(n.id).get("root").getAsBoolean()).findFirst().orElse(null);
            if (root == null) {
                continue;
            }
            List<double[]> mods = neighbours.getOrDefault(root.id, List.of()).stream().map(byId::get).filter(n -> n != null)
                    .map(n -> new double[]{n.x, n.y}).toList();
            if (mods.isEmpty()) {
                continue;
            }
            groups.add(new Group(new double[]{root.x, root.y}, mods));
            foundNames.add(definition);
        }
        if (peer != null && settings.nearPeer()) {
            groups.add(peer);
            foundNames.add("the Lightning Staff group of Stormcaller");
        }
        if (groups.isEmpty()) {
            throw new ClassBranchLayout.NoPlace("none of the reference groups " + settings.near() + " found");
        }
        double ox = 0;
        double oy = 0;
        double tx = 0;
        double ty = 0;
        for (Group g : groups) {
            double mx = g.mods().stream().mapToDouble(m -> m[0]).average().orElseThrow();
            double my = g.mods().stream().mapToDouble(m -> m[1]).average().orElseThrow();
            double len = Math.hypot(mx - g.root()[0], my - g.root()[1]);
            ox += (mx - g.root()[0]) / len;
            oy += (my - g.root()[1]) / len;
            tx += (g.root()[0] + 2 * mx) / 3;
            ty += (g.root()[1] + 2 * my) / 3;
        }
        double ol = Math.hypot(ox, oy);
        ox /= ol;
        oy /= ol;
        tx /= groups.size();
        ty /= groups.size();

        List<double[]> taken = new ArrayList<>();
        for (Node n : all) {
            taken.add(new double[]{n.x, n.y});
        }
        taken.addAll(reserved);

        int[][] around = {{2, 0}, {1, 1}, {-1, 1}, {-2, 0}, {-1, -1}, {1, -1}};
        double cosMax = Math.cos(Math.toRadians(settings.maxAngle()));
        double minFree = 2 * dx * settings.freeFactor();
        double best = Double.MAX_VALUE;
        double[][] bestCells = null;
        double bestFree = 0;
        boolean bestAligned = false;
        int range = 4 + (int) Math.ceil(Math.max(span(all, true) / dx, span(all, false) / dy));
        for (int i = -range; i <= range; i++) {
            for (int j = -range; j <= range; j++) {
                if (((i + j) & 1) != 0) {
                    continue;
                }
                double rx = base.x + i * dx;
                double ry = base.y + j * dy;
                for (int k = 0; k < 6; k++) {
                    double ax = rx + around[k][0] * dx;
                    double ay = ry + around[k][1] * dy;
                    double bx = rx + around[(k + 1) % 6][0] * dx;
                    double by = ry + around[(k + 1) % 6][1] * dy;
                    double vx = (ax + bx) / 2 - rx;
                    double vy = (ay + by) / 2 - ry;
                    if ((vx * ox + vy * oy) / Math.hypot(vx, vy) < cosMax) {
                        continue;
                    }
                    double free = Double.MAX_VALUE;
                    for (double[] n : taken) {
                        free = Math.min(free, Math.min(Math.hypot(rx - n[0], ry - n[1]),
                                Math.min(Math.hypot(ax - n[0], ay - n[1]), Math.hypot(bx - n[0], by - n[1]))));
                    }
                    if (free < minFree) {
                        continue;
                    }
                    double score = Math.hypot((rx + ax + bx) / 3 - tx, (ry + ay + by) / 3 - ty);
                    boolean aligned = settings.alignRows() && aligned(ry, groups, dy);
                    if (settings.alignRows() && !aligned) {
                        score += 10000.0;
                    }
                    if (score < best - 1e-6) {
                        best = score;
                        bestFree = free;
                        bestAligned = aligned;
                        // mod_1 left of the direction, mod_2 right (screen y points down)
                        boolean aLeft = cross(ox, oy, ax - rx, ay - ry) < cross(ox, oy, bx - rx, by - ry);
                        bestCells = aLeft ? new double[][]{{rx, ry}, {ax, ay}, {bx, by}} : new double[][]{{rx, ry}, {bx, by}, {ax, ay}};
                    }
                }
            }
        }
        if (bestCells == null) {
            throw new ClassBranchLayout.NoPlace("no free group of three cells pointing like " + foundNames);
        }
        Map<String, int[]> out = new LinkedHashMap<>();
        String[] keys = {"root", "mod_1", "mod_2"};
        for (int i = 0; i < 3; i++) {
            out.put(keys[i], new int[]{(int) Math.round(bestCells[i][0]), (int) Math.round(bestCells[i][1])});
        }
        return new Placement(out, String.format(Locale.ROOT,
                "grid %.1f x %.1f, near %s, fixed node at (%d, %d)%s, %.0f px from them, closest other node %.1f px",
                2 * dx, dy, String.join(", ", foundNames), out.get("root")[0], out.get("root")[1],
                settings.alignRows() ? (bestAligned ? " in their rows" : " (no free place in their rows)") : "",
                best % 10000.0, bestFree));
    }

    /** The root lies in the same group rows as all reference roots (every second grid row). */
    private static boolean aligned(double ry, List<Group> groups, double dy) {
        for (Group g : groups) {
            double steps = (ry - g.root()[1]) / (2 * dy);
            if (Math.abs(steps - Math.rint(steps)) > 0.01) {
                return false;
            }
        }
        return true;
    }

    /** z of the cross product: negative = left of the direction on screen (y down). */
    private static double cross(double ox, double oy, double vx, double vy) {
        return ox * vy - oy * vx;
    }

    /** Most common step between neighbouring rows (robust against single nodes off the grid). */
    private static double rowStep(List<Node> all) {
        List<Double> ys = all.stream().map(n -> Math.round(n.y * 2) / 2.0).distinct().sorted().toList();
        return mode(ys.size() < 2 ? List.of() : diffs(ys));
    }

    private static List<Double> diffs(List<Double> sorted) {
        List<Double> out = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            out.add(sorted.get(i) - sorted.get(i - 1));
        }
        return out;
    }

    /** Most common smallest horizontal distance to a node in the next row (the half step of the honeycomb). */
    private static double halfStep(List<Node> all, double dy) {
        List<Double> steps = new ArrayList<>();
        for (Node a : all) {
            double min = Double.MAX_VALUE;
            for (Node b : all) {
                if (Math.abs(Math.abs(a.y - b.y) - dy) < 0.5) {
                    min = Math.min(min, Math.abs(a.x - b.x));
                }
            }
            if (min < Double.MAX_VALUE && min > 0.5) {
                steps.add(Math.round(min * 100) / 100.0);
            }
        }
        return mode(steps);
    }

    private static double mode(List<Double> values) {
        Map<Double, Integer> count = new HashMap<>();
        for (double v : values) {
            if (v > 0.5) {
                count.merge(v, 1, Integer::sum);
            }
        }
        return count.entrySet().stream().max((a, b) -> a.getValue().equals(b.getValue()) ? Double.compare(b.getKey(), a.getKey())
                : Integer.compare(a.getValue(), b.getValue())).map(Map.Entry::getKey).orElse(0.0);
    }

    /** The node most other nodes line up with on the grid. */
    private static Node gridBase(List<Node> all, double dx, double dy) {
        Node best = all.get(0);
        int bestCount = -1;
        for (Node a : all) {
            int count = 0;
            for (Node b : all) {
                double i = (b.x - a.x) / dx;
                double j = (b.y - a.y) / dy;
                if (Math.abs(i - Math.round(i)) < 0.05 && Math.abs(j - Math.round(j)) < 0.05 && ((Math.round(i) + Math.round(j)) & 1) == 0) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = a;
            }
        }
        return best;
    }

    private static double span(List<Node> all, boolean x) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Node n : all) {
            double v = x ? n.x : n.y;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return max - min;
    }
}
