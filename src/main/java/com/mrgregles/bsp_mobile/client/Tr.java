package com.mrgregles.bsp_mobile.client;

import net.minecraft.client.resources.language.I18n;

/**
 * Screen text from the language file ({@code assets/bsp_mobile/lang/en_us.json}, written by {@code tools/gen_lang.py}).
 * Keys are {@code gui.bsp_mobile.<key>}; values use {@code %s} for the parts filled in. {@code tools/check_lang.py}
 * checks that every key used here exists.
 */
final class Tr {
    private static final String PREFIX = "gui.bsp_mobile.";

    private Tr() {}

    static String t(String key, Object... args) {
        return I18n.get(PREFIX + key, args);
    }

    static boolean has(String key) {
        return I18n.exists(PREFIX + key);
    }
}
