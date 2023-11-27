package com.sggdev.wcsdk;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

public class BabaikaBLEDevice {
    static String BLE_DEVICE_ICO = "ic_ble_device";

    private final ArrayList<BabaikaItem> items = new ArrayList<>();

    BabaikaBLEDevice() {
        //
    }

    String getPictureName() { return BLE_DEVICE_ICO; }

    ArrayList<BabaikaCommand> copyCommands() {
        ArrayList<BabaikaCommand> res = new ArrayList<>();
        for (BabaikaItem item : items) {
            if ((item instanceof BabaikaCommand) &&
                !(item instanceof BabaikaNotiCommand)) {
                res.add((BabaikaCommand)item);
            }
        }
        return res;
    }

    BabaikaItem copyNotification(String chid) {
        for (BabaikaItem notification : items) {
            if (notification instanceof BabaikaNotification) {
                String mid = ((BabaikaNotification)notification).getUUID().substring(0, 8);
                if (mid.equals(chid)) {
                    Class<? extends BabaikaItem> cl = notification.getClass();
                    try {
                        Constructor<?>[] ctors = cl.getDeclaredConstructors();
                        Constructor<?> ctor = null;
                        for (Constructor<?> constructor : ctors) {
                            if (constructor.getGenericParameterTypes().length == 0) {
                                ctor = constructor;
                                break;
                            }
                        }
                        if (ctor != null) {
                            ctor.setAccessible(true);
                            BabaikaNotification c = (BabaikaNotification) ctor.newInstance();
                            c.restoreState(notification.saveState());
                            return c;
                        } else
                            return null;
                    } catch (InstantiationException | InvocationTargetException | IllegalAccessException x) {
                        x.printStackTrace();
                    }
                }
            } else
            if (notification instanceof BabaikaNotiCommand) {
                if (((BabaikaNotiCommand)notification).getNoti().getUUID().equals(chid)) {
                    //return new BabaikaNotification(chid, notification.getPicture());
                    Class<? extends BabaikaItem> cl = notification.getClass();
                    try {
                        Constructor<?>[] ctors = cl.getDeclaredConstructors();
                        Constructor<?> ctor = null;
                        for (Constructor<?> constructor : ctors) {
                            if (constructor.getGenericParameterTypes().length == 0) {
                                ctor = constructor;
                                break;
                            }
                        }
                        if (ctor != null) {
                            ctor.setAccessible(true);
                            BabaikaNotiCommand c = (BabaikaNotiCommand) ctor.newInstance();
                            c.restoreState(notification.saveState());
                            return c;
                        } else
                            return null;
                    } catch (InstantiationException | InvocationTargetException | IllegalAccessException x) {
                        x.printStackTrace();
                    }
                }
            }
        }
        return null;
    }

    void putCommNotification(BabaikaNotiCommand notificationComm) {
        items.add(notificationComm);
    }
}