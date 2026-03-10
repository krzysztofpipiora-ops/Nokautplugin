public class DownedPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Long> downedPlayers = new HashMap<>();
    private final Map<UUID, BossBar> reviveBars = new HashMap<>();
    private final Map<UUID, BukkitTask> deathTasks = new HashMap<>();
    private final Map<UUID, Long> lastShiftClick = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        if (player.getHealth() - event.getFinalDamage() <= 0) {
            if (hasTotem(player)) return;

            if (!downedPlayers.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
                enterDownedState(player);
            }
        }
    }

    private void enterDownedState(Player player) {
        UUID uuid = player.getUniqueId();
        player.setHealth(2.0); // Zostawiamy 1 serce
        player.setSwimming(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 1200, 1));
        
        downedPlayers.put(uuid, System.currentTimeMillis() + 60000);

        // Zadanie: Śmierć po 60 sekundach
        BukkitTask deathTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (downedPlayers.containsKey(uuid)) {
                    downedPlayers.remove(uuid);
                    player.setHealth(0); // Definitywna śmierć
                    player.sendMessage("§cWykrwawiłeś się...");
                }
            }
        }.runTaskLater(this, 1200L); // 60 sekund * 20 ticków
        
        deathTasks.put(uuid, deathTask);
        player.sendMessage("§cZostałeś powalony! Masz 60s na ratunek.");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Blokada ruchu dla powalonych (mogą się tylko lekko obracać)
        if (downedPlayers.containsKey(event.getPlayer().getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player helper = event.getPlayer();
        
        // Obsługa noszenia (Double Shift)
        if (event.isSneaking()) {
            handleCarry(helper);
        }

        // Obsługa wskrzeszania
        if (event.isSneaking()) {
            for (Entity entity : helper.getNearbyEntities(2, 2, 2)) {
                if (entity instanceof Player && downedPlayers.containsKey(entity.getUniqueId())) {
                    startReviveProcess(helper, (Player) entity);
                    break;
                }
            }
        }
    }

    private void startReviveProcess(Player helper, Player downed) {
        BossBar bar = Bukkit.createBossBar("§aWskrzeszanie: " + downed.getName(), BarColor.GREEN, BarStyle.SOLID);
        bar.addPlayer(helper);
        reviveBars.put(helper.getUniqueId(), bar);

        new BukkitRunnable() {
            double progress = 0.0;

            @Override
            public void run() {
                // Warunki przerwania: helper przestaje kucać, oddala się lub downed ginie
                if (!helper.isSneaking() || helper.getLocation().distance(downed.getLocation()) > 3 || !downedPlayers.containsKey(downed.getUniqueId())) {
                    bar.removeAll();
                    reviveBars.remove(helper.getUniqueId());
                    this.cancel();
                    return;
                }

                progress += 0.1; // 0.1 co 2 ticki = 10 sekund łącznie
                bar.setProgress(Math.min(progress, 1.0));

                if (progress >= 1.0) {
                    finishRevive(downed);
                    bar.removeAll();
                    reviveBars.remove(helper.getUniqueId());
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 2L); // Sprawdzanie co 0.1 sekundy
    }

    private void finishRevive(Player downed) {
        UUID uuid = downed.getUniqueId();
        downedPlayers.remove(uuid);
        if (deathTasks.containsKey(uuid)) {
            deathTasks.get(uuid).cancel();
            deathTasks.remove(uuid);
        }
        downed.setSwimming(false);
        downed.setHealth(6.0); // Wstaje z 3 sercami
        downed.sendMessage("§aZostałeś uratowany!");
    }

    private void handleCarry(Player helper) {
        long now = System.currentTimeMillis();
        long last = lastShiftClick.getOrDefault(helper.getUniqueId(), 0L);
        
        if (now - last < 400) {
            for (Entity entity : helper.getNearbyEntities(2, 2, 2)) {
                if (entity instanceof Player && downedPlayers.containsKey(entity.getUniqueId())) {
                    helper.addPassenger(entity);
                    helper.sendMessage("§6Niesiesz gracza " + entity.getName());
                    break;
                }
            }
        }
        lastShiftClick.put(helper.getUniqueId(), now);
    }

    private boolean hasTotem(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING ||
               player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }
    }
