// src/main/java/com/example/lodestonewarps/config/ModConfig.java
package com.example.lodestonewarps.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    public int max_distance = 5000;
    public int min_distance = 16;
    public boolean same_dimension_only = true;
    public boolean creation_requires_sneak = true;
    public long creation_timeout_seconds = 3600;
    public boolean consume_recovery_compass = true;

    public int warmup_seconds = 5;
    public int warmup_movement_check_ticks = 5;
    public int warmup_particle_ticks = 5;
    public int warmup_actionbar_ticks = 20;
    public double warmup_position_tolerance = 0.55;
    public double warmup_y_tolerance = 0.25;
    public int warmup_cancel_retry_seconds = 3;

    public int cooldown_seconds_per_500 = 60;
    public int cooldown_min_seconds = 10;
    public int cooldown_max_seconds = 600;

    public int weakness_seconds_per_1000 = 180;
    public int weakness_min_seconds = 15;
    public int weakness_max_seconds = 900;
    public int weakness_amplifier = 1;
    public int weakness_reapply_ticks = 5;
    public boolean weakness_protected_from_milk = true;

    // Дополнительные негативные эффекты по расстоянию: до slowness_from_distance — только слабость,
    // от slowness_from_distance — ещё и замедление, от mining_fatigue_from_distance — ещё и усталость.
    public int slowness_from_distance = 500;
    public int slowness_amplifier = 1;
    public int mining_fatigue_from_distance = 1500;
    public int mining_fatigue_amplifier = 1;

    /**
     * Сила эффекта зависит от ступени штрафа (0 — близко, 1 — средне, 2 — далеко):
     * каждый следующий эффект начинается с I уровня, а предыдущие на дальней дистанции
     * поднимаются до значения из конфига (по умолчанию II).
     */
    public int weaknessAmplifierFor(int tier) {
        return tier >= 1 ? weakness_amplifier : 0;
    }

    public int slownessAmplifierFor(int tier) {
        return tier >= 2 ? slowness_amplifier : 0;
    }

    public int miningFatigueAmplifierFor(int tier) {
        return mining_fatigue_amplifier;
    }

    public int blindness_seconds = 5;
    public boolean blindness_removable_by_milk = true;
    public boolean blindness_persist_after_death = true;
    public boolean blindness_persist_after_logout = true;

    public String hp_penalty_mode = "FULL_LEAVES_ONE_OTHERWISE_KILL";
    public boolean ignore_armor = true;
    public boolean ignore_protection = true;
    public boolean ignore_resistance = true;
    public boolean check_absorption = false;
    public boolean strip_absorption = false;
    public boolean allow_totem_save = true;
    public boolean apply_hp_penalty_after_teleport = false;

    public boolean allow_creative = true;
    public boolean allow_spectator = true;
    public boolean creative_bypass_penalty = true;
    public boolean spectator_bypass_penalty = true;
    public boolean creative_bypass_cooldown = true;
    public boolean spectator_bypass_cooldown = true;
    public boolean creative_bypass_cost = false;

    public int exit_clear_base_height = 2;
    public boolean exit_clear_drop_blocks = true;
    public boolean exit_clear_for_player_size = true;
    public int exit_clear_max_width = 3;
    public int exit_clear_max_height = 4;
    public List<String> exit_clear_blacklist = new ArrayList<>(List.of(
            "minecraft:bedrock",
            "minecraft:end_portal_frame",
            "minecraft:end_portal",
            "minecraft:nether_portal",
            "minecraft:end_gateway",
            "minecraft:command_block",
            "minecraft:chain_command_block",
            "minecraft:repeating_command_block",
            "minecraft:structure_block",
            "minecraft:jigsaw",
            "minecraft:structure_void",
            "minecraft:barrier",
            "minecraft:light",
            "minecraft:moving_piston"
    ));
    public boolean exit_clear_blacklist_unbreakable = true;
    public boolean blocked_exit_cancels_teleport = true;
    public boolean blocked_exit_causes_explosion = false;

    public boolean exit_chunk_preload = true;
    public int exit_chunk_ticket_seconds = 15;
    public int max_active_chunk_tickets = 32;

    public boolean missing_exit_explosion = true;
    public float explosion_power = 2.0f;
    public boolean explosion_fire = false;
    public boolean explosion_block_damage = true;
    public boolean explosion_entity_damage = true;
    public double explosion_warning_seconds = 1.0;
    public boolean explosion_removes_entrance = true;
    public boolean entrance_drop_on_break = false;
    public boolean entrance_drop_on_explosion = false;
    public boolean explosion_preserve_drops = true;
    public int max_active_explosion_animations = 16;

    public boolean cancel_animation_on_failed_teleport = true;
    public boolean cancel_animation_on_warmup_interrupt = true;
    public String cancel_animation_sound = "minecraft:block.fire.extinguish";

    public String player_storage_key = "CASE_SENSITIVE_NAME";
    public int cleanup_expired_player_data_minutes = 5;
    public boolean debug_logging = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ModConfig load(File file) {
        if (!file.exists()) {
            ModConfig config = new ModConfig();
            config.save(file);
            return config;
        }
        try (FileReader reader = new FileReader(file)) {
            ModConfig config = GSON.fromJson(reader, ModConfig.class);
            return config != null ? config : new ModConfig();
        } catch (IOException e) {
            e.printStackTrace();
            return new ModConfig();
        }
    }

    public void save(File file) {
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}