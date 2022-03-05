package com.sggdev.wcwebcameracontrol;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

public class BabaikaBLEDevice {
    static String uuid = "31a29e42-bfb2-431c-8dcf-0e6e3c2c080e";
    static String BLEDEVICE_ICO = "ic_ble_device";

    private final ArrayList<BabaikaItem> items = new ArrayList<>();

    BabaikaBLEDevice() {
        //
    }

    String getPictureName() { return BLEDEVICE_ICO; }

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
                if (((BabaikaNotification)notification).getUUID().equals(chid)) {
                    Class<? extends BabaikaItem> cl = notification.getClass();
                    try {
                        Constructor[] ctors = cl.getDeclaredConstructors();
                        Constructor ctor = null;
                        for (Constructor constructor : ctors) {
                            ctor = constructor;
                            if (ctor.getGenericParameterTypes().length == 0)
                                break;
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
                        Constructor[] ctors = cl.getDeclaredConstructors();
                        Constructor ctor = null;
                        for (Constructor constructor : ctors) {
                            ctor = constructor;
                            if (ctor.getGenericParameterTypes().length == 0)
                                break;
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

    void put(String aKey, String aCommand, String aComment, String aPicture, boolean aRepeatable) {
        items.add(new BabaikaCommand(aKey, aCommand, aComment, aPicture, aRepeatable));
    }
    void put(BabaikaCommand cmd) {
        items.add(cmd);
    }

    void putNoti(String aUUID, String aPicture) {
        items.add(new BabaikaNotification(aUUID, aPicture));
    }

    void putNoti(BabaikaNotification notification) {
        items.add(notification);
    }
    void putCommNoti(String aKey, String aCommand, String aComment, String aPicture, boolean aRepeatable,
                     BabaikaNotification notification) {
        BabaikaNotiCommand item = new BabaikaNotiCommand(aKey, aCommand, aComment, aPicture, aRepeatable);
        item.setNotification(notification);
        items.add(item);
    }

    void putCommNoti(BabaikaNotiCommand notificationComm) {
        items.add(notificationComm);
    }
}