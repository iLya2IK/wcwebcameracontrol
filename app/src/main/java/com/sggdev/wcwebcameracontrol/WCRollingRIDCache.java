package com.sggdev.wcwebcameracontrol;

import java.util.Iterator;
import java.util.concurrent.LinkedBlockingDeque;

public class WCRollingRIDCache extends LinkedBlockingDeque<WCRollingRID> {
    private static final int MAX_ROLL_ELEMENTS = 16;

    @Override
    public void putFirst(WCRollingRID e) throws InterruptedException {
        if (size() >= MAX_ROLL_ELEMENTS) pollLast();

        Iterator<WCRollingRID> it = iterator();

        while (it.hasNext()) {
            WCRollingRID el = it.next();
            if (el.rid() == e.rid()) {
                it.remove();
                break;
            }
        }

        super.putFirst(e);
    }

    public WCRollingRID findByRID(long rid) {

        for (WCRollingRID el : this) {
            if (el.rid() == rid) {
                return el;
            }
        }

        return null;
    }

}
