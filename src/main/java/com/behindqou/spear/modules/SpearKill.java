package com.behindqou.spear.modules;

import com.behindqou.spear.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class SpearKill extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away to look for targets.")
        .defaultValue(64.0)
        .min(5.0)
        .sliderMax(128.0)
        .build());

    private final Setting<Integer> passThrough = sgGeneral.add(new IntSetting.Builder()
        .name("pass-through-ticks")
        .description("Ticks to keep flying straight after a hit.")
        .defaultValue(6)
        .min(0)
        .sliderMax(20)
        .build());

    private final Setting<Integer> hitDelay = sgGeneral.add(new IntSetting.Builder()
        .name("hit-delay")
        .description("Ticks between attacks.")
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .build());

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to target.")
        .defaultValue(EntityType.PLAYER)
        .build());

    private LivingEntity target = null;
    private Vec3d lastDir = new Vec3d(0, 0, 1);
    private int passThroughLeft = 0;
    private int cooldown = 0;
    private boolean enabledElytraFly = false;

    public SpearKill() {
        super(AddonTemplate.CATEGORY, "spear-kill",
            "Elytra spear using Meteor's ElytraFly: locks onto targets and flies through them repeatedly.");
    }

    @Override
    public void onActivate() {
        resetState();

        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFly != null && !elytraFly.isActive()) {
            elytraFly.toggle();
            enabledElytraFly = true;
        }
    }

    @Override
    public void onDeactivate() {
        releaseKeys();
        resetState();

        if (enabledElytraFly) {
            ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
            if (elytraFly != null && elytraFly.isActive()) elytraFly.toggle();
            enabledElytraFly = false;
        }
    }

    private void resetState() {
        target = null;
        passThroughLeft = 0;
        cooldown = 0;
    }

    private void releaseKeys() {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return;

        if (cooldown > 0) cooldown--;

        if (!player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            info("Equip an Elytra.");
            toggle();
            return;
        }

        if (!player.isGliding()) {
            releaseKeys();
            return;
        }

        if (passThroughLeft > 0) {
            passThroughLeft--;
            aim(player, lastDir);
            mc.options.forwardKey.setPressed(true);
            return;
        }

        if (target == null || !target.isAlive() || target.isRemoved()) {
            target = findNearestTarget(player);
            if (target == null) {
                info("No targets in range.");
                toggle();
                return;
            }
        }

        Vec3d to = target.getBoundingBox().getCenter().subtract(player.getBoundingBox().getCenter());
        double len = to.length();
        Vec3d dir = len > 1.0E-4 ? to.multiply(1.0 / len) : lastDir;
        lastDir = dir;

        aim(player, dir);
        mc.options.forwardKey.setPressed(true);
        mc.options.jumpKey.setPressed(to.y > 1.5);
        mc.options.sneakKey.setPressed(to.y < -1.5);

        if (player.squaredDistanceTo(target) <= 9.0 && cooldown == 0 && mc.interactionManager != null) {
            mc.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
            cooldown = hitDelay.get();
            passThroughLeft = passThrough.get();
        }
    }

    private LivingEntity findNearestTarget(ClientPlayerEntity player) {
        Box area = player.getBoundingBox().expand(range.get());
        List<LivingEntity> candidates = mc.world.getEntitiesByClass(
            LivingEntity.class,
            area,
            e -> e != player && e.isAlive() && entities.get().contains(e.getType())
        );
        return candidates.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)))
            .orElse(null);
    }

    private void aim(ClientPlayerEntity player, Vec3d dir) {
        double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yaw = (float) (MathHelper.atan2(-dir.x, dir.z) * 180.0 / Math.PI);
        float pitch = (float) (-(MathHelper.atan2(dir.y, horizontal) * 180.0 / Math.PI));
        player.setYaw(yaw);
        player.setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
    }
}
