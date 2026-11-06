package br.com.chfb.ativo.sharedkernel.settings;

import java.util.function.Function;

/** Configurações em um instante, para que uma mesma leitura não misture valores antigos e novos. */
public final class SettingValues {

    private final Function<String, String> lookup;

    public SettingValues(Function<String, String> lookup) {
        this.lookup = lookup;
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(value(key));
    }

    public int getInt(String key) {
        return Integer.parseInt(value(key));
    }

    public double getDouble(String key) {
        return Double.parseDouble(value(key));
    }

    private String value(String key) {
        String value = lookup.apply(key);
        if (value == null) {
            throw new IllegalStateException("Configuração sem valor: " + key);
        }
        return value;
    }
}
