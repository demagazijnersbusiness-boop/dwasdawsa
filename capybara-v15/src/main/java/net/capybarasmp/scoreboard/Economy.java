package net.capybarasmp.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigInteger;
import java.util.UUID;

public final class Economy {

    private final DataManager data;
    private final BoardManager boards;

    public Economy(DataManager data, BoardManager boards) {
        this.data = data;
        this.boards = boards;
    }

    public BigInteger balance(UUID id) {
        return data.get(id).money;
    }

    public void set(UUID id, BigInteger value) {
        data.get(id).money = value.signum() < 0 ? BigInteger.ZERO : value;
        refresh(id);
    }

    public void add(UUID id, BigInteger value) {
        set(id, balance(id).add(value));
    }

    public boolean take(UUID id, BigInteger value) {
        BigInteger bal = balance(id);
        if (bal.compareTo(value) < 0) return false;
        set(id, bal.subtract(value));
        return true;
    }

    private void refresh(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) boards.update(p);
    }
}
