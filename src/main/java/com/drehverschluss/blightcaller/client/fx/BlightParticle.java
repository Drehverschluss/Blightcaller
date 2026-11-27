package com.drehverschluss.blightcaller.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.fx.ParticleGroupType;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Blightcaller's own particle set (phase 14A): rot smoke, rot drip, spore, hex ember, hex skull and rune. One class, the
 * behaviour per {@link Kind}. Textures from {@code Konzept/Werkzeuge/gen_14a_particles.py} (runes from PixelLab), sprite
 * lists in {@code assets/blightcaller/particles/}.
 * <ul>
 *     <li><b>Look from the spawner:</b> a Spell Engine appearance (spell file or {@code BlightParticleType.with}) sets colour
 *     (RGBA, -1 = the kind's own), scale (factor), glow and, for BURST / DECELERATE / STATIC / ASCEND, the motion.</li>
 *     <li><b>Particle setting:</b> Spell Engine spawns with {@code force}, so the setting is applied here: decreased keeps
 *     two of three, minimal one of eight (spells stay readable, like the 10 % vanilla keeps for some particles).</li>
 *     <li><b>Tinted:</b> the textures are grey with a darker rim, the colour comes from the tint (smoke, drip, spore, ember).
 *     The skull is drawn in two layers: the bone, always lit by the world (a glow from the spawner does not make it bright)
 *     and tinted a quarter towards the colour, and the eyes, always full bright in the colour shifted a little towards blue
 *     (with Complementary they measured 292 degrees, next to Arcane). Runes keep the colours of their texture (bright
 *     core from PixelLab), a colour from the spawner multiplies them.</li>
 *     <li><b>Glow:</b> drips always glow (else they are weaker than Spell Engine's dripping_blood where a spell sets no
 *     glow); smoke only where the spawner asks for it (user, 27.11.).</li>
 * </ul>
 * Client only, created by the providers registered in {@link FxClient}.
 */
public class BlightParticle extends TextureSheetParticle {
    /** Number of smoke shapes; the sprite list is stage-major: all shapes of stage 0, then stage 1, then stage 2. */
    static final int SMOKE_SHAPES = 4;
    static final int SMOKE_STAGES = 3;
    /** How much the skull's bone takes on the colour (0 = bone white, 1 = fully tinted). */
    static final float SKULL_BONE_TINT = 0.25F;
    /** The skull's eyes: red share of the colour kept (less red = bluer; #6F5BE8 goes from 248 to about 233 degrees). */
    static final float SKULL_EYE_RED = 0.65F;

    /**
     * The six kinds. Sizes are the half width of the quad in blocks (Minecraft's quadSize), chosen next to the measured
     * vanilla and Spell Engine particles (phase 14A report). Colours RGBA like Spell Engine.
     */
    public enum Kind {
        // size at scale 1 next to the Spell Engine particle it replaces (measured in phase 14A, half widths: smoke_medium
        // 0.121, dripping_blood 0.082, magic_spark 0.102, magic_skull 0.115, magic_stripe 0.116), so the scales in the spell
        // files carry over; skull and runes a little bigger because their 16 px textures have a transparent border.
        // Lives a little longer than Spell Engine's (12 to 28 ticks), but not so long that many more live at once.
        //          size   life  var  colour       glow
        ROT_SMOKE(0.11F, 28, 8, 0x5FBF5AFFL, false),
        ROT_DRIP(0.08F, 30, 8, 0x5FBF5AFFL, true),
        SPORE(0.10F, 30, 10, 0xC4D445FFL, true),
        HEX_EMBER(0.10F, 14, 4, 0x6F5BE8FFL, true),
        HEX_SKULL(0.13F, 20, 4, 0x6F5BE8FFL, false),
        RUNE(0.13F, 22, 6, -1L, true);

        final float size;
        final int life;
        final int lifeVariance;
        final long color;
        final boolean glow;

        Kind(float size, int life, int lifeVariance, long color, boolean glow) {
            this.size = size;
            this.life = life;
            this.lifeVariance = lifeVariance;
            this.color = color;
            this.glow = glow;
        }
    }

    private final Kind kind;
    private final SpriteSet sprites;
    private final boolean glow;
    private final float baseSize;
    private final float baseAlpha;
    private final float spin;
    private final int shape;
    /** Hex skull: the eye layer and its colour; null for the other kinds. */
    @Nullable
    private TextureAtlasSprite eyes;
    private float eyeR = 1.0F;
    private float eyeG = 1.0F;
    private float eyeB = 1.0F;
    private boolean eyePass;
    /** Rot drip: ticks it still hangs, -1 while falling, ticks of splash left (counted down from 4) once landed. */
    private int hang;
    private int splash = -1;
    private final float wobble;

    protected BlightParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, Kind kind, SpriteSet sprites,
                             @Nullable ParticleGroup.Appearance look) {
        super(level, x, y, z);
        this.kind = kind;
        this.sprites = sprites;
        // drips always glow; the skull's bone never (its eyes always do, see renderRotatedQuad)
        this.glow = kind == Kind.ROT_DRIP || (kind != Kind.HEX_SKULL && (look != null && look.glow != null ? look.glow : kind.glow));
        float scale = look != null && look.scale > 0.0F ? look.scale : 1.0F;
        this.baseSize = kind.size * scale * (0.85F + random.nextFloat() * 0.3F);
        this.quadSize = baseSize;
        this.lifetime = Math.max(4, kind.life + random.nextInt(kind.lifeVariance * 2 + 1) - kind.lifeVariance);
        this.spin = (random.nextFloat() - 0.5F) * 0.08F;
        this.wobble = random.nextFloat() * Mth.TWO_PI;
        this.shape = random.nextInt(SMOKE_SHAPES);
        long color = look != null && look.color >= 0L ? look.color : kind.color;
        float alpha = 1.0F;
        if (color >= 0L) {
            float r = ((color >> 24) & 0xFF) / 255.0F;
            float g = ((color >> 16) & 0xFF) / 255.0F;
            float b = ((color >> 8) & 0xFF) / 255.0F;
            alpha = (color & 0xFF) / 255.0F;
            if (kind == Kind.HEX_SKULL) {
                eyeR = r * SKULL_EYE_RED;
                eyeG = g;
                eyeB = b;
                setColor(Mth.lerp(SKULL_BONE_TINT, 1.0F, r), Mth.lerp(SKULL_BONE_TINT, 1.0F, g), Mth.lerp(SKULL_BONE_TINT, 1.0F, b));
            } else {
                setColor(r, g, b);
            }
        }
        this.baseAlpha = alpha * (look != null ? Mth.clamp(look.opacity, 0.0F, 1.0F) : 1.0F);
        this.alpha = 0.0F;
        this.hasPhysics = kind == Kind.ROT_DRIP;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        // own motion per kind; a few motions from Spell Engine's appearance are honoured on top
        switch (kind) {
            case ROT_SMOKE -> {
                friction = 0.92F;
                gravity = -0.06F;
                roll = random.nextFloat() * Mth.TWO_PI;
                oRoll = roll;
            }
            case ROT_DRIP -> {
                friction = 0.9F;
                gravity = 0.0F;
                hang = 3 + random.nextInt(6);
                xd *= 0.3;
                zd *= 0.3;
                yd = Math.min(yd, 0.0) * 0.3;
            }
            case SPORE -> {
                friction = 0.97F;
                gravity = -0.004F;
            }
            case HEX_EMBER -> {
                friction = 0.86F;
                gravity = -0.02F;
            }
            case HEX_SKULL -> {
                friction = 0.88F;
                gravity = -0.05F;
                roll = (random.nextFloat() - 0.5F) * 0.35F;
                oRoll = roll;
            }
            case RUNE -> {
                friction = 0.85F;
                gravity = -0.006F;
            }
        }
        ParticleGroup.Motion motion = look != null ? look.motion : null;
        if (motion == ParticleGroup.Motion.BURST) {
            friction = 0.72F;
            lifetime = Math.max(4, Math.round(lifetime * 0.75F));
        } else if (motion == ParticleGroup.Motion.DECELERATE) {
            friction = 0.77F;
        } else if (motion == ParticleGroup.Motion.STATIC) {
            friction = 1.0F;
            gravity = 0.0F;
            xd = yd = zd = 0.0;
        } else if (motion == ParticleGroup.Motion.ASCEND && kind != Kind.ROT_DRIP) {
            gravity = Math.min(gravity, -0.03F);
        }
        pickSprites(0.0F);
    }

    /** The sprite (and for the skull the eye layer) for a share of the life. */
    private void pickSprites(float share) {
        switch (kind) {
            case ROT_SMOKE -> {
                int stage = Mth.clamp((int) (share * SMOKE_STAGES), 0, SMOKE_STAGES - 1);
                setSprite(sprites.get(stage * SMOKE_SHAPES + shape, SMOKE_SHAPES * SMOKE_STAGES - 1));
            }
            case ROT_DRIP -> {
                // 0, 1 hanging (the second longer), 2 falling, 3 and 4 the splash
                int frame = splash >= 0 ? (splash >= 2 ? 3 : 4) : hang > 0 ? (hang > 2 ? 0 : 1) : 2;
                setSprite(sprites.get(frame, 4));
            }
            case HEX_EMBER -> setSprite(sprites.get(Mth.clamp((int) (share * 4), 0, 3), 3));
            case HEX_SKULL -> {
                setSprite(sprites.get(0, 1));
                eyes = sprites.get(1, 1);
            }
            default -> {
                // spores and runes: one random shape for the whole life
                if (sprite == null) {
                    pickSprite(sprites);
                }
            }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return glow || eyePass ? 0xF000F0 : super.getLightColor(partialTick);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = Mth.clamp((age + partialTick) / lifetime, 0.0F, 1.0F);
        return switch (kind) {
            // swells while it rises and thins out
            case ROT_SMOKE -> quadSize * (0.75F + 0.75F * (1.0F - (1.0F - t) * (1.0F - t)));
            // pops up, then stays
            case RUNE, HEX_SKULL -> quadSize * Mth.clamp(0.55F + (age + partialTick) * 0.15F, 0.55F, 1.0F);
            // the ember burns down
            case HEX_EMBER -> quadSize * (1.0F - 0.55F * t);
            default -> quadSize;
        };
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float t = age / (float) lifetime;
        oRoll = roll;
        switch (kind) {
            case ROT_SMOKE -> {
                roll += spin;
                // a little sideways drift, then the usual movement
                xd += (random.nextFloat() - 0.5F) * 0.004F;
                zd += (random.nextFloat() - 0.5F) * 0.004F;
                alpha = baseAlpha * fade(t, 0.12F, 0.45F) * 0.85F;
            }
            case ROT_DRIP -> {
                if (tickDrip()) {
                    return;
                }
            }
            case SPORE -> {
                // drifting on the air, a soft wobble and a flicker
                float w = wobble + age * 0.25F;
                xd += Mth.cos(w) * 0.0015F;
                zd += Mth.sin(w * 0.8F) * 0.0015F;
                float flicker = 0.7F + 0.3F * Mth.sin(wobble * 3.0F + age * 0.9F);
                alpha = baseAlpha * fade(t, 0.15F, 0.3F) * flicker;
            }
            case HEX_EMBER -> {
                float flicker = 0.75F + 0.25F * random.nextFloat();
                alpha = baseAlpha * fade(t, 0.05F, 0.35F) * flicker;
            }
            case HEX_SKULL -> {
                roll = (oRoll * 0.9F) + Mth.sin(wobble + age * 0.3F) * 0.012F;
                alpha = baseAlpha * fade(t, 0.12F, 0.35F);
            }
            case RUNE -> {
                float flicker = 0.85F + 0.15F * Mth.sin(wobble + age * 1.3F);
                alpha = baseAlpha * fade(t, 0.1F, 0.4F) * flicker;
            }
        }
        pickSprites(t);
        if (kind != Kind.ROT_DRIP) {
            yd -= 0.04 * gravity;
            move(xd, yd, zd);
            xd *= friction;
            yd *= friction;
            zd *= friction;
        }
    }

    /** Hangs, falls (thick: slow and damped), splashes for 4 ticks on the ground. True when removed. */
    private boolean tickDrip() {
        float t = age / (float) lifetime;
        alpha = baseAlpha * fade(t, 0.05F, 0.15F);
        if (splash >= 0) {
            if (--splash < 0) {
                remove();
                return true;
            }
            pickSprites(t);
            return false;
        }
        if (hang > 0) {
            hang--;
            // creeps down a little while it hangs
            move(xd * 0.2, -0.004, zd * 0.2);
        } else {
            yd -= 0.035;
            move(xd, yd, zd);
            xd *= friction;
            zd *= friction;
            yd *= 0.96;
            if (onGround) {
                splash = 4;
                lifetime = Math.max(lifetime, age + 5);
            }
        }
        pickSprites(t);
        return false;
    }

    /** Fades in over the first share and out over the last share of the life. */
    private static float fade(float t, float in, float out) {
        float a = in > 0.0F ? Mth.clamp(t / in, 0.0F, 1.0F) : 1.0F;
        float b = out > 0.0F ? Mth.clamp((1.0F - t) / out, 0.0F, 1.0F) : 1.0F;
        return Math.min(a, b);
    }

    @Override
    protected void renderRotatedQuad(VertexConsumer buffer, Quaternionf quaternion, float x, float y, float z, float partialTicks) {
        super.renderRotatedQuad(buffer, quaternion, x, y, z, partialTicks);
        if (eyes == null) {
            return;
        }
        // the skull's eyes: a second quad a hair in front, full bright and tinted
        TextureAtlasSprite bone = sprite;
        float r = rCol;
        float g = gCol;
        float b = bCol;
        sprite = eyes;
        rCol = eyeR;
        gCol = eyeG;
        bCol = eyeB;
        eyePass = true;
        float k = 0.995F;
        super.renderRotatedQuad(buffer, quaternion, x * k, y * k, z * k, partialTicks);
        eyePass = false;
        sprite = bone;
        rCol = r;
        gCol = g;
        bCol = b;
    }

    /** Thins the particles out like Minecraft's particle setting (Spell Engine spawns them with force). */
    static boolean skipBySetting(ClientLevel level) {
        ParticleStatus status = Minecraft.getInstance().options.particles().get();
        return switch (status) {
            case ALL -> false;
            case DECREASED -> level.random.nextInt(3) == 0;
            case MINIMAL -> level.random.nextInt(8) != 0;
        };
    }

    /** Provider of one kind, registered with its sprite list in {@link FxClient}. */
    public record Provider(Kind kind, SpriteSet sprites) implements ParticleProvider<ParticleGroupType> {
        @Override
        @Nullable
        public Particle createParticle(ParticleGroupType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            if (skipBySetting(level)) {
                return null;
            }
            return new BlightParticle(level, x, y, z, vx, vy, vz, kind, sprites, type.payload());
        }
    }
}
