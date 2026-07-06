package com.sauti.call;

public interface TelephonyMediaFrameFactory {
    String media(String streamId, byte[] audio);

    String mark(String streamId, String markName);

    String clear(String streamId);
}
