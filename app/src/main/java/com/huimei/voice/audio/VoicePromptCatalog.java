package com.huimei.voice.audio;

import com.huimei.voice.R;
import com.huimei.voice.model.CommandEvent;
import com.huimei.voice.model.VoiceLanguage;

public final class VoicePromptCatalog {
    private VoicePromptCatalog() {
    }

    public static int rawResourceFor(VoiceLanguage language, CommandEvent event) {
        switch (event) {
            case WAKE_UP:
                return language == VoiceLanguage.CHINESE
                        ? R.raw.zh_wake_up
                        : R.raw.en_wake_up;
            case GAS_1_HOUR:
                return language == VoiceLanguage.CHINESE
                        ? R.raw.zh_gas_1_hour
                        : R.raw.en_gas_1_hour;
            case GAS_8_HOURS:
                return language == VoiceLanguage.CHINESE
                        ? R.raw.zh_gas_8_hours
                        : R.raw.en_gas_8_hours;
            default:
                return 0;
        }
    }
}
