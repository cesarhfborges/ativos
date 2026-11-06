package br.com.chfb.ativo.coreapi.dto.admin;

import java.util.List;

public record AdminSettingsResponse(List<SettingResponse> settings, SystemInfoResponse system) {
}
