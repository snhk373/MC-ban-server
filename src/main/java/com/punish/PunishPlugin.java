package com.punish;

import org.bukkit.*;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PunishPlugin extends JavaPlugin implements CommandExecutor, Listener {
    private File dataFile;
    private FileConfiguration dataConfig;

    private final Set<Player> vanishPlayers = new HashSet<>();
    private int stopTaskId = -1;

    private static final String JAIL_WORLD_NAME = "jail";
    private final Map<String, Location> jailOriginLoc = new HashMap<>();

    private final Map<String, List<String>> pvpTeams = new HashMap<>();
    private final Map<String, String> teamInvites = new HashMap<>();

    public static class PunishData {
        String type;
        String player;
        long expire;
        String reason;

        public PunishData(String type, String player, long expire, String reason) {
            this.type = type;
            this.player = player;
            this.expire = expire;
            this.reason = reason;
        }
    }

    private final List<PunishData> punishList = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initDataFile();
        loadPunishData();

        // 预加载小黑屋世界
        loadCustomWorld(JAIL_WORLD_NAME);

        regCmd("banish");
        regCmd("unbanish");
        regCmd("mute");
        regCmd("unmute");
        regCmd("jail");
        regCmd("unjail");
        regCmd("setjail");
        regCmd("clearinv");

        regCmd("msgadmin");
        regCmd("warn");
        regCmd("broadcast");
        regCmd("vanish");
        regCmd("newgift");
        regCmd("server");
        regCmd("admintools");
        regCmd("resetchunk");

        regCmd("ptp");
        regCmd("pzd");
        regCmd("pjb");

        getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                checkExpireTask();
            }
        }.runTaskTimer(this, 20 * 20, 20 * 20);

        getLogger().info("【综合系统】加载完成！处罚+PVP+管理工具全部就绪");
    }

    @Override
    public void onDisable() {
        savePunishData();
        if (stopTaskId != -1) Bukkit.getScheduler().cancelTask(stopTaskId);
        getLogger().info("【综合系统】数据已保存，插件卸载");
    }

    private void regCmd(String name) {
        if (getCommand(name) != null) getCommand(name).setExecutor(this);
    }

    /**
     * 加载服务端根目录下的世界
     * @param worldName 世界文件夹名（必须放在服务端根目录，与world同级）
     * @return 加载成功返回世界对象，失败返回null
     */
    private World loadCustomWorld(String worldName) {
        // 已加载直接返回
        World exist = getServer().getWorld(worldName);
        if (exist != null) return exist;

        // 检测根目录是否存在该世界文件夹
        File worldFolder = new File(worldName);
        if (!worldFolder.exists() || !worldFolder.isDirectory()) {
            getLogger().warning("世界文件夹不存在：" + worldName + "（请放到服务端根目录）");
            return null;
        }

        try {
            WorldCreator wc = new WorldCreator(worldName);
            wc.environment(World.Environment.NORMAL);
            wc.generateStructures(false);
            return getServer().createWorld(wc);
        } catch (Exception e) {
            getLogger().severe("加载世界失败：" + worldName);
            getLogger().severe("请确认世界存档完整，包含 level.dat、region 等文件");
            return null;
        }
    }

    private void initDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadPunishData() {
        punishList.clear();
        if (dataConfig.getConfigurationSection("punish") == null) return;
        for (String key : dataConfig.getConfigurationSection("punish").getKeys(false)) {
            String type = dataConfig.getString("punish." + key + ".type");
            String player = dataConfig.getString("punish." + key + ".player");
            long expire = dataConfig.getLong("punish." + key + ".expire");
            String reason = dataConfig.getString("punish." + key + ".reason");
            punishList.add(new PunishData(type, player, expire, reason));
        }
    }

    public void savePunishData() {
        dataConfig.set("punish", null);
        int i = 0;
        for (PunishData d : punishList) {
            String path = "punish." + i;
            dataConfig.set(path + ".type", d.type);
            dataConfig.set(path + ".player", d.player);
            dataConfig.set(path + ".expire", d.expire);
            dataConfig.set(path + ".reason", d.reason);
            i++;
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setJailSpawn(Player p) {
        World jailWorld = getServer().getWorld(JAIL_WORLD_NAME);
        if (jailWorld == null) {
            p.sendMessage("§c错误：小黑屋世界未加载，请将 jail 世界放到服务端根目录");
            return;
        }
        Location loc = p.getLocation();
        jailWorld.setSpawnLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        p.sendMessage("§a小黑屋出生点已设置");
    }

    private void checkExpireTask() {
        long now = System.currentTimeMillis();
        Iterator<PunishData> it = punishList.iterator();
        while (it.hasNext()) {
            PunishData pd = it.next();
            if (pd.expire > 0 && now > pd.expire) {
                it.remove();
                switch (pd.type) {
                    case "mute":
                        Player muteTarget = getServer().getPlayerExact(pd.player);
                        if (muteTarget != null) muteTarget.sendMessage("§a你的禁言时效已结束！");
                        break;
                    case "jail":
                        releasePlayerFromJail(pd.player);
                        break;
                    case "ban":
                        getServer().getBanList(BanList.Type.NAME).pardon(pd.player);
                        getServer().broadcastMessage("§e玩家 " + pd.player + " 的封禁已自动解除");
                        break;
                }
            }
        }
        savePunishData();
    }

    private void releasePlayerFromJail(String playerName) {
        Player target = getServer().getPlayerExact(playerName);
        if (target == null) return;

        Location origin = jailOriginLoc.remove(playerName);
        if (origin != null) {
            target.teleport(origin);
        } else {
            World mainWorld = getServer().getWorlds().get(0);
            if (mainWorld != null) {
                target.teleport(mainWorld.getSpawnLocation());
            }
        }
        target.sendMessage("§a关押时间结束，已释放！");
    }

    private Optional<PunishData> getPlayerPunish(String name, String type) {
        return punishList.stream()
                .filter(d -> d.player.equalsIgnoreCase(name) && d.type.equals(type))
                .findFirst();
    }

    private void addPunish(String type, String player, long seconds, String reason) {
        long expire = seconds <= 0 ? 0 : System.currentTimeMillis() + seconds * 1000;
        punishList.removeIf(d -> d.player.equalsIgnoreCase(player) && d.type.equals(type));
        punishList.add(new PunishData(type, player, expire, reason));
        savePunishData();
    }

    private boolean removePunish(String player, String type) {
        Optional<PunishData> op = getPlayerPunish(player, type);
        if (op.isPresent()) {
            punishList.remove(op.get());
            savePunishData();
            return true;
        }
        return false;
    }

    private String msg(String path) {
        String raw = getConfig().getString(path);
        if (raw == null) return "";
        return raw.replace("&", "§");
    }

    private String parseColor(String text) {
        return text
                .replace("red", "§c")
                .replace("blue", "§9")
                .replace("yellow", "§e");
    }

    private void sendAdminMsg(String text, Player sender) {
        String prefix = sender != null
                ? "§6[" + sender.getName() + "] §r"
                : "§6[管理员] §r";
        String content = prefix + parseColor(text);
        for (Player p : getServer().getOnlinePlayers()) {
            if (p.hasPermission("punishsystem.admin.msg")) {
                p.sendMessage(content);
            }
        }
    }

    private void sendWarn(Player target, String text) {
        String content = msg("warn-prefix") + parseColor(text);
        target.sendMessage(content);
    }

    private void sendBroadcast(String text) {
        String content = msg("broadcast-prefix") + parseColor(text);
        getServer().broadcastMessage(content);
    }

    private void toggleVanish(Player player) {
        if (vanishPlayers.contains(player)) {
            vanishPlayers.remove(player);
            for (Player online : getServer().getOnlinePlayers()) {
                online.showPlayer(this, player);
            }
            player.sendMessage("§a隐身模式已关闭");
        } else {
            vanishPlayers.add(player);
            for (Player online : getServer().getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.hidePlayer(this, player);
                }
            }
            player.sendMessage("§a隐身模式已开启");
        }
    }

    private void giveNewGift(Player target) {
        ItemStack helm = new ItemStack(Material.LEATHER_HELMET);
        helm.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 2);
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        chest.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 2);
        ItemStack leg = new ItemStack(Material.LEATHER_LEGGINGS);
        leg.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 2);
        ItemStack boot = new ItemStack(Material.LEATHER_BOOTS);
        boot.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 2);

        target.getInventory().addItem(helm, chest, leg, boot);
        target.getInventory().addItem(new ItemStack(Material.LOG, 64));
        target.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
        target.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE));
        target.getInventory().addItem(new ItemStack(Material.STONE_AXE));
        target.getInventory().addItem(new ItemStack(Material.STONE_SPADE));
        target.getInventory().addItem(new ItemStack(Material.WHEAT, 64));
        target.getInventory().addItem(new ItemStack(Material.BREAD, 64));

        target.sendMessage("§a你获得了新手大礼包！");
    }

    private void startStopCountdown(long second) {
        if (stopTaskId != -1) {
            Bukkit.getScheduler().cancelTask(stopTaskId);
        }
        new BukkitRunnable() {
            long remain = second;
            @Override
            public void run() {
                if (remain <= 0) {
                    getServer().shutdown();
                    this.cancel();
                    stopTaskId = -1;
                    return;
                }
                String tip = msg("stop-broadcast-format").replace("%time%", remain + "");
                if (!tip.isEmpty()) sendBroadcast(tip);
                remain--;
            }
        }.runTaskTimer(this, 0, 20);
    }

    private void giveAdminTools(Player p) {
        ItemStack banSword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta banMeta = banSword.getItemMeta();
        banMeta.setDisplayName("§4封禁剑");
        banMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        banSword.setItemMeta(banMeta);

        ItemStack muteSword = new ItemStack(Material.GOLD_SWORD);
        ItemMeta muteMeta = muteSword.getItemMeta();
        muteMeta.setDisplayName("§c禁言剑");
        muteSword.setItemMeta(muteMeta);

        ItemStack vanishPotion = new ItemStack(Material.POTION);
        ItemMeta potionMeta = vanishPotion.getItemMeta();
        potionMeta.setDisplayName("§b管理员隐身药水");
        vanishPotion.setItemMeta(potionMeta);

        ItemStack godChest = new ItemStack(Material.DIAMOND_CHESTPLATE);
        godChest.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 255);
        ItemMeta godMeta = godChest.getItemMeta();
        godMeta.setDisplayName("§6管理员无敌胸甲");
        godChest.setItemMeta(godMeta);

        p.getInventory().addItem(banSword, muteSword, vanishPotion, godChest);
        p.sendMessage("§a管理员工具已发放");
    }

    private int clearDrops() {
        int cnt = 0;
        for (World w : getServer().getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getType() == EntityType.DROPPED_ITEM) {
                    e.remove();
                    cnt++;
                }
            }
        }
        return cnt;
    }

    // ========== 全局异常捕获 指令总入口 ==========
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        try {
            return handleCommand(sender, cmd, args);
        } catch (Exception e) {
            sender.sendMessage("§c指令执行出错，请查看控制台日志");
            getLogger().severe("指令 /" + cmd.getName() + " 执行异常");
            e.printStackTrace();
            return true;
        }
    }

    private boolean handleCommand(CommandSender sender, Command cmd, String[] args) {
        if (cmd.getPermission() != null && !sender.hasPermission(cmd.getPermission())) {
            String tip = msg("no-permission");
            sender.sendMessage(tip.isEmpty() ? "§c无权限" : tip);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("setjail")) {
            if (!(sender instanceof Player)) return true;
            setJailSpawn((Player) sender);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("clearinv")) {
            if (args.length < 1) { sender.sendMessage("§c用法：/clearinv <玩家>"); return true; }
            Player target = getServer().getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage("§c找不到玩家"); return true; }
            target.getInventory().clear();
            sender.sendMessage("§a已清空背包：" + target.getName());
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("mute")) {
            if (args.length < 2) { sender.sendMessage("§c用法：/mute <玩家> <秒数> [原因]"); return true; }
            String pName = args[0];
            long sec;
            try { sec = Long.parseLong(args[1]); } catch (Exception e) { sender.sendMessage("§c秒数必须数字"); return true; }
            String reason = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "违规";
            addPunish("mute", pName, sec, reason);
            sender.sendMessage("§a已禁言 " + pName + "，时长 " + sec + " 秒");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("unmute")) {
            if (args.length < 1) { sender.sendMessage("§c用法：/unmute <玩家>"); return true; }
            String pName = args[0];
            boolean ok = removePunish(pName, "mute");
            sender.sendMessage(ok ? "§a已解除禁言" : "§c无禁言记录");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("banish")) {
            if (args.length < 2) { sender.sendMessage("§c用法：/banish <玩家> <秒数> [原因]"); return true; }
            String pName = args[0];
            long sec;
            try { sec = Long.parseLong(args[1]); } catch (Exception e) { sender.sendMessage("§c秒数必须数字"); return true; }
            String reason = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "违规";
            addPunish("ban", pName, sec, reason);

            BanList banList = getServer().getBanList(BanList.Type.NAME);
            Date expireDate = sec <= 0 ? null : new Date(System.currentTimeMillis() + sec * 1000);
            banList.addBan(pName, reason, expireDate, sender.getName());

            sender.sendMessage("§a已封禁 " + pName);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("unbanish")) {
            if (args.length < 1) { sender.sendMessage("§c用法：/unbanish <玩家>"); return true; }
            String pName = args[0];
            removePunish(pName, "ban");
            getServer().getBanList(BanList.Type.NAME).pardon(pName);
            sender.sendMessage("§a已解除封禁");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("jail")) {
            World jailWorld = getServer().getWorld(JAIL_WORLD_NAME);
            if (jailWorld == null) {
                sender.sendMessage("§c小黑屋世界未加载，请将 jail 世界放到服务端根目录");
                return true;
            }
            if (args.length < 2) { sender.sendMessage("§c用法：/jail <玩家> <秒数> [原因]"); return true; }
            String pName = args[0];
            long sec;
            try { sec = Long.parseLong(args[1]); } catch (Exception e) { sender.sendMessage("§c秒数必须数字"); return true; }
            String reason = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "违规";
            addPunish("jail", pName, sec, reason);

            Player target = getServer().getPlayerExact(pName);
            if (target != null) {
                jailOriginLoc.put(pName, target.getLocation());
                target.teleport(jailWorld.getSpawnLocation());
                target.sendMessage("§c你被关押进小黑屋，时长 " + sec + " 秒");
            }
            sender.sendMessage("§a已关押玩家：" + pName);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("unjail")) {
            if (args.length < 1) { sender.sendMessage("§c用法：/unjail <玩家>"); return true; }
            String pName = args[0];
            boolean ok = removePunish(pName, "jail");
            if (ok) {
                releasePlayerFromJail(pName);
                sender.sendMessage("§a已释放玩家：" + pName);
            } else {
                sender.sendMessage("§c无关押记录");
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("msgadmin")) {
            if (args.length < 1) { sender.sendMessage("§c用法：/msgadmin <消息>"); return true; }
            String text = String.join(" ", args);
            sendAdminMsg(text, sender instanceof Player ? (Player) sender : null);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("warn")) {
            if (args.length < 2) { sender.sendMessage("§c用法：/warn <玩家> <消息>"); return true; }
            Player target = getServer().getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage("§c找不到玩家"); return true; }
            String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            sendWarn(target, text);
            sender.sendMessage("§a警告已发送");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("broadcast")) {
            if (args.length < 1) { sender.sendMessage("§c用法：/broadcast <内容>"); return true; }
            sendBroadcast(String.join(" ", args));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("vanish")) {
            if (!(sender instanceof Player)) return true;
            toggleVanish((Player) sender);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("newgift")) {
            Player target;
            if (args.length >= 1) {
                target = getServer().getPlayerExact(args[0]);
                if (target == null) { sender.sendMessage("§c找不到玩家"); return true; }
            } else {
                if (!(sender instanceof Player)) return true;
                target = (Player) sender;
            }
            giveNewGift(target);
            sender.sendMessage("§a已发放新手礼包给 " + target.getName());
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("server")) {
            if (args.length < 2 || !args[0].equalsIgnoreCase("stop")) {
                sender.sendMessage("§c用法：/server stop <秒数>");
                return true;
            }
            long sec;
            try { sec = Long.parseLong(args[1]); } catch (Exception e) { sender.sendMessage("§c秒数必须数字"); return true; }
            startStopCountdown(sec);
            sender.sendMessage("§a已启动延时关服");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("admintools")) {
            if (!(sender instanceof Player)) return true;
            giveAdminTools((Player) sender);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("resetchunk")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            Chunk chunk = p.getLocation().getChunk();
            chunk.unload(false);
            p.getWorld().regenerateChunk(chunk.getX(), chunk.getZ());
            p.sendMessage("§a区块已重置");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("ptp")) {
            if (args.length < 2) { sender.sendMessage("§c用法：/ptp <玩家> <地图名>"); return true; }
            Player target = getServer().getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage("§c玩家不在线"); return true; }
            String mapName = args[1];
            World world = loadCustomWorld(mapName);
            if (world == null) {
                sender.sendMessage("§cPVP地图不存在：" + mapName + "（请放到服务端根目录）");
                return true;
            }
            target.teleport(world.getSpawnLocation());
            target.sendMessage("§a你被传送到PVP地图：" + mapName);
            sender.sendMessage("§a已传送 " + target.getName() + " 到 " + mapName);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("pzd")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (args.length < 1) {
                p.sendMessage("§c用法：/pzd <invite|accept|leave|disband|list> [玩家]");
                return true;
            }
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "invite":
                    if (args.length < 2) { p.sendMessage("§c用法：/pzd invite <玩家>"); return true; }
                    Player target = getServer().getPlayerExact(args[1]);
                    if (target == null) { p.sendMessage("§c玩家不在线"); return true; }
                    if (target.getName().equals(p.getName())) { p.sendMessage("§c不能邀请自己"); return true; }
                    if (pvpTeams.containsKey(p.getName())) { p.sendMessage("§c你已是队长，不能再邀请"); return true; }
                    for (List<String> m : pvpTeams.values()) {
                        if (m.contains(p.getName())) { p.sendMessage("§c你已在队伍中"); return true; }
                    }
                    for (List<String> m : pvpTeams.values()) {
                        if (m.contains(target.getName())) { p.sendMessage("§c对方已有队伍"); return true; }
                    }
                    teamInvites.put(target.getName(), p.getName());
                    p.sendMessage("§a已发送组队邀请给 " + target.getName());
                    target.sendMessage("§e" + p.getName() + " 邀请你组队，输入 /pzd accept 接受");
                    break;

                case "accept":
                    if (!teamInvites.containsKey(p.getName())) { p.sendMessage("§c没有待处理邀请"); return true; }
                    String leader = teamInvites.remove(p.getName());
                    List<String> members = pvpTeams.getOrDefault(leader, new ArrayList<>());
                    members.add(p.getName());
                    pvpTeams.put(leader, members);
                    p.sendMessage("§a你加入了 " + leader + " 的队伍");
                    Player leaderP = getServer().getPlayerExact(leader);
                    if (leaderP != null) leaderP.sendMessage("§a" + p.getName() + " 加入了你的队伍");
                    break;

                case "leave":
                    String myLeader = null;
                    for (Map.Entry<String, List<String>> entry : pvpTeams.entrySet()) {
                        if (entry.getValue().contains(p.getName())) {
                            myLeader = entry.getKey();
                            break;
                        }
                    }
                    if (myLeader == null && !pvpTeams.containsKey(p.getName())) {
                        p.sendMessage("§c你不在任何队伍");
                        return true;
                    }
                    if (pvpTeams.containsKey(p.getName())) {
                        List<String> ms = pvpTeams.remove(p.getName());
                        p.sendMessage("§a你已解散队伍");
                        for (String m : ms) {
                            Player mp = getServer().getPlayerExact(m);
                            if (mp != null) mp.sendMessage("§c队伍已被队长解散");
                        }
                    } else {
                        pvpTeams.get(myLeader).remove(p.getName());
                        p.sendMessage("§a你已离开队伍");
                        Player lp = getServer().getPlayerExact(myLeader);
                        if (lp != null) lp.sendMessage("§c" + p.getName() + " 离开了队伍");
                    }
                    break;

                case "disband":
                    if (!pvpTeams.containsKey(p.getName())) { p.sendMessage("§c你不是队长"); return true; }
                    List<String> ms = pvpTeams.remove(p.getName());
                    p.sendMessage("§a队伍已解散");
                    for (String m : ms) {
                        Player mp = getServer().getPlayerExact(m);
                        if (mp != null) mp.sendMessage("§c队伍已被解散");
                    }
                    break;

                case "list":
                    String listLeader = null;
                    if (pvpTeams.containsKey(p.getName())) {
                        listLeader = p.getName();
                    } else {
                        for (Map.Entry<String, List<String>> entry : pvpTeams.entrySet()) {
                            if (entry.getValue().contains(p.getName())) {
                                listLeader = entry.getKey();
                                break;
                            }
                        }
                    }
                    if (listLeader == null) { p.sendMessage("§c你不在队伍中"); return true; }
                    p.sendMessage("§e队长：" + listLeader);
                    p.sendMessage("§e队员：" + String.join(", ", pvpTeams.get(listLeader)));
                    break;

                default:
                    p.sendMessage("§c未知子命令");
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("pjb")) {
            if (!(sender instanceof Player)) return true;
            if (args.length < 2) { sender.sendMessage("§c用法：/pjb <玩家> <原因>"); return true; }
            Player reporter = (Player) sender;
            String targetName = args[0];
            String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

            int id = dataConfig.getInt("report-next-id", 1);
            String path = "reports." + id;
            dataConfig.set(path + ".reporter", reporter.getName());
            dataConfig.set(path + ".target", targetName);
            dataConfig.set(path + ".reason", reason);
            dataConfig.set(path + ".time", System.currentTimeMillis());
            dataConfig.set("report-next-id", id + 1);
            try {
                dataConfig.save(dataFile);
            } catch (IOException e) {
                e.printStackTrace();
            }

            reporter.sendMessage("§a举报已提交，管理员将尽快处理");
            for (Player admin : getServer().getOnlinePlayers()) {
                if (admin.hasPermission("punishsystem.admin.report")) {
                    admin.sendMessage("§c[举报] " + reporter.getName() + " 举报 " + targetName + "，原因：" + reason);
                }
            }
            return true;
        }

        return false;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        Optional<PunishData> op = getPlayerPunish(p.getName(), "mute");
        if (op.isPresent()) {
            e.setCancelled(true);
            p.sendMessage("§c你处于禁言状态！原因：" + op.get().reason);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player join = e.getPlayer();
        for (Player v : vanishPlayers) {
            join.hidePlayer(this, v);
        }
    }
}
