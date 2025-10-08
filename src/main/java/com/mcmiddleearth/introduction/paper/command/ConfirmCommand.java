package com.mcmiddleearth.introduction.paper.command;

import com.mcmiddleearth.introduction.paper.IntroductionPlugin;
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
                                    && (second.isInside(player) || second.isFixed(player));
                        })
                        .executes(ctx -> {
                            Player player =  (Player) ctx.getSource().getSender();
                            SecondRoom second = (SecondRoom) IntroductionPlugin.getInstance().getSecond();
                            if(second.isInside(player)) {
                                second.teleport(player, second, null);
                            } else {
                                second.handleOverride(player, false);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("ignore")
                        .requires(sender -> {
                            SecondRoom second = (SecondRoom) IntroductionPlugin.getInstance().getSecond();
                            return sender.getSender() instanceof Player player
                                    && (!second.isForge(player) || !second.isSupportedVersion(player))
                                    && (second.isInside(player) || second.isFixed(player));
                        })
                        .executes(ctx -> {
                            Player player =  (Player) ctx.getSource().getSender();
                            SecondRoom second = (SecondRoom) IntroductionPlugin.getInstance().getSecond();
                            if(second.isInside(player)) {
                                second.teleport(player, second, null);
                            } else {
                                second.handleOverride(player, false);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
