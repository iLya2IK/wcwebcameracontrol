package com.sggdev.wcwebcameracontrol;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class SampleGattAttributes {

    private final static HashMap<String, Class<? extends BabaikaBLEDevice>> write_attributes = new HashMap();
    private final static HashMap<String, Class<? extends BabaikaBLEDevice>> read_attributes = new HashMap();
    private final static HashMap<String, Class<? extends BabaikaBLEDevice>> device_classes = new HashMap();
    static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final static String[] MAIN_SERVICE = {"00009ef0-0000-1000-8000-00805f9b34fb"};
    private final static String BT_WEBCAM_CFG_CHAR = "00009ef1-0000-1000-8000-00805f9b34fb";
    static String BT_WEBCAM_NOTI_CHAR1 = "00009ef2-0000-1000-8000-00805f9b34fb";
    static String BLE_NAME_PREFIX = "babaikaWC";

    static {
        write_attributes.put(BT_WEBCAM_CFG_CHAR,  BabaikaWebCam.class);
        read_attributes.put(BT_WEBCAM_NOTI_CHAR1, BabaikaWebCam.class);

        device_classes.put(BabaikaWebCam.uuid, BabaikaWebCam.class);
    }

    public static boolean isMainService(String uuid) {
        for (String val: MAIN_SERVICE) {
            if (uuid.equals(val)) {return true;}
        }
        return (false);
    }

    public static boolean isInputChar(String chid) {
        return  write_attributes.containsKey(chid);
    }

    public static boolean isOutputChar(String chid) {
        return  read_attributes.containsKey(chid);
    }

    public static boolean isIOChar(String chid) {
        return (isInputChar(chid) || isOutputChar(chid));
    }

    public static String getUUIDPicture(String uuid) {
        Class<? extends BabaikaBLEDevice> cl = device_classes.get(uuid);
        if (cl != null) {
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
                    BabaikaBLEDevice c = (BabaikaBLEDevice) ctor.newInstance();
                    return c.getPictureName();
                } else {
                    return null;
                }
                // production code should handle these exceptions more gracefully
            } catch (InstantiationException | InvocationTargetException | IllegalAccessException x) {
                x.printStackTrace();
            }
        }
        return null;
    }

    public static String getCharPicture(String chid) {
        if (isIOChar(chid)) {
            Class<? extends BabaikaBLEDevice> cl = write_attributes.get(chid);
            if (cl == null) cl = read_attributes.get(chid);
            if (cl != null) {
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
                        BabaikaBLEDevice c = (BabaikaBLEDevice) ctor.newInstance();
                        return c.getPictureName();
                    } else {
                        return null;
                    }
                    // production code should handle these exceptions more gracefully
                } catch (InstantiationException | InvocationTargetException | IllegalAccessException x) {
                    x.printStackTrace();
                }
            }
        }
        return null;
    }

    public static ArrayList<BabaikaCommand> getCommandSet(String chid) {
        if (isInputChar(chid)) {
            Class<? extends BabaikaBLEDevice> cl = write_attributes.get(chid);
            if (cl != null) {
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
                        BabaikaBLEDevice c = (BabaikaBLEDevice) ctor.newInstance();
                        return c.copyCommands();
                    } else {
                        return null;
                    }
                    // production code should handle these exceptions more gracefully
                } catch (InstantiationException | InvocationTargetException | IllegalAccessException x) {
                    x.printStackTrace();
                }
            }
        }
        return null;
    }

    public static BabaikaItem getNotification(String chid) {
        if (isOutputChar(chid)) {
            Class<? extends BabaikaBLEDevice> cl = read_attributes.get(chid);
            if (cl != null) {
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
                        BabaikaBLEDevice c = (BabaikaBLEDevice) ctor.newInstance();
                        return c.copyNotification(chid);
                    } else {
                        return null;
                    }
                    // production code should handle these exceptions more gracefully
                } catch (InstantiationException | InvocationTargetException | IllegalAccessException x) {
                    x.printStackTrace();
                }
            }
        }
        return null;
    }
}
