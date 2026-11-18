package com.behindqou.spear;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

public class SpearEngine {
    public static boolean isActive = false;

    private static final double SPEED = 1.5;            // blocks per tick
    private static final double SEARCH_RADIUS = 64;
    private static final double REACH_SQ = 3.0 * 3.0;   // survival melee reach
    private static final long HIT_COOLDOWN_MS = 100;
    private static final int PASS_THROUGH_TICKS = 6;    // keep flying straight after a hit
    private static final int MAX_HITS = 999;

    private static LivingEntity target = null;
    private static Vec3d lastDir = new Vec3d(0, 0, 1);
    private static int passThrough = 0;
    private static int hitCount = 0;
    private static long lastHitTime = 0;
    private static boolean movingUp = true;
    private static double verticalOffset = 0;

    private static final List<EntityType<?>> ALLOWED_ENTITIES = List.of(
        EntityType.PLAYER, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
        EntityType.SPIDER, EntityType.WITHER_SKELETON, EntityType.STRAY, EntityType.HUSK,
        EntityType.DROWNED, EntityType.WOLF, EntityType.OCELOT, EntityType.IRON_GOLEM,
        EntityType.EVOKER, EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.RAVAGER,
        EntityType.BAT, EntityType.BEE, EntityType.CAT, EntityType.CHICKEN, EntityType.COW,
        EntityType.HOGLIN, EntityType.HORSE, EntityType.MOOSHROOM, EntityType.MULE,
        EntityType.PIG, EntityType.RABBIT, EntityType.SHEEP, EntityType.SNIFFER,
        EntityType.TRADER_LLAMA, EntityType.TURTLE, EntityType.VILLAGER,
        EntityType.WANDERING_TRADER, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN
    );

    public static void onClientTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (!isActive || player == null || client.world == null) {
            return;
        }

        if (!player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            stop(client, "Spear Kill: equip an Elytra");
            return;
        }

        // Make sure we are gliding (server must be told too)
        if (!player.isFallFlying()) {
            if (player.isOnGround()) {
                return; // wait until the player jumps
            }
            player.startFallFlying();
            player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }

        // Keep flying straight for a few ticks after a hit so we pass through the target
        if (passThrough > 0) {
            passThrough--;
            player.setVelocity(lastDir.multiply(SPEED));
            return;
        }

        if (target == null || !target.isAlive() || target.isRemoved()) {
            target = findNearestTarget(client);
            if (target == null) {
                stop(client, "Spear Kill: no targets in range");
                return;
            }
        }

        Vec3d to = target.getBoundingBox().getCenter().subtract(player.getBoundingBox().getCenter());
        double len = to.length();
        Vec3d dir = len > 1.0E-4 ? to.multiply(1.0 / len) : lastDir;
        lastDir = dir;

        aim(player, dir);

        // Small vertical wobble
        verticalOffset += movingUp ? 0.2 : -0.2;
        if (verticalOffset >= 1.0) movingUp = false;
        if (verticalOffset <= -1.0) movingUp = true;

        player.setVelocity(dir.x * SPEED, dir.y * SPEED + (movingUp ? 0.05 : -0.05), dir.z * SPEED);

        if (player.squaredDistanceTo(target) <= REACH_SQ) {
            long now = System.currentTimeMillis();
            if (now - lastHitTime > HIT_COOLDOWN_MS && client.interactionManager != null) {
                lastHitTime = now;
                hitCount++;
                client.interactionManager.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);
                passThrough = PASS_THROUGH_TICKS;
            }
            if (hitCount > MAX_HITS) {
                stop(client, "Spear Kill: max hits reached");
            }
        }
    }

    private static LivingEntity findNearestTarget(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        Box area = player.getBoundingBox().expand(SEARCH_RADIUS);
        List<LivingEntity> candidates = client.world.getEntitiesByClass(
            LivingEntity.class,
            area,
            e -> e != player && e.isAlive() && ALLOWED_ENTITIES.contains(e.getType())
        );
        return candidates.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)))
            .orElse(null);
    }

    private static void aim(ClientPlayerEntity player, Vec3d dir) {
        double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yaw = (float) (MathHelper.atan2(-dir.x, dir.z) * 180.0 / Math.PI);
        float pitch = (float) (-(MathHelper.atan2(dir.y, horizontal) * 180.0 / Math.PI));
        player.setYaw(yaw);
        player.setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
    }

    private static void stop(MinecraftClient client, String message) {
        isActive = false;
        reset();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), true);
        }
    }

    public static void reset() {
        target = null;
        passThrough = 0;
        hitCount = 0;
        lastHitTime = 0;
        verticalOffset = 0;
        movingUp = true;
    }
}
