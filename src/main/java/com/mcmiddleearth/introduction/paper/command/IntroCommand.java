package com.mcmiddleearth.introduction.paper.command;

import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.brigadier.NullCommandSender;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

public class IntroCommand {

    @SuppressWarnings("UnstableApiUsage")
    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName) {
        return Commands.literal(commandName)
                .then(Commands.literal("reload")
                        .requires(sender -> sender.getSender().hasPermission("introduction.reload"))
                        .executes(ctx -> {
                            IntroductionPlugin.getInstance().unloadData();
                            Bukkit.getScheduler().runTaskLater(IntroductionPlugin.getInstance(), () -> {
                                Bukkit.getServer().reloadData();
                                IntroductionPlugin.getInstance().reloadConfig();
                                IntroductionPlugin.getInstance().loadData();
                            },20);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("glowitem")
                        .requires(sender ->
                        {
//Logger.getGlobal().info(sender.getSender().getClass().getSimpleName());
//Logger.getGlobal().info("match: "+(!(sender.getSender() instanceof Player)));
                            return !(sender.getSender() instanceof Player);
                        })
/*                        .executes(context -> {
Logger.getGlobal().info("input: "+context.getInput());
                            return Command.SINGLE_SUCCESS;
                        }))*/
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.literal("off")
                                        .executes(context -> {
                                            Player player = Bukkit.getPlayer(context.getArgument("player", String.class));
                                            if(player != null) {
                                                IntroductionPlugin.getInstance().getGlowListener().setItemGlow(player, false);
//Logger.getGlobal().info("glowitem off");
                                            }
                                            return Command.SINGLE_SUCCESS;
                                        }))
                                .then(Commands.literal("on")
                                        .executes(context -> {
                                            Player player = Bukkit.getPlayer(context.getArgument("player", String.class));
                                            if(player != null) {
                                                IntroductionPlugin.getInstance().getGlowListener().setItemGlow(player, true);
//Logger.getGlobal().info("glowitem on");
                                            }
                                                //try {
                                                //KotlinBridge.info();
                                                /*Class<?> queryClass = Class.forName("com.typewritermc.core.entries.Query");
                                                for(Method method: queryClass.getDeclaredMethods()) {
                                                    Logger.getGlobal().info("Method: "+method.getName());
                                                }
                                                Field companionField = queryClass.getField("Companion");
                                                Object companion = companionField.get(null);
                                                Method findMethod = companion.getClass().getDeclaredMethod("find");
                                                Object entries = findMethod.invoke(companion);
                                                Logger.getGlobal().info("Entries: "+entries.getClass().getName());
                                                for(Method entryMethod: entries.getClass().getDeclaredMethods()) {
                                                    Logger.getGlobal().info("Entry Method: "+entryMethod.getName());
                                                }*/
                                            /*} catch (Exception e) {
                                                throw new RuntimeException(e);
                                            }*/
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .build();
    }
}
