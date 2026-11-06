package br.com.chfb.ativo.coreapi.repository;

import br.com.chfb.ativo.coreapi.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
