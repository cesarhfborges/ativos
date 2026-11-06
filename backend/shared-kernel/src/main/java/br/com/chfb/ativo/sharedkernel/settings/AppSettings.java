package br.com.chfb.ativo.sharedkernel.settings;

/** Dá aos módulos acesso às configurações gerais, sem depender de onde elas são guardadas. */
public interface AppSettings {

    SettingValues current();
}
