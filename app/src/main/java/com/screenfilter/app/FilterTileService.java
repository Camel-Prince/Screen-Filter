package com.screenfilter.app;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import com.screenfilter.app.core.KeywordMatcher;

public final class FilterTileService extends TileService {
    @Override public void onStartListening() { update(); }
    @Override public void onClick() {
        super.onClick();
        FilterSettings settings = new FilterSettings(this);
        if (settings.consented() && FilterRuntime.connected && !new KeywordMatcher(settings.keywords()).isEmpty()) {
            settings.setEnabled(!settings.enabled());
        }
        update();
    }
    private void update() {
        Tile tile = getQsTile();
        if (tile == null) return;
        FilterSettings settings = new FilterSettings(this);
        boolean ready = settings.consented() && FilterRuntime.connected
                && !new KeywordMatcher(settings.keywords()).isEmpty();
        tile.setState(!ready ? Tile.STATE_UNAVAILABLE : settings.enabled() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(!ready ? "请先在应用中设置" : settings.enabled() ? "过滤已开启" : "已暂停");
        tile.updateTile();
    }
}
