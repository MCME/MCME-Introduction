package com.mcmiddleearth.introduction.command;

import com.mcmiddleearth.introduction.IntroductionPlugin;
import com.mcmiddleearth.introduction.rooms.SecondRoom;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

public class IntroCommand {

    @SuppressWarnings("UnstableApiUsage")
    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName) {
        return Commands.literal(commandName)
                .then(Commands.literal("reload")
                        .requires(sender -> sender.getSender().hasPermission("introduction.reload"))
                        .executes(ctx -> {
                            IntroductionPlugin.getInstance().unloadData();
                            IntroductionPlugin.getInstance().reloadConfig();
                            IntroductionPlugin.getInstance().loadData();
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
