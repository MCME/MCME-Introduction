package com.mcmiddleearth.introduction.paper.command;

import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
import com.mcmiddleearth.introduction.paper.confirmData.ConfirmDataManager;
import com.mcmiddleearth.introduction.paper.rooms.SecondRoom;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

public class ConfirmCommand {

    @SuppressWarnings("UnstableApiUsage")
    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName) {
        return Commands.literal(commandName)
                .then(Commands.literal("optifine")
                        .requires(sender -> {
                            SecondRoom second = (SecondRoom) IntroductionPlugin.getInstance().getSecond();
                            return sender.getSender() instanceof Player player
                                    && second.isForge(player)
                                    && second.isSupportedVersion(player)
                                    /*&& (second.isInside(player) || second.isFixed(player))*/;
                        })
                        .executes(ctx -> {
                            Player player =  (Player) ctx.getSource().getSender();
                            SecondRoom second = (SecondRoom) IntroductionPlugin.getInstance().getSecond();
                            if(second.isInside(player.getLocation())) {
                                second.teleport(player, second, null);
                            } else {
                                second.handleOverride(player, false);
                            }
                            IntroductionPlugin.getInstance().getConfirmDataManager().confirmOptifine(player.getUniqueId());
                            IntroductionPlugin.sendInfoMessage(player, "You will no longer be prompted about required Optifine settings.");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("ignore")
                        .requires(sender -> {
                            SecondRoom second = (SecondRoom) IntroductionPlugin.getInstance().getSecond();
                            return sender.getSender() instanceof Player player
                                    && (!second.isForge(player) || !second.isSupportedVersion(player))
                                    /*&& (second.isInside(player) || second.isFixed(player))*/;
                        })
                        .executes(ctx -> {
                            Player player =  (Player) ctx.getSource().getSender();
                            SecondRoom second = (SecondRoom) IntroductionPlugin.getInstance().getSecond();
                            if(second.isInside(player.getLocation())) {
                                second.teleport(player, second, null);
                            } else {
                                second.handleOverride(player, false);
                            }
                            IntroductionPlugin.getInstance().getConfirmDataManager().confirmIgnore(player.getUniqueId());
                            IntroductionPlugin.sendInfoMessage(player, "You will no longer be prompted about compatibility issues.");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("reset")
                        .executes(ctx -> {
                            Player player =  (Player) ctx.getSource().getSender();
                            ConfirmDataManager mgr = IntroductionPlugin.getInstance().getConfirmDataManager();
                            mgr.resetConfirmations(player.getUniqueId().toString());
                            IntroductionPlugin.sendInfoMessage(player,"Your confirmation state has been reset.");
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
