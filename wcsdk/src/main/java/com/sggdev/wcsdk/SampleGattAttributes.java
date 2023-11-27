package com.sggdev.wcsdk;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class SampleGattAttributes {

    private final static HashMap<String, Class<? extends BabaikaBLEDevice>> write_attributes = new HashMap();
    private final static HashMap<String, Class<? extends BabaikaBLEDevice>> read_attributes = new HashMap();
    private final static HashMap<String, Class<? extends BabaikaBLEDevice>> device_classes = new HashMap();
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final static String[] MAIN_SERVICE = {"00009ef0"};
    private final static String BT_WEBCAM_CFG_CHAR = "00009ef1";
    static String BT_WEBCAM_NOTI_CHAR1 = "00009ef2";
    private final static String BT_RADIO_CFG_CHAR = "00009ef3";
    static String BT_RADIO_NOTI_CHAR1 = "00009ef4";
    private final static String BT_RELAY_CFG_CHAR = "00009ef5";
    static String BT_RELAY_NOTI_CHAR1 = "00009ef6";
    public static String BLE_NAME_PREFIX = "babaikaWC";
    
    static void registerClass(String write_ch, String read_ch, String uuid, Class<? extends BabaikaBLEDevice> cls) {
        write_attributes.put(write_ch,  cls);
        read_attributes.put(read_ch, cls);
        device_classes.put(uuid, cls);
    }

    static {
        registerClass(BT_WEBCAM_CFG_CHAR,  BT_WEBCAM_NOTI_CHAR1, BabaikaWebCam.uuid, BabaikaWebCam.class);
        registerClass(BT_RADIO_CFG_CHAR,  BT_RADIO_NOTI_CHAR1, BabaikaRadio.uuid, BabaikaRadio.class);
        registerClass(BT_RELAY_CFG_CHAR,  BT_RELAY_NOTI_CHAR1, BabaikaRelay.uuid, BabaikaRelay.class);    
    }

    public static boolean isMainService(String uuid) {
        for (String val: MAIN_SERVICE) {
            if (uuid.startsWith(val)) {return true;}
        }
        return (false);
    }

    public static boolean isInputChar(String chid) {
        if (chid.length() < 8) return  false;
        return  write_attributes.containsKey(chid.substring(0, 8));
    }

    public static boolean isOutputChar(String chid) {
        if (chid.length() < 8) return  false;
        return  read_attributes.containsKey(chid.substring(0, 8));
    }

    public static boolean isIOChar(String chid) {
        return (isInputChar(chid) || isOutputChar(chid));
    }

    public static String getUUIDPicture(String uuid) {
        Class<? extends BabaikaBLEDevice> cl = device_classes.get(uuid);
        if (cl != null) {
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
        if (chid.length() < 8) return  "";
        String chid0 = chid.substring(0, 8);
        if (isIOChar(chid0)) {
            Class<? extends BabaikaBLEDevice> cl = write_attributes.get(chid0);
            if (cl == null) cl = read_attributes.get(chid0);
            if (cl != null) {
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
                        BabaikaBLEDevice c = (BabaikaBLEDevice) ctor.newInstance();
                        return c.getPictureName();
                    } else {
                        return "";
                    }
                    // production code should handle these exceptions more gracefully
                } catch (InstantiationException | InvocationTargetException | IllegalAccessException x) {
                    x.printStackTrace();
                }
            }
        }
        return "";
    }

    public static ArrayList<BabaikaCommand> getCommandSet(String chid) {
        if (chid.length() < 8) return  null;
        String chid0 = chid.substring(0, 8);
        if (isInputChar(chid0)) {
            Class<? extends BabaikaBLEDevice> cl = write_attributes.get(chid0);
            if (cl != null) {
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
        if (chid.length() < 8) return  null;
        String chid0 = chid.substring(0, 8);
        if (isOutputChar(chid0)) {
            Class<? extends BabaikaBLEDevice> cl = read_attributes.get(chid0);
            if (cl != null) {
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
                        BabaikaBLEDevice c = (BabaikaBLEDevice) ctor.newInstance();
                        return c.copyNotification(chid0);
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
