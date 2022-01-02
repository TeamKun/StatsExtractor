package net.kunmc.lab.statsextractor;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class StatsExtractor extends JavaPlugin {

    private Logger LOGGER;

    @Override
    public void onEnable() {
        // Plugin startup logic
        LOGGER = getLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if ("stats-players".equals(command.getName())) {

            // 時間がかかるので別スレッド
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        // フォルダを作成
                        getDataFolder().mkdirs();
                        // 書き出し先ファイル
                        File path = new File(getDataFolder(), "stats-export.csv");
                        FileWriter out = new FileWriter(path);
                        // CSVのヘッダーを設定
                        CSVFormat format = CSVFormat.Builder.create()
                                .setHeader(Stream.concat(
                                        Stream.of("UUID", "Name", "Team"),
                                        Arrays.stream(Statistic.values()).map(Statistic::name)
                                ).toArray(String[]::new))
                                .build();
                        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
                        // 鯖に入ったことあるすべてのプレイヤーの統計を取得
                        OfflinePlayer[] offlinePlayers = getServer().getOfflinePlayers();
                        int max = offlinePlayers.length;
                        int now = 0;
                        // CSV書き込み開始
                        try (CSVPrinter printer = new CSVPrinter(out, format)) {
                            for (OfflinePlayer player : offlinePlayers) {
                                // 進捗を表示 (処理に時間がかかるため)
                                sender.sendMessage(String.format("書き出し中(%d/%d): %s", ++now, max, player.getName()));
                                // CSV一行書き出し
                                printer.printRecord(Stream.concat(
                                        // UUID, プレイヤー名, チーム
                                        Stream.of(
                                                player.getUniqueId(),
                                                player.getName(),
                                                Optional.ofNullable(player.getName()).map(sb::getEntryTeam).map(Team::getName).orElse("")
                                        ),
                                        // 統計情報
                                        Arrays.stream(Statistic.values()).map(stat -> {
                                            switch (stat.getType()) {
                                                // アイテムやブロックごとの統計がある項目はすべて合算して取得する
                                                case ITEM:
                                                    return Arrays.stream(Material.values())
                                                            .filter(Material::isItem)
                                                            .mapToInt(item -> player.getStatistic(stat, item))
                                                            .sum();
                                                case BLOCK:
                                                    return Arrays.stream(Material.values())
                                                            .filter(Material::isBlock)
                                                            .mapToInt(block -> player.getStatistic(stat, block))
                                                            .sum();
                                                case ENTITY:
                                                    return Arrays.stream(EntityType.values())
                                                            .mapToInt(entity -> {
                                                                try {
                                                                    return player.getStatistic(stat, entity);
                                                                } catch (IllegalArgumentException e) {
                                                                    return 0;
                                                                }
                                                            })
                                                            .sum();
                                                // 通常の統計取得
                                                default:
                                                case UNTYPED:
                                                    return player.getStatistic(stat);
                                            }
                                        })
                                ).toArray(Object[]::new));
                            }
                        }
                        // どこに保存されたか出力
                        sender.sendMessage("書き出しに成功しました: " + path.getAbsolutePath());
                    } catch (IOException e) {
                        // エラー通知
                        sender.sendMessage("書き出しに失敗しました。");
                        // エラーログ
                        LOGGER.log(Level.SEVERE, "書き出しに失敗しました。", e);
                    }
                }
            }.runTaskLaterAsynchronously(this, 0);

            sender.sendMessage("書き出し開始。");

        }

        return true;
    }

}
