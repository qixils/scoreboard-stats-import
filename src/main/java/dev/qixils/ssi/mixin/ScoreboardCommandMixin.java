package dev.qixils.ssi.mixin;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.server.command.ScoreboardCommand;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static dev.qixils.ssi.ScoreboardStatsImport.INSTANCE;

@Mixin(ScoreboardCommand.class)
public class ScoreboardCommandMixin {

    // We could mixin directly into the Scoreboard class,
    // but I don't think there's any value in re-triggering this every server startup

    @Redirect(method = "executeAddObjective", at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/Scoreboard;addObjective(Ljava/lang/String;Lnet/minecraft/scoreboard/ScoreboardCriterion;Lnet/minecraft/text/Text;Lnet/minecraft/scoreboard/ScoreboardCriterion$RenderType;ZLnet/minecraft/scoreboard/number/NumberFormat;)Lnet/minecraft/scoreboard/ScoreboardObjective;"))
    private static ScoreboardObjective onCreateObjective(
            Scoreboard scoreboard,
            String name,
            ScoreboardCriterion criterion,
            Text displayName,
            ScoreboardCriterion.RenderType renderType,
            boolean displayAutoUpdate,
            @Nullable NumberFormat numberFormat
    ) {
        ScoreboardObjective objective = scoreboard.addObjective(name, criterion, displayName, renderType, displayAutoUpdate, numberFormat);
        if (INSTANCE == null) return objective;

        INSTANCE.importStats(scoreboard, objective, criterion);
        return objective;
    }
}