package net.capybarasmp.scoreboard;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** K, M, B, T, QA, QI, SX, SP, OC, NO, DC formatting and parsing. */
public final class MoneyUtil {

    private static final String[] NAMES = {"K", "M", "B", "T", "QA", "QI", "SX", "SP", "OC", "NO", "DC"};
    private static final BigInteger[] UNITS = new BigInteger[NAMES.length];

    static {
        for (int i = 0; i < NAMES.length; i++) {
            UNITS[i] = BigInteger.TEN.pow(3 * (i + 1));
        }
    }

    private MoneyUtil() {}

    public static String format(BigInteger value) {
        boolean negative = value.signum() < 0;
        BigInteger abs = value.abs();
        int idx = -1;
        for (int i = UNITS.length - 1; i >= 0; i--) {
            if (abs.compareTo(UNITS[i]) >= 0) {
                idx = i;
                break;
            }
        }
        String out;
        if (idx < 0) {
            out = abs.toString();
        } else {
            out = new BigDecimal(abs)
                    .divide(new BigDecimal(UNITS[idx]), 2, RoundingMode.DOWN)
                    .stripTrailingZeros()
                    .toPlainString() + NAMES[idx];
        }
        return negative ? "-" + out : out;
    }

    /** Parses "500", "1.5k", "2M", "3qa" ... Throws NumberFormatException when invalid. */
    public static BigInteger parse(String input) {
        if (input == null) throw new NumberFormatException("empty");
        String s = input.trim().toUpperCase().replace(",", "").replace("$", "");
        if (s.isEmpty() || s.length() > 40) throw new NumberFormatException("invalid");
        BigInteger mult = BigInteger.ONE;
        for (int i = NAMES.length - 1; i >= 0; i--) {
            if (s.endsWith(NAMES[i])) {
                mult = UNITS[i];
                s = s.substring(0, s.length() - NAMES[i].length());
                break;
            }
        }
        BigDecimal number = new BigDecimal(s.trim());
        return number.multiply(new BigDecimal(mult)).toBigInteger();
    }
}
